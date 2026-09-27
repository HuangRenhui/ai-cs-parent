package com.ai.cs.common.llm;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型用量/失败事件记录器
 *
 * <p>把每次调用事件写入 Redis Stream（{@link #STREAM_KEY}，单字段 {@code event} 存 {@link ModelUsageEvent}
 * 的 JSON，另附 {@code ts}），由 ai-cs-job 消费落库 cs_model_usage；
 * 同时维护当日 token/成本计数（Redis hash {@link #dailyKey(Long)}，字段 tokens/calls/costMicro），
 * 供路由层配额检查，并在达到 80% 配额时预警一次（当日去重）。</p>
 *
 * <p>成本按模型单价与 token 计算（元，精度 ×10^6）；无单价的本地/免费模型返回 null。
 * 无 Redis 环境（{@link StringRedisTemplate} 缺席）时只记日志跳过，不影响主链路。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelUsageRecorder {

    /** 用量事件 Redis Stream key（ai-cs-job 消费后落库） */
    public static final String STREAM_KEY = "ai:model:usage:stream";
    /** Stream 长度上限，超出按近似裁剪，防止无限增长 */
    private static final long MAX_STREAM_LENGTH = 100_000L;
    /** 成本精度：元 ×10^6 */
    private static final BigDecimal COST_SCALE = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000L);
    /** 配额预警阈值：达到 80% 时预警一次 */
    private static final double QUOTA_WARN_RATIO = 0.8D;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 允许无 Redis 环境启动（required=false）：无 Redis 时记录直接跳过，不影响主链路 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 当日用量计数 key：hash {tokens, calls, costMicro}
     */
    public static String dailyKey(Long modelId) {
        return "ai:model:usage:daily:" + modelId + ":" + LocalDate.now().format(DAY);
    }

    /**
     * 配额预警去重 key：同一模型同一天只预警一次
     */
    private static String warnKey(Long modelId) {
        return "ai:model:usage:warn:" + modelId + ":" + LocalDate.now().format(DAY);
    }

    /**
     * 记录一次调用（成功或失败）：写 Stream + 累计当日用量 + 配额预警
     */
    public void record(ModelUsageEvent event, AiModelRoute route) {
        if (event == null) {
            return;
        }
        if (event.getTs() == null) {
            event.setTs(System.currentTimeMillis());
        }
        StringRedisTemplate redis = this.redisTemplate;
        if (redis == null) {
            log.debug("未配置 Redis，跳过用量记录 modelId={} success={}", event.getModelId(), event.getSuccess());
            return;
        }
        // 两段写入各自 try/catch，职责分离、互不阻塞：
        //   ① Stream（全量明细，由 job 落库做报表/对账）；
        //   ② 当日计数（供路由层配额判断）。
        // 二者失败都只记日志：用量统计属旁路，绝不能因为统计失败把主链路调用带崩。
        try {
            Map<String, String> body = new LinkedHashMap<>(2);
            body.put("event", JSON.toJSONString(event));
            body.put("ts", String.valueOf(event.getTs()));
            redis.opsForStream().add(STREAM_KEY, body);
            trimStream(redis);
        } catch (Exception e) {
            log.warn("模型用量事件写入 Redis Stream 失败: {}", e.getMessage());
        }
        try {
            accumulateDaily(redis, event, route);
        } catch (Exception e) {
            log.warn("模型当日用量累计失败: {}", e.getMessage());
        }
    }

    /**
     * 按模型单价与 token 计算本次成本（元）
     *
     * @return null 表示该模型未配置单价（本地/免费模型）
     */
    public static BigDecimal computeCost(AiModelRoute route, Integer promptTokens, Integer completionTokens) {
        if (route == null) {
            return null;
        }
        BigDecimal costPer1kIn = route.getCostPer1kIn();
        BigDecimal costPer1kOut = route.getCostPer1kOut();
        if (costPer1kIn == null && costPer1kOut == null) {
            return null;
        }
        BigDecimal cost = BigDecimal.ZERO;
        if (costPer1kIn != null && promptTokens != null && promptTokens > 0) {
            cost = cost.add(costPer1kIn.multiply(BigDecimal.valueOf(promptTokens)).divide(THOUSAND, 6, RoundingMode.HALF_UP));
        }
        if (costPer1kOut != null && completionTokens != null && completionTokens > 0) {
            cost = cost.add(costPer1kOut.multiply(BigDecimal.valueOf(completionTokens)).divide(THOUSAND, 6, RoundingMode.HALF_UP));
        }
        return cost.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 便捷构造成功事件（含成本快照）
     */
    public static ModelUsageEvent success(AiModelRoute route, String sessionId, ModelCallResult result) {
        if (result == null) {
            return null;
        }
        ModelUsageEvent event = baseEvent(route, sessionId);
        event.setPromptTokens(result.getPromptTokens());
        event.setCompletionTokens(result.getCompletionTokens());
        event.setTotalTokens(result.getTotalTokens());
        event.setLatencyMs(result.getLatencyMs());
        event.setCost(computeCost(route, result.getPromptTokens(), result.getCompletionTokens()));
        event.setSuccess(1);
        return event;
    }

    /**
     * 便捷构造失败事件
     */
    public static ModelUsageEvent failure(AiModelRoute route, String sessionId, Long latencyMs, String errorMsg) {
        ModelUsageEvent event = baseEvent(route, sessionId);
        event.setLatencyMs(latencyMs);
        event.setErrorMsg(errorMsg);
        event.setSuccess(0);
        return event;
    }

    // ==================== 内部 ====================

    private static ModelUsageEvent baseEvent(AiModelRoute route, String sessionId) {
        ModelUsageEvent event = new ModelUsageEvent();
        if (route != null) {
            event.setModelId(route.getId());
            event.setModelName(route.getModelName());
            event.setModelType(route.getModelType());
            event.setProvider(route.getProvider());
        }
        event.setSessionId(sessionId);
        event.setTs(System.currentTimeMillis());
        return event;
    }

    /** 近似裁剪 Stream，失败（Redis 版本过低等）只记 debug */
    private void trimStream(StringRedisTemplate redis) {
        try {
            redis.opsForStream().trim(STREAM_KEY, MAX_STREAM_LENGTH, true);
        } catch (Exception e) {
            log.debug("裁剪用量 Stream 失败: {}", e.getMessage());
        }
    }

    /** 累计当日 tokens/calls/costMicro，并在达到 80% 配额时预警一次 */
    private void accumulateDaily(StringRedisTemplate redis, ModelUsageEvent event, AiModelRoute route) {
        Long modelId = event.getModelId();
        if (modelId == null) {
            // 取不到模型 ID 的事件归入 -1 桶：既不会丢计数，也避免 Redis key 里出现 "null"
            modelId = -1L;
        }
        String key = dailyKey(modelId);
        // 全部用 HINCRBY 原子累加：多实例并发写同一模型时不会丢计数（读改写会）
        long tokens = event.getTotalTokens() == null ? 0L : event.getTotalTokens();
        if (tokens > 0) {
            redis.opsForHash().increment(key, "tokens", tokens);
        }
        // calls 每次必加（含失败调用），便于用 calls 与失败数直接算失败率
        redis.opsForHash().increment(key, "calls", 1L);
        if (event.getCost() != null) {
            // 成本按「元 ×10^6」存整数，避免浮点累加误差；读取侧（ModelRouter.overQuota）再除回去
            long costMicro = event.getCost().multiply(COST_SCALE).setScale(0, RoundingMode.HALF_UP).longValue();
            if (costMicro > 0) {
                redis.opsForHash().increment(key, "costMicro", costMicro);
            }
        }
        // 保留 3 天而非 1 天：跨日排查"昨天配额为什么超了"时还能看到原始计数
        redis.expire(key, Duration.ofDays(3));
        if (route != null) {
            warnIfQuotaNearlyExhausted(redis, route, key);
        }
    }

    /** 当日用量达到配额的 80% 时预警一次（当日去重），避免刷屏 */
    private void warnIfQuotaNearlyExhausted(StringRedisTemplate redis, AiModelRoute route, String dailyUsageKey) {
        Long modelId = route.getId();
        if (modelId == null) {
            return;
        }
        Long limitTokens = route.getDailyTokenLimit();
        BigDecimal limitCost = route.getDailyCostLimit();
        if ((limitTokens == null || limitTokens <= 0) && limitCost == null) {
            return;
        }
        Object tokensValue = redis.opsForHash().get(dailyUsageKey, "tokens");
        Object costValue = redis.opsForHash().get(dailyUsageKey, "costMicro");
        long usedTokens = toLong(tokensValue);
        BigDecimal usedCost = costValue == null ? BigDecimal.ZERO
                : new BigDecimal(costValue.toString()).divide(COST_SCALE, 6, RoundingMode.HALF_UP);

        boolean tokenWarn = limitTokens != null && limitTokens > 0 && usedTokens >= limitTokens * QUOTA_WARN_RATIO;
        boolean costWarn = limitCost != null && limitCost.signum() > 0
                && usedCost.compareTo(limitCost.multiply(BigDecimal.valueOf(QUOTA_WARN_RATIO))) >= 0;
        if (!tokenWarn && !costWarn) {
            return;
        }
        // 用 Redis 的 setIfAbsent 做「当日只预警一次」：内存标记在多实例下会各预警一遍（刷屏），
        // 而这里只需要一个跨实例的开关，代价是一次 Redis 写（预警是低频路径，可接受）
        Boolean firstWarn = redis.opsForValue().setIfAbsent(warnKey(modelId), "1", Duration.ofDays(1));
        if (Boolean.TRUE.equals(firstWarn)) {
            log.warn("模型当日配额预警（已达 {}%）modelId={} tokens={}/{} cost={}/{}",
                    (int) (QUOTA_WARN_RATIO * 100), modelId, usedTokens, limitTokens, usedCost, limitCost);
        }
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
