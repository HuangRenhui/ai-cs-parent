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

    /** 落库 create_time 字段的时间格式 */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 每轮从 Redis Stream 批量读取的最大条数，防止单次任务过大拖垮数据库 */
    private static final int BATCH = 200;

    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private JobLockService jobLockService;

    /** 消费开关：配置为 false 时任务空转，便于线上应急止血 */
    @Value("${job.model-usage-consume:true}")
    private boolean consumeEnabled;

    /** 近 5 分钟模型调用失败次数的告警阈值 */
    @Value("${job.model-fail-alert-threshold:5}")
    private int failAlertThreshold;

    /**
     * 定时消费模型用量事件（上一轮结束后间隔 5 秒再执行）。
     * <p>
     * 消费模型说明：各业务模块通过 {@link ModelUsageRecorder} 把用量事件追加到 Redis Stream；
     * 本任务每轮从 Stream 起始位置（0-0）批量读取尚未删除的记录，成功落库后立即从 Stream 中
     * 删除该条记录——"删除"动作相当于手动 ACK，已删除的记录下一轮不会再被读到；
     * 落库失败的记录保留在 Stream 中，下一轮会被自动重试（可能反复重试，需关注告警日志）。
     */
    @Scheduled(fixedDelay = 5000)
    public void consume() {
        // 开关关闭时直接跳过
        if (!consumeEnabled) {
            return;
        }
        // 多实例部署下通过 Redis 分布式锁互斥，避免同一批记录被重复落库
        if (!jobLockService.tryLock("job:lock:model-usage-consume", Duration.ofMinutes(1))) {
            return;
        }
        try {
            // 从 Stream 头部读取：由于成功记录会被即时删除，这里读到的都是未处理或处理失败的记录
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    StreamReadOptions.empty().count(BATCH),
                    StreamOffset.create(ModelUsageRecorder.STREAM_KEY, ReadOffset.from("0-0")));
            // 本轮没有待处理记录，直接结束（注意 return 前锁会在 finally 中释放）
            if (records == null || records.isEmpty()) {
                return;
            }
            int inserted = 0;
            for (MapRecord<String, Object, Object> record : records) {
                try {
                    // 事件体统一存放在 payload 字段中，内容为 JSON 字符串
                    Object payload = record.getValue().get("payload");
                    if (payload == null) {
                        continue;
                    }
                    // 反序列化为用量事件：包含模型标识、token 用量、耗时、成本、是否成功等字段
                    ModelUsageEvent event = JSON.parseObject(String.valueOf(payload), ModelUsageEvent.class);
                    insert(event);
                    inserted++;
                    // 落库成功后删除 Stream 记录（相当于 ACK），防止下轮重复消费
                    redisTemplate.opsForStream().delete(ModelUsageRecorder.STREAM_KEY, record.getId());
                } catch (Exception e) {
                    // 单条失败不影响整批：该记录保留在 Stream 中，等待下一轮重试
                    log.warn("消费模型用量记录失败: {}", e.getMessage());
                }
            }
            if (inserted > 0) {
                log.info("模型用量落库: {} 条", inserted);
            }
            // 每轮结束后检查近期失败量，超阈值输出告警日志
            alertIfNeeded();
        } catch (Exception e) {
            log.error("模型用量消费任务异常", e);
        } finally {
            // 无论成败都释放锁，避免锁占用到 TTL 过期影响下一轮
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

    /**
     * 将单条用量事件写入 cs_model_usage 表
     *
     * @param e 用量事件；字段含义：modelId/modelName 模型标识，modelType 模型类型（llm/embedding），
     *          provider 供应商，sessionId 关联会话，prompt/completion/totalTokens 三段 token 计数，
     *          latencyMs 调用耗时，cost 估算成本，success 是否成功，errorMsg 失败原因，ts 事件发生毫秒时间戳
     */
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
                // 事件时间戳缺失时以当前时间兜底，避免 create_time 落空
                e.getTs() == null ? LocalDateTime.now().format(FMT)
                        : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.getTs()), java.time.ZoneId.systemDefault()).format(FMT));
    }
}
