package com.ai.cs.common.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模型熔断器（占位）
 *
 * <p>TODO 后续实现：按模型 ID 维护「连续失败计数 + 熔断截止时间」的内存状态（每实例，重启即重置）。
 * 连续失败达到 {@code route.failThreshold}（缺省 2）即摘除并冷却 60 秒；冷却期内 {@link #allow} 返回 false，
 * 冷却期结束后放行一次调用作为半开探测；调用成功即清零计数并解除熔断。
 * 熔断触发时同步把运行时健康标记为 DOWN（由 ModelRouter 负责）。</p>
 *
 * <p>当前不做任何熔断：{@link #allow} 恒返回 true（不摘除任何模型）、{@link #onFailure} 恒返回 false
 * （不触发熔断）、{@link #isOpen} 恒 false、{@link #consecutiveFails} 恒 0。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    /**
     * 是否允许调用该模型（占位：恒允许，不做熔断判断）
     */
    public boolean allow(AiModelRoute route) {
        return true;
    }

    /**
     * 调用成功：重置计数（占位：不做处理）
     */
    public void onSuccess(AiModelRoute route) {
        log.debug("[占位] 模型熔断计数复位未实现 routeId={}", route == null ? null : route.getId());
    }

    /**
     * 调用失败：累计连续失败，达到阈值则熔断（占位：恒不熔断）
     *
     * @return false
     */
    public boolean onFailure(AiModelRoute route) {
        log.info("[占位] 模型熔断计数未实现，不触发熔断 routeId={}", route == null ? null : route.getId());
        return false;
    }

    /**
     * 当前是否处于熔断中（占位：恒 false）
     */
    public boolean isOpen(AiModelRoute route) {
        return false;
    }

    /**
     * 当前连续失败次数（占位：恒 0）
     */
    public int consecutiveFails(Long modelId) {
        return 0;
    }
}
