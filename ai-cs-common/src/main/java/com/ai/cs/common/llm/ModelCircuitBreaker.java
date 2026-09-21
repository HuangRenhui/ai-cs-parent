package com.ai.cs.common.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型熔断器
 *
 * <p>按模型 ID 维护「连续失败计数 + 熔断截止时间」的内存状态（每实例，重启即重置）。
 * 连续失败达到 {@code route.failThreshold}（缺省 2）即摘除并冷却 {@value #COOLDOWN_MS} 毫秒；
 * 冷却期内 {@link #allow} 返回 false，冷却期结束后放行一次调用作为半开探测；调用成功即清零计数并解除熔断。</p>
 *
 * <p>熔断触发时把运行时健康标记为 DOWN 由 {@link ModelRouter} 负责（本类不依赖路由对象之外的状态）。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    /** 未配置 failThreshold 时的缺省熔断阈值 */
    private static final int DEFAULT_FAIL_THRESHOLD = 2;
    /** 熔断冷却时间(毫秒) */
    private static final long COOLDOWN_MS = 60_000L;

    private final Map<Long, State> states = new ConcurrentHashMap<>();

    /**
     * 是否允许调用该模型：熔断中返回 false；冷却结束自动转为半开（放行一次探测）
     */
    public boolean allow(AiModelRoute route) {
        Long modelId = idOf(route);
        if (modelId == null) {
            return true;
        }
        State state = states.get(modelId);
        if (state == null) {
            return true;
        }
        synchronized (state) {
            if (state.openUntil <= 0) {
                return true;
            }
            if (System.currentTimeMillis() >= state.openUntil) {
                state.openUntil = 0;
                log.info("模型熔断冷却结束，放行半开探测 modelId={} 历史连续失败={}", modelId, state.fails);
                return true;
            }
            return false;
        }
    }

    /**
     * 调用成功：清零计数并解除熔断
     */
    public void onSuccess(AiModelRoute route) {
        Long modelId = idOf(route);
        if (modelId == null) {
            return;
        }
        State state = states.get(modelId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.fails > 0 || state.openUntil > 0) {
                log.info("模型调用恢复，重置熔断状态 modelId={} 失败次数={}", modelId, state.fails);
            }
            state.fails = 0;
            state.openUntil = 0;
        }
    }

    /**
     * 调用失败：累计连续失败，达到阈值则熔断
     *
     * @return true 表示本次触发了熔断（调用方据此把健康标记为 DOWN）
     */
    public boolean onFailure(AiModelRoute route) {
        Long modelId = idOf(route);
        if (modelId == null) {
            return false;
        }
        int threshold = thresholdOf(route);
        State state = states.computeIfAbsent(modelId, key -> new State());
        synchronized (state) {
            state.fails++;
            if (state.fails >= threshold) {
                state.openUntil = System.currentTimeMillis() + COOLDOWN_MS;
                log.warn("模型连续失败达阈值，熔断 {} 毫秒 modelId={} 阈值={} 失败次数={}",
                        COOLDOWN_MS, modelId, threshold, state.fails);
                return true;
            }
            return false;
        }
    }

    /**
     * 当前是否处于熔断中
     */
    public boolean isOpen(AiModelRoute route) {
        Long modelId = idOf(route);
        if (modelId == null) {
            return false;
        }
        State state = states.get(modelId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.openUntil > System.currentTimeMillis();
        }
    }

    /**
     * 当前连续失败次数
     */
    public int consecutiveFails(Long modelId) {
        if (modelId == null) {
            return 0;
        }
        State state = states.get(modelId);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.fails;
        }
    }

    // ==================== 内部 ====================

    private static Long idOf(AiModelRoute route) {
        return route == null ? null : route.getId();
    }

    private static int thresholdOf(AiModelRoute route) {
        Integer failThreshold = route == null ? null : route.getFailThreshold();
        return failThreshold == null || failThreshold <= 0 ? DEFAULT_FAIL_THRESHOLD : failThreshold;
    }

    /** 单模型熔断状态 */
    private static final class State {
        /** 连续失败次数 */
        private int fails;
        /** 熔断截止时间(毫秒)，0 表示未熔断 */
        private long openUntil;
    }
}
