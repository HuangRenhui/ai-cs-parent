package com.ai.cs.job.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 系统健康检查定时任务
 * 每5分钟检查一次系统健康状况，异常时记录告警日志
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class HealthCheckTask {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Value("${job.health-check:true}")
    private boolean healthCheckEnabled;

    /**
     * 健康检查计数器（连续失败次数）
     */
    private int consecutiveDbFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    @Scheduled(fixedRate = 300000)
    public void healthCheck() {
        if (!healthCheckEnabled) {
            return;
        }

        boolean dbHealthy = checkDatabase();
        boolean redisHealthy = checkRedis();

        if (!dbHealthy) {
            consecutiveDbFailures++;
            if (consecutiveDbFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.error("[健康检查-告警] 数据库连接连续失败 {} 次！", consecutiveDbFailures);
            }
        } else {
            consecutiveDbFailures = 0;
        }

        if (dbHealthy && redisHealthy) {
            log.debug("[健康检查] 系统健康 - 数据库:正常, Redis:正常");
        } else {
            log.warn("[健康检查] 系统异常 - 数据库:{}, Redis:{}",
                    dbHealthy ? "正常" : "异常",
                    redisHealthy ? "正常" : "异常");
        }

        // 记录健康状态到数据库
        recordHealthStatus(dbHealthy, redisHealthy);
    }

    /**
     * 检查数据库连接
     */
    private boolean checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("[健康检查] 数据库连接异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查Redis连接（通过尝试读写来验证连接）
     */
    private boolean checkRedis() {
        try {
            String testKey = "health:check:" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(testKey, "1", 10, TimeUnit.SECONDS);
            String value = redisTemplate.opsForValue().get(testKey);
            return "1".equals(value);
        } catch (Exception e) {
            log.error("[健康检查] Redis连接异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 记录健康检查状态到统计表
     */
    private void recordHealthStatus(boolean dbHealthy, boolean redisHealthy) {
        try {
            String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String sql = "INSERT INTO cs_operation_log (operation_type, operation_content, operator, create_time) " +
                    "VALUES (?, ?, ?, NOW())";
            String content = String.format("健康检查: 数据库=%s, Redis=%s",
                    dbHealthy ? "正常" : "异常",
                    redisHealthy ? "正常" : "异常");
            jdbcTemplate.update(sql, "HEALTH_CHECK", content, "SYSTEM");
        } catch (Exception e) {
            // 健康检查记录失败不应影响主流程
            log.debug("[健康检查] 记录健康状态失败: {}", e.getMessage());
        }
    }
}
