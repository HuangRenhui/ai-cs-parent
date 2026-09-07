package com.ai.cs.job.task;

import com.ai.cs.common.llm.ModelUsageEvent;
import com.ai.cs.common.llm.ModelUsageRecorder;
import com.ai.cs.job.support.JobLockService;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 模型用量消费任务：从 Redis 流批量消费并落库 cs_model_usage。
 * 近 5 分钟失败数超过阈值时输出告警日志（供日志采集/ELK 告警）。
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelUsageConsumeTask {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BATCH = 200;

    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private JobLockService jobLockService;

    @Value("${job.model-usage-consume:true}")
    private boolean consumeEnabled;

    @Value("${job.model-fail-alert-threshold:5}")
    private int failAlertThreshold;

    @Scheduled(fixedDelay = 5000)
    public void consume() {
        if (!consumeEnabled) {
            return;
        }
        if (!jobLockService.tryLock("job:lock:model-usage-consume", Duration.ofMinutes(1))) {
            return;
        }
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    StreamReadOptions.empty().count(BATCH),
                    StreamOffset.create(ModelUsageRecorder.STREAM_KEY, ReadOffset.from("0-0")));
            if (records == null || records.isEmpty()) {
                return;
            }
            int inserted = 0;
            for (MapRecord<String, Object, Object> record : records) {
                try {
                    Object payload = record.getValue().get("payload");
                    if (payload == null) {
                        continue;
                    }
                    ModelUsageEvent event = JSON.parseObject(String.valueOf(payload), ModelUsageEvent.class);
                    insert(event);
                    inserted++;
                    redisTemplate.opsForStream().delete(ModelUsageRecorder.STREAM_KEY, record.getId());
                } catch (Exception e) {
                    log.warn("消费模型用量记录失败: {}", e.getMessage());
                }
            }
            if (inserted > 0) {
                log.info("模型用量落库: {} 条", inserted);
            }
            alertIfNeeded();
        } catch (Exception e) {
            log.error("模型用量消费任务异常", e);
        } finally {
            jobLockService.unlock("job:lock:model-usage-consume");
        }
    }

    /** 近 5 分钟失败数超阈值时输出告警日志 */
    private void alertIfNeeded() {
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(5);
            Long fails = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM cs_model_usage WHERE success = 0 AND create_time >= ?",
                    Long.class, since.format(FMT));
            if (fails != null && fails >= failAlertThreshold) {
                log.error("[模型告警] 近5分钟模型调用失败 {} 次，超过阈值 {}，请检查模型可用性与密钥", fails, failAlertThreshold);
            }
        } catch (Exception e) {
            log.debug("模型失败告警检查失败: {}", e.getMessage());
        }
    }

    private void insert(ModelUsageEvent e) {
        String sql = "INSERT INTO cs_model_usage (model_id, model_name, model_type, provider, session_id, " +
                "prompt_tokens, completion_tokens, total_tokens, latency_ms, cost, success, error_msg, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.update(sql,
                e.getModelId(),
                e.getModelName(),
                e.getModelType(),
                e.getProvider(),
                e.getSessionId(),
                e.getPromptTokens(),
                e.getCompletionTokens(),
                e.getTotalTokens(),
                e.getLatencyMs(),
                e.getCost(),
                e.getSuccess(),
                e.getErrorMsg(),
                e.getTs() == null ? LocalDateTime.now().format(FMT)
                        : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.getTs()), java.time.ZoneId.systemDefault()).format(FMT));
    }
}
