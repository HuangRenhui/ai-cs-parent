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

    public boolean tryLock(String key, Duration ttl) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("任务锁获取失败，跳过本轮: {}", e.getMessage());
            return false;
        }
    }

    public void unlock(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("释放任务锁失败: {}", e.getMessage());
        }
    }
}
