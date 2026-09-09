package com.ai.cs.job.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;

/**
 * 多实例 job 互斥。拿不到锁则跳过本轮，避免重复清理/统计。
 */
@Slf4j
@Component
public class JobLockService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取任务锁（基于 Redis SETNX，带 TTL 防死锁）
     *
     * @param key 锁键，按任务区分，如 job:lock:stats-hourly
     * @param ttl 锁自动过期时间；即使持有者异常退出，锁也会到期自动释放
     * @return true 表示抢到锁可执行本轮任务；false（含 Redis 异常）表示跳过本轮
     */
    public boolean tryLock(String key, Duration ttl) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("任务锁获取失败，跳过本轮: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 释放任务锁；释放失败仅记 debug 日志，锁会靠 TTL 自然过期兜底
     *
     * @param key 锁键
     */
    public void unlock(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("释放任务锁失败: {}", e.getMessage());
        }
    }
}
