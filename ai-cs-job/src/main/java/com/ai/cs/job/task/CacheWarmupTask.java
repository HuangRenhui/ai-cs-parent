package com.ai.cs.job.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热定时任务（占位）
 *
 * <p>TODO 后续实现：在业务早高峰前把热点数据（热门 FAQ、系统配置、工单统计）提前加载进 Redis，
 * 避免早高峰大量缓存 miss 回源数据库。</p>
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class CacheWarmupTask {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /** 缓存预热开关 */
    @Value("${job.cache-warmup:true}")
    private boolean warmupEnabled;

    /**
     * 缓存预热入口（占位）
     *
     * <p>TODO 后续实现：预热热门 FAQ（按访问次数取前 100，TTL 1 小时）、系统配置（TTL 2 小时）、
     * 工单状态与类型统计（TTL 30 分钟）；cron 每天凌晨 3 点执行。</p>
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void warmupCache() {
        log.info("[占位] 缓存预热任务（未执行实际逻辑）warmupEnabled={}", warmupEnabled);
    }
}
