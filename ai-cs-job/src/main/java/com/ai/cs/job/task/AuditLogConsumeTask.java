package com.ai.cs.job.task;

import com.ai.cs.common.event.AuditEventPublisher;
import com.ai.cs.job.support.JobLockService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作审计消费任务（占位）
 *
 * <p>TODO 后续实现：从 Redis 列表批量弹出审计事件并写入 cs_operation_log。</p>
 */
@Slf4j
@Component
public class AuditLogConsumeTask {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BATCH = 100;

    @Resource
    private StringRedisTemplate redisTemplate;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private JobLockService jobLockService;

    @Value("${job.audit-log-consume:true}")
    private boolean consumeEnabled;

    /**
     * 消费一批审计事件（占位）
     *
     * <p>TODO 后续实现：受 {@code job.audit-log-consume} 开关与分布式锁控制，循环批量弹出
     * Redis 队列并落库；单条失败只记日志、不回队列，避免毒消息死循环。</p>
     */
    @Scheduled(fixedDelay = 3000)
    public void consume() {
        log.info("[占位] 操作审计消费任务（未执行实际逻辑）consumeEnabled={}", consumeEnabled);
    }
}
