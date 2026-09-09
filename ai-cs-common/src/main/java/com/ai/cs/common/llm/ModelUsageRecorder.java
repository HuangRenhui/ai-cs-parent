package com.ai.cs.common.llm;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 模型用量/失败事件记录器：写 Redis 流由 ai-cs-job 落库；
 * 同时维护当日 token/成本计数（供路由层配额检查）与 80% 配额预警。
 * 不阻塞主调用链，失败仅打日志。
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelUsageRecorder {

    /** 用量事件 Redis Stream key（ai-cs-job 消费后落库） */
    public static final String STREAM_KEY = "ai:model:usage:stream";
    /** Stream 最大长度，超出截断，防止消费端故障时内存膨胀 */
    private static final long MAX_LEN = 100000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 成本计数精度：元 × 10^6 */
    private static final long COST_SCALE = 1_000_000L;
    private static final BigDecimal COST_SCALE_BD = BigDecimal.valueOf(COST_SCALE);
    /** 配额预警水位 */
    private static final double ALERT_RATIO = 0.8;

    /** 允许无 Redis 环境启动（required=false）：无 Redis 时记录直接跳过，不影响主链路 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** 当日用量计数 key：hash {tokens, costMicro} */
    public static String dailyKey(Long modelId) {
        return "ai:model:usage:daily:" + modelId + ":" + LocalDate.now().format(DAY);
    }

    /** 当日 80% 预警去重 key */
    private static String alertKey(Long modelId) {
        return "ai:model:quota:alert:" + modelId + ":" + LocalDate.now().format(DAY);
    }

    /**
     * 记录一次调用（成功或失败）：写流 + 累计当日计数 + 80% 配额预警。
     */
    public void record(ModelUsageEvent event, AiModelRoute route) {
        if (event == null) {
            return;
        }
        if (event.getTs() == null) {
            event.setTs(System.currentTimeMillis());
        }
        if (redisTemplate == null) {
            return;
        }
        try {
            Map<String, String> map = new HashMap<>();
            map.put("payload", JSON.toJSONString(event));
            redisTemplate.opsForStream().add(STREAM_KEY, map);
            redisTemplate.opsForStream().trim(STREAM_KEY, MAX_LEN);
        } catch (Exception e) {
            log.warn("记录模型用量事件失败: {}", e.getMessage());
        }
        // 仅成功且有 token 消耗时累计配额计数
        if (route != null && route.getId() != null
                && event.getSuccess() != null && event.getSuccess() == 1
                && event.getTotalTokens() != null && event.getTotalTokens() > 0) {
            accumulateDaily(route, event);
        }
    }

    /** 累计当日 token/成本计数（Redis hash 自增），并做 80% 配额预警 */
    private void accumulateDaily(AiModelRoute route, ModelUsageEvent event) {
        try {
            String key = dailyKey(route.getId());
            long tokens = redisTemplate.opsForHash().increment(key, "tokens", event.getTotalTokens());
            long costMicro = 0L;
            // 成本按 元×10^6 的整数存储，避免浮点累计误差
            if (event.getCost() != null) {
                costMicro = redisTemplate.opsForHash().increment(key, "costMicro",
                        event.getCost().multiply(COST_SCALE_BD).longValue());
            }
            // 过期时间 50 小时：覆盖当天计数 + 跨天缓冲，避免 key 永久残留
            redisTemplate.expire(key, 50, TimeUnit.HOURS);
            alertIfNearQuota(route, tokens, costMicro);
        } catch (Exception e) {
            log.warn("累计模型当日用量失败: {}", e.getMessage());
        }
    }

    /** 达到 80% 配额时输出一次预警日志（当日去重） */
    private void alertIfNearQuota(AiModelRoute route, long tokens, long costMicro) {
        boolean nearToken = route.getDailyTokenLimit() != null && route.getDailyTokenLimit() > 0
                && tokens >= route.getDailyTokenLimit() * ALERT_RATIO;
        boolean nearCost = route.getDailyCostLimit() != null
                && BigDecimal.valueOf(costMicro).divide(COST_SCALE_BD, 6, RoundingMode.HALF_UP)
                        .compareTo(route.getDailyCostLimit().multiply(BigDecimal.valueOf(ALERT_RATIO))) >= 0;
        if (!nearToken && !nearCost) {
            return;
        }
        String key = alertKey(route.getId());
        // setIfAbsent 保证同一模型当天只预警一次，避免日志刷屏
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", 26, TimeUnit.HOURS);
        if (Boolean.TRUE.equals(first)) {
            log.warn("[模型配额预警] 模型[{}]当日用量已达 80%：tokens={}/{}, cost={}/{} 元，超过配额后将自动降级到其它候选模型",
                    route.getModelName(), tokens,
                    route.getDailyTokenLimit() == null ? "不限" : route.getDailyTokenLimit(),
                    BigDecimal.valueOf(costMicro).divide(COST_SCALE_BD, 4, RoundingMode.HALF_UP),
                    route.getDailyCostLimit() == null ? "不限" : route.getDailyCostLimit());
        }
    }

    /**
     * 按模型单价与 token 计算本次成本（元），无单价返回 null（本地/免费模型）。
     */
    public static BigDecimal computeCost(AiModelRoute route, Integer promptTokens, Integer completionTokens) {
        if (route == null) {
            return null;
        }
        BigDecimal cost = null;
        if (route.getCostPer1kIn() != null && promptTokens != null && promptTokens > 0) {
            cost = route.getCostPer1kIn().multiply(BigDecimal.valueOf(promptTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        }
        if (route.getCostPer1kOut() != null && completionTokens != null && completionTokens > 0) {
            BigDecimal out = route.getCostPer1kOut().multiply(BigDecimal.valueOf(completionTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            cost = cost == null ? out : cost.add(out);
        }
        return cost;
    }

    /**
     * 便捷构造成功事件（含成本快照）
     */
    public static ModelUsageEvent success(AiModelRoute route, String sessionId, ModelCallResult result) {
        ModelUsageEvent e = new ModelUsageEvent();
        fill(e, route, sessionId);
        e.setSuccess(1);
        if (result != null) {
            e.setPromptTokens(result.getPromptTokens());
            e.setCompletionTokens(result.getCompletionTokens());
            e.setTotalTokens(result.getTotalTokens());
            e.setLatencyMs(result.getLatencyMs());
            e.setCost(computeCost(route, result.getPromptTokens(), result.getCompletionTokens()));
        }
        return e;
    }

    /**
     * 便捷构造失败事件
     */
    public static ModelUsageEvent failure(AiModelRoute route, String sessionId, Long latencyMs, String errorMsg) {
        ModelUsageEvent e = new ModelUsageEvent();
        fill(e, route, sessionId);
        e.setSuccess(0);
        e.setLatencyMs(latencyMs);
        // 错误信息截断到 480 字符，防止超长堆栈撑爆 Redis 流与库表字段
        e.setErrorMsg(errorMsg == null ? "unknown" : (errorMsg.length() > 480 ? errorMsg.substring(0, 480) : errorMsg));
        return e;
    }

    /** 填充事件的模型快照字段（route 为 null 时只填会话ID） */
    private static void fill(ModelUsageEvent e, AiModelRoute route, String sessionId) {
        if (route != null) {
            e.setModelId(route.getId());
            e.setModelName(route.getModelName());
            e.setModelType(route.getModelType());
            e.setProvider(route.getProvider());
        }
        e.setSessionId(sessionId);
    }
}
