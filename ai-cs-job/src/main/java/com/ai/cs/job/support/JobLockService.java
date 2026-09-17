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
 * 多实例 job 互斥（占位）
 *
 * <p>TODO 后续实现：基于 Redis SETNX + TTL 抢锁，锁值用 UUID，释放时用 Lua 比对，防止误删其他实例刚抢到的锁。</p>
 *
 * <p>⚠️ <b>占位实现不做任何互斥</b>：{@link #tryLock} 恒返回 true。补实现前，
 * 不要把依赖「同一时刻只有一个实例执行」的批处理逻辑接在它上面。</p>
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
     * 尝试获取任务锁（占位）
     *
     * <p>TODO 后续实现：用 {@code setIfAbsent(key, token, ttl)} 抢锁并记录 token；Redis 异常时按「跳过本轮」处理。</p>
     *
     * @param key 锁键，按任务区分，如 job:lock:stats-hourly
     * @param ttl 锁自动过期时间；即使持有者异常退出，锁也会到期自动释放
     * @return 占位恒返回 true（<b>不产生互斥效果</b>）
     */
    public boolean tryLock(String key, Duration ttl) {
        log.info("[占位] 获取任务锁（未做互斥）key={} ttl={}", key, ttl);
        return true;
    }

    /**
     * 释放任务锁（占位）
     *
     * <p>TODO 后续实现：仅删除自己持有的 token（Lua 比对），释放失败只记 debug，锁靠 TTL 自然过期。</p>
     *
     * @param key 锁键
     */
    public void unlock(String key) {
        log.info("[占位] 释放任务锁（未做互斥）key={}", key);
    }
}
