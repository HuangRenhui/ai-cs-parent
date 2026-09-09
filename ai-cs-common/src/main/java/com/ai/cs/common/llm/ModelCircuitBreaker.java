package com.ai.cs.common.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型熔断器：连续失败达到阈值即摘除，冷却期后允许半开探测。
 * 状态保存在内存（每实例），配合注册表健康标记实现快速切换。
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    /** 默认连续失败阈值 */
    private static final int DEFAULT_FAIL_THRESHOLD = 2;
    /** 熔断冷却期(毫秒)，到期后允许一次探测 */
    private static final long COOLDOWN_MS = 60_000;

    /** 单模型的熔断状态：连续失败计数 + 熔断截止时间 */
    private static class State {
        /** 连续失败次数（成功即清零） */
        final AtomicInteger consecutiveFails = new AtomicInteger(0);
        /** 熔断截止时间点(毫秒)，当前时间小于该值则拒绝调用；0 表示未熔断 */
        volatile long openUntil = 0L;
    }

    /** 模型ID -> 熔断状态（按实例内存保存，进程重启即重置） */
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    /**
     * 是否允许调用该模型
     */
    public boolean allow(AiModelRoute route) {
        if (route == null || route.getId() == null) {
            return true;
        }
        State s = states.get(route.getId());
        if (s == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        // 冷却期内直接拒绝；冷却期过后放行一次调用作为探测（半开）
        if (s.openUntil > now) {
            return false;
        }
        return true;
    }

    /**
     * 调用成功：重置计数
     */
    public void onSuccess(AiModelRoute route) {
        if (route == null || route.getId() == null) {
            return;
        }
        State s = states.computeIfAbsent(route.getId(), k -> new State());
        s.consecutiveFails.set(0);
        s.openUntil = 0L;
    }

    /**
     * 调用失败：累计连续失败，达到阈值则熔断
     *
     * @return 是否触发熔断
     */
    public boolean onFailure(AiModelRoute route) {
        if (route == null || route.getId() == null) {
            return false;
        }
        // 模型未单独配置阈值时用默认值
        int threshold = route.getFailThreshold() != null && route.getFailThreshold() > 0
                ? route.getFailThreshold() : DEFAULT_FAIL_THRESHOLD;
        State s = states.computeIfAbsent(route.getId(), k -> new State());
        int fails = s.consecutiveFails.incrementAndGet();
        if (fails >= threshold) {
            s.openUntil = System.currentTimeMillis() + COOLDOWN_MS;
            log.warn("模型熔断: id={} name={} 连续失败{}次, 冷却{}ms", route.getId(), route.getModelName(), fails, COOLDOWN_MS);
            return true;
        }
        return false;
    }

    /**
     * 当前是否处于熔断中
     */
    public boolean isOpen(AiModelRoute route) {
        if (route == null || route.getId() == null) {
            return false;
        }
        State s = states.get(route.getId());
        return s != null && s.openUntil > System.currentTimeMillis();
    }

    /**
     * 当前连续失败次数
     */
    public int consecutiveFails(Long modelId) {
        State s = states.get(modelId);
        return s == null ? 0 : s.consecutiveFails.get();
    }
}
