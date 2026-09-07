package com.ai.cs.common.llm;

import com.alibaba.fastjson.JSON;
import com.ai.cs.common.constant.RedisKeyConst;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型路由：统一从注册表选择模型，支持本地/在线零差异切换、故障自动切换、熔断与用量记录。
 *
 * <p>优先级：注册表启用模型(priority升序) → 当前生效(active) → 旧配置 DashScope 兜底。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelRouter {

    @Resource
    private ModelProperties modelProperties;

    @Resource
    private EmbeddingProperties embeddingProperties;

    @Resource(required = false)
    private StringRedisTemplate redisTemplate;

    @Resource
    private ModelCircuitBreaker circuitBreaker;

    @Resource
    private ModelUsageRecorder usageRecorder;

    /** 本地路由池：modelType -> 启用模型列表(按 priority 升序) */
    private final Map<String, List<AiModelRoute>> localRegistry = new ConcurrentHashMap<>();

    /** 当前生效：modelType -> route */
    private final Map<String, AiModelRoute> localActive = new ConcurrentHashMap<>();

    /** 注册表版本号，用于 Redis 变更探测 */
    private volatile long lastVersion = 0L;

    private volatile OpenAiCompatClient openAiClient;
    private volatile DashscopeModelClient legacyClient;

    @PostConstruct
    public void init() {
        refreshFromRedis();
    }

    // ==================== 注册表管理 ====================

    /**
     * 用数据库启用模型刷新本地路由池（base-service 变更后调用）。
     */
    public synchronized void registerLocal(List<AiModelRoute> routes) {
        localRegistry.clear();
        localActive.clear();
        if (routes != null) {
            Map<String, List<AiModelRoute>> byType = routes.stream()
                    .filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                    .collect(Collectors.groupingBy(AiModelRoute::getModelType));
            byType.forEach((type, list) -> {
                list.sort(Comparator.comparing(AiModelRoute::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AiModelRoute::getId, Comparator.nullsLast(Long::compareTo)));
                localRegistry.put(type, new ArrayList<>(list));
                list.stream().filter(r -> r.getIsActive() != null && r.getIsActive() == 1)
                        .findFirst()
                        .ifPresent(r -> localActive.put(type, r));
            });
        }
        log.info("模型路由池已刷新: {}", localRegistry.keySet());
    }

    /**
     * 定时从 Redis 同步注册表（消费服务冷启动/变更时兜底）。
     */
    @Scheduled(fixedDelay = 5000)
    public void refreshFromRedis() {
        if (redisTemplate == null) {
            return;
        }
        try {
            String version = redisTemplate.opsForValue().get(RedisKeyConst.AI_MODEL_VERSION);
            long v = version == null ? 0L : Long.parseLong(version);
            if (v <= lastVersion) {
                return;
            }
            Map<Object, Object> registry = redisTemplate.opsForHash().entries(RedisKeyConst.AI_MODEL_REGISTRY);
            Map<Object, Object> active = redisTemplate.opsForHash().entries(RedisKeyConst.AI_MODEL_ACTIVE);
            if (registry == null || registry.isEmpty()) {
                return;
            }
            localRegistry.clear();
            localActive.clear();
            registry.forEach((type, json) -> {
                List<AiModelRoute> list = JSON.parseArray(String.valueOf(json), AiModelRoute.class);
                if (list != null && !list.isEmpty()) {
                    list.sort(Comparator.comparing(AiModelRoute::getPriority, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(AiModelRoute::getId, Comparator.nullsLast(Long::compareTo)));
                    localRegistry.put(String.valueOf(type), list);
                }
            });
            active.forEach((type, json) -> {
                AiModelRoute route = JSON.parseObject(String.valueOf(json), AiModelRoute.class);
                if (route != null) {
                    localActive.put(String.valueOf(type), route);
                }
            });
            lastVersion = v;
            log.info("从 Redis 同步模型注册表: version={}, types={}", v, localRegistry.keySet());
        } catch (Exception e) {
            log.warn("同步模型注册表失败: {}", e.getMessage());
        }
    }

    // ==================== 对话 ====================

    /**
     * 统一对话入口：优先注册表，失败自动切换，最终兜底旧配置。
     */
    public String chat(List<Map<String, String>> messages) {
        return chatWithResult(messages, null).getText();
    }

    /**
     * 统一对话入口（带会话ID，用于用量关联）
     */
    public String chat(List<Map<String, String>> messages, String sessionId) {
        return chatWithResult(messages, sessionId).getText();
    }

    /**
     * 统一对话入口（返回完整结果，含用量）
     */
    public ModelCallResult chatWithResult(List<Map<String, String>> messages, String sessionId) {
        List<AiModelRoute> candidates = candidates(ModelTypeEnum.LLM.getCode());
        if (!candidates.isEmpty()) {
            AiModelRoute active = localActive.get(ModelTypeEnum.LLM.getCode());
            if (active != null && circuitBreaker.allow(active)) {
                try {
                    return doChat(active, messages, sessionId);
                } catch (Exception e) {
                    log.warn("当前生效模型调用失败: {} - {}", active.getModelName(), e.getMessage());
                    circuitBreaker.onFailure(active);
                    usageRecorder.record(ModelUsageRecorder.failure(active, sessionId, null, e.getMessage()));
                }
            }
            for (AiModelRoute route : candidates) {
                if (active != null && route.getId().equals(active.getId())) {
                    continue;
                }
                if (!circuitBreaker.allow(route)) {
                    continue;
                }
                try {
                    return doChat(route, messages, sessionId);
                } catch (Exception e) {
                    log.warn("模型调用失败: {} - {}", route.getModelName(), e.getMessage());
                    circuitBreaker.onFailure(route);
                    usageRecorder.record(ModelUsageRecorder.failure(route, sessionId, null, e.getMessage()));
                }
            }
            log.warn("注册表所有 LLM 模型均不可用，回退旧配置");
        }
        return legacyChatWithResult(messages, sessionId);
    }

    private ModelCallResult doChat(AiModelRoute route, List<Map<String, String>> messages, String sessionId) {
        OpenAiCompatClient client = openAiClient();
        ModelCallResult result = client.chatWithUsage(route.getBaseUrl(), route.getApiKey(),
                route.getRemoteModel(), route.getTemperature(), messages, route.getTimeoutMs());
        circuitBreaker.onSuccess(route);
        usageRecorder.record(ModelUsageRecorder.success(route, sessionId, result));
        return result;
    }

    // ==================== Embedding ====================

    /**
     * 统一 Embedding 入口：优先注册表 EMBEDDING 模型，失败自动切换，最终兜底旧配置。
     */
    public List<Float> embed(String text) {
        return OpenAiCompatClient.parseEmbedding(embedWithResult(text, null));
    }

    /**
     * 统一 Embedding 入口（返回完整结果，含用量）
     */
    public ModelCallResult embedWithResult(String text, String sessionId) {
        List<AiModelRoute> candidates = candidates(ModelTypeEnum.EMBEDDING.getCode());
        if (!candidates.isEmpty()) {
            AiModelRoute active = localActive.get(ModelTypeEnum.EMBEDDING.getCode());
            if (active != null && circuitBreaker.allow(active)) {
                try {
                    return doEmbed(active, text, sessionId);
                } catch (Exception e) {
                    log.warn("当前生效 Embedding 模型调用失败: {} - {}", active.getModelName(), e.getMessage());
                    circuitBreaker.onFailure(active);
                    usageRecorder.record(ModelUsageRecorder.failure(active, sessionId, null, e.getMessage()));
                }
            }
            for (AiModelRoute route : candidates) {
                if (active != null && route.getId().equals(active.getId())) {
                    continue;
                }
                if (!circuitBreaker.allow(route)) {
                    continue;
                }
                try {
                    return doEmbed(route, text, sessionId);
                } catch (Exception e) {
                    log.warn("Embedding 模型调用失败: {} - {}", route.getModelName(), e.getMessage());
                    circuitBreaker.onFailure(route);
                    usageRecorder.record(ModelUsageRecorder.failure(route, sessionId, null, e.getMessage()));
                }
            }
            log.warn("注册表所有 EMBEDDING 模型均不可用，回退旧配置");
        }
        return legacyEmbedWithResult(text, sessionId);
    }

    private ModelCallResult doEmbed(AiModelRoute route, String text, String sessionId) {
        OpenAiCompatClient client = openAiClient();
        ModelCallResult result = client.embedWithUsage(route.getBaseUrl(), route.getApiKey(),
                route.getRemoteModel(), text, route.getTimeoutMs());
        circuitBreaker.onSuccess(route);
        usageRecorder.record(ModelUsageRecorder.success(route, sessionId, result));
        return result;
    }

    // ==================== 测试连接 ====================

    /**
     * 测试指定模型连通性（不记录用量，不触发熔断计数）。
     */
    public boolean testConnect(AiModelRoute route) {
        if (route == null) {
            return false;
        }
        try {
            OpenAiCompatClient client = openAiClient();
            if (ModelTypeEnum.EMBEDDING.getCode().equals(route.getModelType())) {
                client.embedWithUsage(route.getBaseUrl(), route.getApiKey(), route.getRemoteModel(), "ping", route.getTimeoutMs());
            } else {
                client.chatWithUsage(route.getBaseUrl(), route.getApiKey(), route.getRemoteModel(),
                        route.getTemperature(), List.of(Map.of("role", "user", "content", "ping")), route.getTimeoutMs());
            }
            return true;
        } catch (Exception e) {
            log.warn("测试连接失败: {} - {}", route.getModelName(), e.getMessage());
            return false;
        }
    }

    // ==================== 内部 ====================

    private List<AiModelRoute> candidates(String modelType) {
        return localRegistry.getOrDefault(modelType, List.of());
    }

    private OpenAiCompatClient openAiClient() {
        if (openAiClient == null) {
            synchronized (this) {
                if (openAiClient == null) {
                    int timeout = modelProperties.getTimeoutSeconds() == null ? 30 : modelProperties.getTimeoutSeconds();
                    openAiClient = new OpenAiCompatClient(timeout);
                }
            }
        }
        return openAiClient;
    }

    private ModelCallResult legacyChatWithResult(List<Map<String, String>> messages, String sessionId) {
        long start = System.currentTimeMillis();
        String text = legacyClient().chat(messages);
        long latency = System.currentTimeMillis() - start;
        return ModelCallResult.of(text, null, null, null, latency);
    }

    private ModelCallResult legacyEmbedWithResult(String text, String sessionId) {
        long start = System.currentTimeMillis();
        List<Float> vector = legacyClient().embed(text);
        long latency = System.currentTimeMillis() - start;
        return ModelCallResult.of(JSON.toJSONString(vector), null, null, null, latency);
    }

    private DashscopeModelClient legacyClient() {
        if (legacyClient == null) {
            synchronized (this) {
                if (legacyClient == null) {
                    legacyClient = new DashscopeModelClient(modelProperties, embeddingProperties);
                }
            }
        }
        return legacyClient;
    }
}
