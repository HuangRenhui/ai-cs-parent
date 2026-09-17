package com.ai.cs.common.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 模型用量/失败事件记录器（占位）
 *
 * <p>原实现：把每次调用事件写入 Redis Stream（{@link #STREAM_KEY}），由 ai-cs-job 消费落库；
 * 同时维护当日 token/成本计数（Redis hash，供路由层配额检查）并在达到 80% 配额时预警一次。
 * 成本按模型单价与 token 计算（元，精度 ×10^6），无单价的本地/免费模型返回 null。</p>
 *
 * <p>当前不写流、不计费：{@link #record} 只记日志，{@link #computeCost} 恒返回 null，
 * {@link #success}／{@link #failure} 事件构造返回 null，{@link #dailyKey} 返回约定的 key 形状。
 * 用量统计因此恒为空。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelUsageRecorder {

    /** 用量事件 Redis Stream key（ai-cs-job 消费后落库） */
    public static final String STREAM_KEY = "ai:model:usage:stream";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 允许无 Redis 环境启动（required=false）：无 Redis 时记录直接跳过，不影响主链路 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 当日用量计数 key：hash {tokens, costMicro}（占位：仅拼 key，不读写）
     */
    public static String dailyKey(Long modelId) {
        return "ai:model:usage:daily:" + modelId + ":" + LocalDate.now().format(DAY);
    }

    /**
     * 记录一次调用（成功或失败）（占位：不写流、不累计）
     *
     * <p>TODO 后续实现：向 {@link #STREAM_KEY} 追加事件（Stream 上限 100000，超出截断），
     * 并累计当日 token/成本计数、在达到 80% 配额时输出一次预警日志（当日去重）。</p>
     */
    public void record(ModelUsageEvent event, AiModelRoute route) {
        log.info("[占位] 模型用量记录未实现 routeId={} sessionId={}",
                route == null ? null : route.getId(), event == null ? null : event.getSessionId());
    }

    /**
     * 按模型单价与 token 计算本次成本（元）（占位：恒返回 null）
     *
     * @return null（表示未计费/未实现）
     */
    public static BigDecimal computeCost(AiModelRoute route, Integer promptTokens, Integer completionTokens) {
        log.info("[占位] 模型成本计算未实现，返回 null");
        return null;
    }

    /**
     * 便捷构造成功事件（含成本快照）（占位：返回 null）
     *
     * @return null
     */
    public static ModelUsageEvent success(AiModelRoute route, String sessionId, ModelCallResult result) {
        log.info("[占位] 成功用量事件构造未实现，返回 null");
        return null;
    }

    /**
     * 便捷构造失败事件（占位：返回 null）
     *
     * @return null
     */
    public static ModelUsageEvent failure(AiModelRoute route, String sessionId, Long latencyMs, String errorMsg) {
        log.info("[占位] 失败用量事件构造未实现，返回 null");
        return null;
    }
}
