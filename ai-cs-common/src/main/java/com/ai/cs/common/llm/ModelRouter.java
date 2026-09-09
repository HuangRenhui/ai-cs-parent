package com.ai.cs.common.llm;

import com.ai.cs.common.constant.RedisKeyConst;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型路由器：负责按能力(模型类型)选取当前生效模型、多模型故障转移(fallback)。
 *
 * <p>模型注册表由 base-service({@code /system/ai-model})写入 Redis(Hash: {@code ai:model:registry})，
 * 本组件在 base-service(测试连接/健康探测)与 agent/knowledge(对话/向量) 中复用。</p>
 *
 * <p><b>兼容旧配置</b>：当某能力未在注册表中登记任何模型（候选池为空）时，
 * 自动回退到历史配置(ai.llm / ai.embedding 的 {@link DashscopeModelClient})，
 * 保证不登记模型时原对话/向量链路完全不变；登记后才启用注册路由与故障转移。</p>
 *
 * <p><b>熔断与用量</b>：连续失败达到模型 {@code failThreshold} 即由 {@link ModelCircuitBreaker}
 * 摘除并冷却 60s；每次调用（成功/失败）经 {@link ModelUsageRecorder} 写 Redis 流，由 ai-cs-job 落库。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelRouter {

    /** 默认对话能力类型：chat() 不带类型时使用 */
    private static final String DEFAULT_LLM_TYPE = ModelTypeEnum.LLM.getCode();

    /** type -> 该能力下已启用模型列表（按 priority 升序 + active 优先） */
    private final Map<String, List<AiModelRoute>> candidates = new ConcurrentHashMap<>();

    private final OpenAiCompatClient client = new OpenAiCompatClient(20);

    @Resource
    private StringRedisTemplate redisTemplate;

    /** 旧配置回退客户端(DashScope 原生协议，来自 ai.llm/ai.embedding) */
    @Resource
    private DashscopeModelClient legacyClient;

    @Resource
    private ModelCircuitBreaker circuitBreaker;

    @Resource
    private ModelUsageRecorder usageRecorder;

    /** 最近一次从 Redis 拉取注册表的时间戳（毫秒），用于防抖 */
    private volatile long lastRefresh = 0L;

    /** 注册表刷新防抖间隔(毫秒)：5 秒内多次请求只拉一次 Redis */
    private static final long REFRESH_INTERVAL_MS = 5000L;

    /**
     * 对话：选用 LLM 类型的当前生效模型，失败时按优先级顺延到下一候选（故障转移）。
     * 未登记任何对话模型时回退到 {@link #legacyClient}。
     *
     * @param messages 消息列表 {role, content}
     * @return 模型输出文本
     */
    public String chat(List<Map<String, String>> messages) {
        return chatForType(DEFAULT_LLM_TYPE, messages);
    }

    /**
     * 对话（带会话ID，用于用量关联）
     */
    public String chat(List<Map<String, String>> messages, String sessionId) {
        return chatForType(DEFAULT_LLM_TYPE, messages, sessionId);
    }

    /**
     * 指定能力类型的对话（预留：对话页可选模型/视觉等）
     */
    public String chatForType(String modelType, List<Map<String, String>> messages) {
        return chatForType(modelType, messages, null);
    }

    /**
     * 指定能力类型的对话（带会话ID）
     */
    public String chatForType(String modelType, List<Map<String, String>> messages, String sessionId) {
        ensureLoaded();
        List<AiModelRoute> pool = pool(modelType);
        if (pool.isEmpty()) {
            // 兼容旧配置：未注册模型时走历史 DashScope 配置，不破坏原链路
            return legacyClient.chatMessages(messages);
        }
        Exception last = null;
        for (AiModelRoute route : pool) {
            // 跳过已 DOWN、熔断中、或超当日配额的模型（超限自动顺延，免费/本地候选即降级目标）
            if (ModelHealthEnum.DOWN.getCode().equalsIgnoreCase(route.getHealth())
                    || !circuitBreaker.allow(route)) {
                continue;
            }
            if (quotaExceeded(route)) {
                continue;
            }
            try {
                ModelCallResult result = callChatWithRetry(route, messages);
                markHealthy(route);
                circuitBreaker.onSuccess(route);
                usageRecorder.record(ModelUsageRecorder.success(route, sessionId, result), route);
                return result.getText();
            } catch (Exception e) {
                last = e;
                log.warn("模型[{}]调用失败(含重试)，尝试切换到候选。原因: {}", route.getModelName(), e.getMessage());
                circuitBreaker.onFailure(route);
                usageRecorder.record(ModelUsageRecorder.failure(route, sessionId, null, e.getMessage()), route);
                markDownIfBroken(route);
            }
        }
        // 注册的候选全部失败：回退历史配置作最后兜底
        try {
            return legacyClient.chatMessages(messages);
        } catch (Exception legacyEx) {
            log.warn("历史配置回退失败: {}", legacyEx.getMessage());
            throw new ModelCallException("所有对话模型均不可用，请稍后重试或检查模型配置: "
                    + (last == null ? "无可用候选" : last.getMessage()));
        }
    }

    /**
     * 向量化：选用 EMBEDDING 类型模型；未登记向量模型时回退 {@link #legacyClient}。
     *
     * @param text 待向量化文本
     * @return 向量
     */
    public List<Float> embed(String text) {
        return embed(text, null);
    }

    /**
     * 向量化（带会话ID，用于用量关联）
     */
    public List<Float> embed(String text, String sessionId) {
        ensureLoaded();
        List<AiModelRoute> pool = pool(ModelTypeEnum.EMBEDDING.getCode());
        if (pool.isEmpty()) {
            return legacyClient.embed(text);
        }
        Exception last = null;
        for (AiModelRoute route : pool) {
            if (ModelHealthEnum.DOWN.getCode().equalsIgnoreCase(route.getHealth())
                    || !circuitBreaker.allow(route)) {
                continue;
            }
            if (quotaExceeded(route)) {
                continue;
            }
            try {
                ModelCallResult result = callEmbedWithRetry(route, text);
                markHealthy(route);
                circuitBreaker.onSuccess(route);
                usageRecorder.record(ModelUsageRecorder.success(route, sessionId, result), route);
                return OpenAiCompatClient.parseEmbedding(result);
            } catch (Exception e) {
                last = e;
                log.warn("向量模型[{}]调用失败(含重试)，尝试切换到候选。原因: {}", route.getModelName(), e.getMessage());
                circuitBreaker.onFailure(route);
                usageRecorder.record(ModelUsageRecorder.failure(route, sessionId, null, e.getMessage()), route);
                markDownIfBroken(route);
            }
        }
        try {
            return legacyClient.embed(text);
        } catch (Exception legacyEx) {
            log.warn("历史配置回退失败: {}", legacyEx.getMessage());
            throw new ModelCallException("所有向量模型均不可用，请稍后重试或检查模型配置: "
                    + (last == null ? "无可用候选" : last.getMessage()));
        }
    }

    /**
     * 单模型连通性测试（供管理页"测试连接"与健康探测使用），不修改路由池、不计熔断与用量。
     *
     * @return true=连接成功
     */
    public boolean testConnect(AiModelRoute route) {
        if (route == null) {
            return false;
        }
        try {
            if (ModelTypeEnum.EMBEDDING.getCode().equalsIgnoreCase(route.getModelType())) {
                ModelCallResult r = client.embedWithUsage(route.getBaseUrl(), route.getApiKey(),
                        route.getRemoteModel(), "ping", route.getTimeoutMs());
                List<Float> v = OpenAiCompatClient.parseEmbedding(r);
                return v != null && !v.isEmpty();
            }
            ModelCallResult r = client.chatWithUsage(route.getBaseUrl(), route.getApiKey(),
                    route.getRemoteModel(), route.getTemperature(),
                    List.of(Map.of("role", "user", "content", "ping")), route.getTimeoutMs());
            return StringUtils.hasText(r.getText());
        } catch (Exception e) {
            log.warn("模型[{}]连通测试失败: {}", route.getModelName(), e.getMessage());
            return false;
        }
    }

    /**
     * 将一批模型注册进内存路由池，并按能力分组排好序。
     * base-service 在启动预热与配置变更后调用（同时写 Redis）。
     */
    public void registerLocal(List<AiModelRoute> routes) {
        // 整体重建路由池：配置以"全量覆盖"语义下发，不做增量合并
        candidates.clear();
        if (routes == null || routes.isEmpty()) {
            return;
        }
        for (AiModelRoute r : routes) {
            // 跳过未启用的模型
            if (r == null || !isEnabled(r)) {
                continue;
            }
            // 按能力类型分组
            candidates.computeIfAbsent(r.getModelType(), k -> new ArrayList<>()).add(r);
        }
        // 每组内部排序：生效模型最前、DOWN 最后、其余按优先级升序
        candidates.values().forEach(this::sortAndEnsureActiveFirst);
    }

    /**
     * 从 Redis 拉取全部启用模型并刷新本地路由池（消费服务启动/变更时调用）。
     */
    public void refreshFromRedis() {
        if (redisTemplate == null) {
            log.debug("StringRedisTemplate 不可用，跳过 Redis 刷新");
            return;
        }
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(RedisKeyConst.AI_MODEL_REGISTRY);
            List<AiModelRoute> all = new ArrayList<>();
            for (Object value : entries.values()) {
                if (value == null) {
                    continue;
                }
                List<AiModelRoute> list = JSON.parseObject(String.valueOf(value),
                        new TypeReference<List<AiModelRoute>>() {
                        });
                if (list != null) {
                    all.addAll(list);
                }
            }
            registerLocal(all);
        } catch (Exception e) {
            log.warn("刷新 Redis 模型注册表失败: {}", e.getMessage());
        }
    }

    /** 本地模型池是否为空（判断是否已注册过） */
    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    /**
     * 消费服务（agent/knowledge 等独立进程）按需从 Redis 同步注册表。
     * 带 5 秒防抖：使 base-service 的"设为生效/启停"在数秒内传播到本服务，又不至于每次请求打 Redis。
     */
    private void ensureLoaded() {
        long now = System.currentTimeMillis();
        // 防抖：间隔未到直接返回（先无锁快速判断，避免每次请求都抢锁）
        if (redisTemplate == null || now - lastRefresh < REFRESH_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            // 双重检查：抢锁期间可能已有其它线程刷新过
            if (System.currentTimeMillis() - lastRefresh < REFRESH_INTERVAL_MS) {
                return;
            }
            // 先更新时间戳再拉取：即使拉取失败也不会造成每请求都访问 Redis
            lastRefresh = now;
            refreshFromRedis();
        }
    }

    /** 获取某能力的候选（深拷贝，避免外部修改内部排序态） */
    public List<AiModelRoute> pool(String modelType) {
        List<AiModelRoute> list = candidates.get(modelType);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    /** 某能力当前生效模型（active==1 且 enabled），无则返回 priority 最小者 */
    public AiModelRoute active(String modelType) {
        List<AiModelRoute> list = pool(modelType);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    private boolean isEnabled(AiModelRoute r) {
        return r.getEnabled() != null && r.getEnabled() == 1;
    }

    /**
     * 同模型失败重试：首次 + maxRetries 次；重试不重复计熔断（一次用户调用最多计一次失败）。
     * 注意：超时后重试对付费模型可能重复计费，maxRetries 建议 0~2。
     */
    private ModelCallResult callChatWithRetry(AiModelRoute route, List<Map<String, String>> messages) {
        // 重试次数上限硬钳制为 5，防止配置错误导致长时间卡死
        int attempts = 1 + Math.max(0, route.getMaxRetries() == null ? 0 : Math.min(route.getMaxRetries(), 5));
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return client.chatWithUsage(route.getBaseUrl(), route.getApiKey(),
                        route.getRemoteModel(), route.getTemperature(), messages, route.getTimeoutMs());
            } catch (Exception e) {
                last = e;
                if (i < attempts - 1) {
                    log.info("模型[{}]第{}次调用失败，立即重试: {}", route.getModelName(), i + 1, e.getMessage());
                }
            }
        }
        throw last instanceof ModelCallException mce ? mce : new ModelCallException("模型调用失败: " + last.getMessage(), last);
    }

    /** 向量调用的同模型失败重试，语义同 {@link #callChatWithRetry} */
    private ModelCallResult callEmbedWithRetry(AiModelRoute route, String text) {
        int attempts = 1 + Math.max(0, route.getMaxRetries() == null ? 0 : Math.min(route.getMaxRetries(), 5));
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return client.embedWithUsage(route.getBaseUrl(), route.getApiKey(),
                        route.getRemoteModel(), text, route.getTimeoutMs());
            } catch (Exception e) {
                last = e;
                if (i < attempts - 1) {
                    log.info("向量模型[{}]第{}次调用失败，立即重试: {}", route.getModelName(), i + 1, e.getMessage());
                }
            }
        }
        throw last instanceof ModelCallException mce ? mce : new ModelCallException("模型调用失败: " + last.getMessage(), last);
    }

    /**
     * 当日配额检查：token 或成本任一达到上限即视为超限（Redis 计数，由用量记录器维护）。
     * 超限后顺延下一候选——把免费/本地模型注册为候选即获得"超限自动降级"能力。
     */
    private boolean quotaExceeded(AiModelRoute route) {
        if (redisTemplate == null || route.getId() == null) {
            return false;
        }
        boolean hasTokenLimit = route.getDailyTokenLimit() != null && route.getDailyTokenLimit() > 0;
        boolean hasCostLimit = route.getDailyCostLimit() != null && route.getDailyCostLimit().signum() > 0;
        if (!hasTokenLimit && !hasCostLimit) {
            return false;
        }
        try {
            Map<Object, Object> daily = redisTemplate.opsForHash().entries(ModelUsageRecorder.dailyKey(route.getId()));
            long tokens = parseLong(daily.get("tokens"));
            long costMicro = parseLong(daily.get("costMicro"));
            boolean over = (hasTokenLimit && tokens >= route.getDailyTokenLimit())
                    || (hasCostLimit && java.math.BigDecimal.valueOf(costMicro)
                            .movePointLeft(6).compareTo(route.getDailyCostLimit()) >= 0);
            if (over) {
                log.warn("模型[{}]已达当日配额上限(tokens={}/{}, costMicro={})，顺延下一候选",
                        route.getModelName(), tokens,
                        hasTokenLimit ? route.getDailyTokenLimit() : "不限", costMicro);
            }
            return over;
        } catch (Exception e) {
            // 读取配额失败时放行，避免 Redis 抖动导致全部模型被误判超限
            log.warn("读取模型当日配额失败，按未超限处理: {}", e.getMessage());
            return false;
        }
    }

    /** Redis hash 值安全转 long，非法值按 0 处理 */
    private long parseLong(Object v) {
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void sortAndEnsureActiveFirst(List<AiModelRoute> list) {
        list.sort((a, b) -> {
            // 生效的排最前，其次 DOWN 的排最后
            int da = ModelHealthEnum.DOWN.getCode().equalsIgnoreCase(a.getHealth()) ? 1 : 0;
            int db = ModelHealthEnum.DOWN.getCode().equalsIgnoreCase(b.getHealth()) ? 1 : 0;
            if (da != db) {
                return da - db;
            }
            boolean aa = a.getIsActive() != null && a.getIsActive() == 1;
            boolean ba = b.getIsActive() != null && b.getIsActive() == 1;
            if (aa != ba) {
                return aa ? -1 : 1;
            }
            return Integer.compare(prio(a.getPriority()), prio(b.getPriority()));
        });
    }

    /** 优先级 null 安全取值（null 视为 0，即最优先） */
    private int prio(Integer p) {
        return p == null ? 0 : p;
    }

    /** 熔断触发时同步把运行时健康标记为 DOWN（不落 Redis，冷却/探测恢复由熔断器与测试连接负责） */
    private void markDownIfBroken(AiModelRoute route) {
        if (circuitBreaker.isOpen(route)) {
            route.setHealth(ModelHealthEnum.DOWN.getCode());
        }
    }

    private void markHealthy(AiModelRoute route) {
        route.setHealth(ModelHealthEnum.HEALTHY.getCode());
    }
}
