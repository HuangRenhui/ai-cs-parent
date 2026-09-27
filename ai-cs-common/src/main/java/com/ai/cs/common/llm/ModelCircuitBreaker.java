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
 * <p><b>两个必须知道的设计取舍：</b></p>
 * <ul>
 *   <li><b>状态是每实例内存级</b>（{@link ConcurrentHashMap} + 每模型一把锁，重启即清零）：多实例部署时
 *       各实例各自熔断，不做分布式共享。这是有意为之——熔断判断在每次调用的热路径上，
 *       若每实例都要写 Redis 会引入额外延迟与单点依赖；代价是"某实例已熔断、另一实例还会再试几次"，
 *       最终由失败重试兜住，可接受。</li>
 *   <li><b>半开是隐式实现的</b>：冷却期结束后 {@link #isOpen} 自然返回 false，下一次调用即为"半开探测"；
 *       因为 {@code fails} 未清零，探测若再次失败会立刻重新熔断（这正是期望的行为）。
 *       因此路由层用的是 {@link #isOpen}，而 {@link #allow} 目前<b>没有调用方</b>（保留给需要显式
 *       "冷却结束日志 + 状态转换"语义的场景，见其 javadoc）。</li>
 * </ul>
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
     * 是否允许调用该模型：熔断中返回 false；冷却结束自动转为半开（放行一次探测）。
     *
     * <p>语义上与 {@link #isOpen} 互补：{@code isOpen} 是纯查询，本方法在"冷却刚结束"时会把
     * {@code openUntil} 归零并打一条日志（便于排查"什么时候恢复的"）。</p>
     *
     * <p><b>注意</b>：当前路由层走的是 {@link #isOpen}（半开由冷却时间自然达成），
     * 本方法暂无调用方；若要启用"显式半开日志/状态转换"，把 {@link ModelRouter#available} 的判断
     * 从 {@code isOpen} 换成本方法即可，两者判定结果一致，只差一条恢复日志与状态归零。</p>
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
     * <p>熔断后 {@code fails} <b>不清零</b>：冷却结束放行的那次"半开探测"若再失败，
     * {@code fails} 已 ≥ 阈值，会立刻重新熔断，避免"冷却—失败—冷却"的慢速抖动。</p>
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
