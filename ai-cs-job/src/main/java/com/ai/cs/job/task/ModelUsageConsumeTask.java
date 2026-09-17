package com.ai.cs.job.task;

import com.ai.cs.common.llm.ModelUsageEvent;
import com.ai.cs.common.llm.ModelUsageRecorder;
import com.ai.cs.job.support.JobLockService;
import com.alibaba.fastjson2.JSON;
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
 * 模型用量消费任务（占位）
 *
 * <p>TODO 后续实现：从 Redis 流批量消费用量事件并落库 cs_model_usage；
 * 近 5 分钟失败数超过阈值时输出告警日志（供日志采集/ELK 告警）。</p>
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
     * 消费模型用量事件（占位）
     *
     * <p>TODO 后续实现：从 {@code ModelUsageRecorder.STREAM_KEY} 头部批量读取未处理记录，反序列化为
     * {@link ModelUsageEvent} 后落库 cs_model_usage，落库成功即删除该条 Stream 记录（等价于手动 ACK）；
     * 单条失败保留待重试；每轮结束检查近 5 分钟失败数并超阈值告警。</p>
     */
    @Scheduled(fixedDelay = 5000)
    public void consume() {
        log.info("[占位] 模型用量消费任务（未执行实际逻辑）consumeEnabled={} failAlertThreshold={}",
                consumeEnabled, failAlertThreshold);
    }
}
