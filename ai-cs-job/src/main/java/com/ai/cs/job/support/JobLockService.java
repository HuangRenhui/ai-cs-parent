package com.ai.cs.job.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 多实例 job 互斥。
 *
 * <p>基于 Redis {@code SET NX + TTL} 抢锁，锁值为本线程 UUID；释放时用 Lua 比对锁值，
 * 防止误删其他实例刚抢到的锁。Redis 不可用时按「跳过本轮」处理，宁可少跑一次，
 * 也不让多实例重复执行。</p>
 */
@Slf4j
@Component
public class JobLockService {

    /** 仅当当前值等于本实例 token 时才删除 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 当前线程持有的锁 token，unlock 时用于比对 */
    private final ThreadLocal<String> tokenHolder = new ThreadLocal<>();

    /**
     * 尝试获取任务锁：Redis SET NX + TTL，锁值为本线程 UUID。
     *
     * <p>Redis 异常时按「跳过本轮」处理：宁可少跑一次定时任务，也不能让多实例重复执行。</p>
     *
     * @param key 锁键，按任务区分，如 job:lock:stats-hourly
     * @param ttl 锁自动过期时间；即使持有者异常退出，锁也会到期自动释放
     * @return true=抢到锁可以执行；false=已被其他实例占用或 Redis 不可用
     */
    public boolean tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                tokenHolder.set(token);
                return true;
            }
            log.info("任务锁已被占用，跳过本轮 key={}", key);
            return false;
        } catch (Exception e) {
            log.warn("获取任务锁失败（Redis 异常），跳过本轮 key={}", key, e);
            return false;
        }
    }

    /**
     * 释放任务锁：仅当值仍为本线程 token 时删除，避免误删其他实例刚抢到的锁。
     *
     * @param key 锁键
     */
    public void unlock(String key) {
        String token = tokenHolder.get();
        if (token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        } catch (Exception e) {
            // 释放失败只记日志，锁会靠 TTL 自然过期
            log.warn("释放任务锁失败 key={}，等待 TTL 过期", key, e);
        } finally {
            tokenHolder.remove();
        }
    }
}
