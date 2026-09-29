package com.ai.cs.common.llm;

import com.ai.cs.common.constant.RedisKeyConst;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 模型路由器
 *
 * <p>按能力（模型类型）选取当前生效模型并做多模型故障转移。注册表由 base-service
 * （{@code /system/ai-model}）写入 Redis（Hash {@link RedisKeyConst#AI_MODEL_REGISTRY}，field = modelType，
 * value = 该能力启用模型的 JSON 数组；{@link RedisKeyConst#AI_MODEL_ACTIVE} 存生效模型；
 * {@link RedisKeyConst#AI_MODEL_VERSION} 存版本号），本组件在 base-service、agent、knowledge 中复用。</p>
 *
 * <p>两条链路：</p>
 * <ul>
 *   <li><b>注册表链路</b>：候选池非空时，按「active 优先 → priority 升序」依次尝试，连续失败达
 *       {@code failThreshold} 由 {@link ModelCircuitBreaker} 摘除并冷却 60 秒；当日 token/成本配额用尽则顺延到下一候选。</li>
 *   <li><b>兜底配置链路</b>：某能力未登记任何模型（或候选全部熔断/超额）时，回退 {@link DashscopeModelClient}
 *       （读 yml 的 {@code ai.llm} / {@code ai.embedding}），保证「注册表为空也能跑」。</li>
 * </ul>
 *
 * <p>每次调用经 {@link ModelUsageRecorder} 写 Redis 流，由 ai-cs-job 落库；
 * {@link #chatForType} 等入口在调用前会按 5 秒防抖刷新注册表，避免每次请求都打 Redis。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelRouter {

    /** 默认对话能力类型：chat() 不带类型时使用 */
    private static final String DEFAULT_LLM_TYPE = ModelTypeEnum.LLM.getCode();

    /** 注册表刷新防抖窗口(毫秒)：base-service 变更后数秒内传播到各服务，又不至于每次请求打 Redis */
    private static final long REFRESH_DEBOUNCE_MS = 5_000L;

    /** 连通性探测的超时(毫秒)：探测要快，不占用正常调用预算 */
    private static final int PROBE_TIMEOUT_MS = 8_000;

    /** OpenAI 兼容客户端的默认读超时(秒) */
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;

    /** 常见供应方的 OpenAI 兼容默认入口：baseUrl 未配置时按 provider 兜底 */
    private static final Map<String, String> DEFAULT_BASE_URLS = Map.of(
            "dashscope", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "openai", "https://api.openai.com/v1",
            "deepseek", "https://api.deepseek.com/v1",
            "ollama", "http://localhost:11434/v1");

    /** type -> 该能力下已启用模型列表（按 active 优先 + priority 升序） */
    private final Map<String, List<AiModelRoute>> candidates = new ConcurrentHashMap<>();

    private final OpenAiCompatClient client = new OpenAiCompatClient(DEFAULT_TIMEOUT_SECONDS);

    /** 允许无 Redis 环境启动：无 Redis 时只走兜底配置链路 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** 兜底配置客户端：注册表为空时回退（读 ai.llm / ai.embedding） */
    @Resource
    private DashscopeModelClient legacyClient;

    @Resource
    private ModelCircuitBreaker circuitBreaker;

    @Resource
    private ModelUsageRecorder usageRecorder;

    /** 上次刷新注册表的时间(毫秒)，用于防抖 */
    private volatile long lastRefreshAt;

    /** 上次读到的注册表版本号，未变化则跳过重建 */
    private volatile String lastRegistryVersion;

    // ==================== 对话 ====================

    /**
     * 默认能力对话（LLM）
     */
    public String chat(List<Map<String, String>> messages) {
        return chatForType(DEFAULT_LLM_TYPE, messages, null);
    }

    /**
     * 默认能力对话（带会话ID，用于用量关联）
     */
    public String chat(List<Map<String, String>> messages, String sessionId) {
        return chatForType(DEFAULT_LLM_TYPE, messages, sessionId);
    }

    /**
     * 指定能力类型的对话
     */
    public String chatForType(String modelType, List<Map<String, String>> messages) {
        return chatForType(modelType, messages, null);
    }

    /**
     * 指定能力类型的对话（带会话ID）
     *
     * <p><b>两级重试结构</b>：外层遍历候选池做<b>故障转移</b>，内层按该模型的 {@code maxRetries}
     * 做<b>同模型重试</b>；某个候选重试耗尽后自动换下一个候选，全部候选都失败才收尾。</p>
     *
     * <p><b>收尾口径（关键，别改错）</b>：{@code tried} 记录「是否有候选被真正调用过」——</p>
     * <ul>
     *   <li>{@code tried == false}：没有候选可用（未登记 / 全被熔断 / 全部超出当日配额）→
     *       回退 yml 兜底链路，保证「注册表为空也能跑」；</li>
     *   <li>{@code tried == true}：调用过但都失败 → <b>抛异常，不降级到兜底模型</b>。
     *       这是有意为之：候选全挂说明上游故障，此时静默换兜底模型会把「故障」伪装成「成功」，
     *       调用方与监控都会失去感知（宁可失败得响亮）。</li>
     * </ul>
     *
     * @param modelType 能力类型，缺省按 LLM
     * @param messages  消息列表
     * @param sessionId 会话ID（可空，仅用于用量归集）
     * @return 模型输出文本
     * @throws ModelCallException 所有候选均调用失败（异常为最后一次失败原因）
     */
    public String chatForType(String modelType, List<Map<String, String>> messages, String sessionId) {
        String type = normalizeType(modelType);
        refreshIfStale();
        List<AiModelRoute> pool = pool(type);
        ModelCallException lastError = null;
        // 区分「没候选可用」与「候选都调用失败」两种收尾方式，语义见方法注释
        boolean tried = false;
        for (AiModelRoute route : pool) {
            if (!available(route)) {
                // 熔断中或配额用尽：跳过该候选，继续看下一个（不计入 tried，因为它根本没被调用）
                continue;
            }
            // 内层重试次数 = 1 次 + 该模型配置的 maxRetries（瞬时抖动优先靠重试解决，不急着换模型）
            int attempts = 1 + Math.max(0, nvl(route.getMaxRetries()));
            for (int i = 0; i < attempts; i++) {
                tried = true;
                long start = System.currentTimeMillis();
                try {
                    ModelCallResult result = client.chatWithUsage(baseUrlOf(route), route.getApiKey(),
                            route.getRemoteModel(), route.getTemperature(), messages, routeTimeoutMs(route));
                    onCallSuccess(route, sessionId, result);
                    return result.getText();
                } catch (Exception e) {
                    lastError = onCallFailure(route, sessionId, "对话", System.currentTimeMillis() - start, e, i + 1);
                    pauseBeforeRetry(i, attempts);
                }
            }
        }
        if (!tried) {
            // 该能力未登记模型，或候选全部被熔断/超配额 → 回退 yml 兜底链路
            return legacyChat(type, messages);
        }
        // 试过但全失败：不降级到兜底模型，把最后一次失败原样抛出
        throw lastError != null ? lastError : new ModelCallException("模型调用失败，无可用候选: " + type);
    }

    // ==================== 向量 ====================

    /**
     * 向量化
     */
    public List<Float> embed(String text) {
        return embed(text, null);
    }

    /**
     * 向量化（带会话ID）
     *
     * <p>重试/收尾口径与 {@link #chatForType} 完全一致（候选内重试 → 换候选 → tried 决定是否走兜底）。</p>
     *
     * @return 向量；全部候选失败且兜底不可用时抛 {@link ModelCallException}
     */
    public List<Float> embed(String text, String sessionId) {
        String type = ModelTypeEnum.EMBEDDING.getCode();
        refreshIfStale();
        List<AiModelRoute> pool = pool(type);
        ModelCallException lastError = null;
        // 同 chat：false 表示"没候选可用"（走兜底），true 表示"试过且全失败"（抛异常）
        boolean tried = false;
        for (AiModelRoute route : pool) {
            if (!available(route)) {
                continue;
            }
            int attempts = 1 + Math.max(0, nvl(route.getMaxRetries()));
            for (int i = 0; i < attempts; i++) {
                tried = true;
                long start = System.currentTimeMillis();
                try {
                    ModelCallResult result = client.embedWithUsage(baseUrlOf(route), route.getApiKey(),
                            route.getRemoteModel(), text, routeTimeoutMs(route));
                    List<Float> vector = OpenAiCompatClient.parseEmbedding(result);
                    if (vector.isEmpty()) {
                        throw new ModelCallException("向量化返回空向量");
                    }
                    onCallSuccess(route, sessionId, result);
                    return vector;
                } catch (Exception e) {
                    lastError = onCallFailure(route, sessionId, "向量化", System.currentTimeMillis() - start, e, i + 1);
                    pauseBeforeRetry(i, attempts);
                }
            }
        }
        if (!tried) {
            return legacyEmbed(type, text);
        }
        throw lastError != null ? lastError : new ModelCallException("向量化失败，无可用候选: " + type);
    }

    /** 同一模型再次调用前等待，避免超时重试立刻打满供应商。最后一次失败不再等待。 */
    private void pauseBeforeRetry(int failedAttemptIndex, int attempts) {
        if (failedAttemptIndex + 1 >= attempts) {
            return;
        }
        long delay = Math.min(2000L, 200L * (failedAttemptIndex + 1));
        long jitter = ThreadLocalRandom.current().nextLong(80);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("模型重试被中断");
        }
    }

    // ==================== 探测与注册表 ====================

    /**
     * 测试模型连通性：发一次最小请求（对话 ping / 向量 ping），并回写运行时健康标记
     *
     * @return true 表示连通
     */
    public boolean testConnect(AiModelRoute route) {
        if (route == null) {
            return false;
        }
        try {
            String type = normalizeType(route.getModelType());
            if (ModelTypeEnum.EMBEDDING.getCode().equals(type) || ModelTypeEnum.RERANK.getCode().equals(type)) {
                ModelCallResult result = client.embedWithUsage(baseUrlOf(route), route.getApiKey(),
                        route.getRemoteModel(), "ping", PROBE_TIMEOUT_MS);
                if (OpenAiCompatClient.parseEmbedding(result).isEmpty()) {
                    throw new ModelCallException("探测返回空向量");
                }
            } else {
                ModelCallResult result = client.chatWithUsage(baseUrlOf(route), route.getApiKey(),
                        route.getRemoteModel(), route.getTemperature(),
                        List.of(Map.of("role", "user", "content", "ping")), PROBE_TIMEOUT_MS);
                if (result.getText() == null || result.getText().isBlank()) {
                    throw new ModelCallException("探测返回空内容");
                }
            }
            circuitBreaker.onSuccess(route);
            route.setHealth(ModelHealthEnum.HEALTHY.getCode());
            return true;
        } catch (Exception e) {
            route.setHealth(ModelHealthEnum.DOWN.getCode());
            log.warn("模型连通性探测失败 id={} name={}: {}", route.getId(), route.getModelName(), e.getMessage());
            return false;
        }
    }

    /**
     * 注册本地模型池：写入候选池（active 优先 + priority 升序），并同步写 Redis 注册表
     *
     * <p>由 base-service 在启动预热、模型增删改/生效切换后调用；其它服务只需读 Redis。</p>
     */
    public void registerLocal(List<AiModelRoute> routes) {
        Map<String, List<AiModelRoute>> grouped = new HashMap<>();
        if (routes != null) {
            for (AiModelRoute route : routes) {
                if (route == null || route.getModelType() == null) {
                    continue;
                }
                grouped.computeIfAbsent(normalizeType(route.getModelType()), key -> new ArrayList<>()).add(route);
            }
        }
        Map<String, List<AiModelRoute>> sorted = new HashMap<>();
        grouped.forEach((type, list) -> sorted.put(type, sort(list)));

        candidates.clear();
        candidates.putAll(sorted);
        lastRefreshAt = System.currentTimeMillis();
        writeRegistryToRedis(sorted);
        log.info("本地模型池已注册: {}", describe(sorted));
    }

    /**
     * 从 Redis 拉取启用模型刷新本地池（带 {@value #REFRESH_DEBOUNCE_MS} 毫秒防抖）
     *
     * <p>版本号（{@link RedisKeyConst#AI_MODEL_VERSION}）未变化时直接跳过重建。</p>
     */
    public void refreshFromRedis() {
        doRefresh(false);
    }

    /**
     * 本地模型池是否为空（为空时上层会回退兜底配置链路）
     */
    public boolean isEmpty() {
        return candidates.values().stream().allMatch(List::isEmpty);
    }

    /**
     * 某能力的候选列表（按 active 优先 + priority 升序，只读视图）
     */
    public List<AiModelRoute> pool(String modelType) {
        return candidates.getOrDefault(normalizeType(modelType), List.of());
    }

    /**
     * 某能力当前生效模型（候选首位），无候选返回 null
     */
    public AiModelRoute active(String modelType) {
        List<AiModelRoute> pool = pool(modelType);
        return pool.isEmpty() ? null : pool.get(0);
    }

    // ==================== 内部 ====================

    /** 调用前按防抖窗口刷新注册表（避免每次请求打 Redis） */
    private void refreshIfStale() {
        if (System.currentTimeMillis() - lastRefreshAt < REFRESH_DEBOUNCE_MS) {
            return;
        }
        doRefresh(true);
    }

    /** 读 Redis 重建候选池；debounced=true 时二次校验防抖窗口 */
    private synchronized void doRefresh(boolean debounced) {
        long now = System.currentTimeMillis();
        if (debounced && now - lastRefreshAt < REFRESH_DEBOUNCE_MS) {
            return;
        }
        lastRefreshAt = now;
        StringRedisTemplate redis = this.redisTemplate;
        if (redis == null) {
            return;
        }
        try {
            // 版本号未变且本地已有候选 → 直接跳过重建（这是"大多数请求都不打 Redis"的关键优化）。
            // 额外加 !isEmpty() 是为了兜住"版本号没变但本地候选为空"的边界：比如启动早期
            // 注册表还没写入、或被别处 clear 过，此时必须重建，否则会一直走兜底链路。
            String version = redis.opsForValue().get(RedisKeyConst.AI_MODEL_VERSION);
            if (version != null && version.equals(lastRegistryVersion) && !isEmpty()) {
                return;
            }
            Map<Object, Object> entries = redis.opsForHash().entries(RedisKeyConst.AI_MODEL_REGISTRY);
            Map<String, List<AiModelRoute>> rebuilt = new HashMap<>();
            if (entries != null) {
                entries.forEach((field, value) -> {
                    List<AiModelRoute> routes = parseRoutes(value);
                    if (!routes.isEmpty()) {
                        rebuilt.put(normalizeType(String.valueOf(field)), sort(routes));
                    }
                });
            }
            candidates.clear();
            candidates.putAll(rebuilt);
            lastRegistryVersion = version;
            log.info("模型注册表已刷新: {}", describe(rebuilt));
        } catch (Exception e) {
            log.warn("刷新模型注册表失败（沿用本地候选池）: {}", e.getMessage());
        }
    }

    /** 把候选池写回 Redis 注册表（active 单独一份，版本号用于各服务判断是否刷新） */
    private void writeRegistryToRedis(Map<String, List<AiModelRoute>> sorted) {
        StringRedisTemplate redis = this.redisTemplate;
        if (redis == null) {
            return;
        }
        try {
            Map<String, String> registry = new HashMap<>();
            Map<String, String> activeMap = new HashMap<>();
            sorted.forEach((type, list) -> {
                registry.put(type, JSON.toJSONString(list));
                if (!list.isEmpty()) {
                    activeMap.put(type, JSON.toJSONString(list.get(0)));
                }
            });
            if (!registry.isEmpty()) {
                // 注册表（全量候选，JSON 数组）与生效表（每能力首位）分开存：
                // 其它服务读注册表重建候选池，管理页/诊断读生效表拿"当前用的是哪个模型"
                redis.opsForHash().putAll(RedisKeyConst.AI_MODEL_REGISTRY, registry);
                redis.opsForHash().putAll(RedisKeyConst.AI_MODEL_ACTIVE, activeMap);
            }
            // 版本号用毫秒时间戳即可：消费方只判断"是否变化"，不解析具体值；
            // 它是各服务跳过重建的依据（见 doRefresh 的版本判断）
            redis.opsForValue().set(RedisKeyConst.AI_MODEL_VERSION, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("写入模型注册表失败: {}", e.getMessage());
        }
    }

    /**
     * 候选是否可用：未被熔断且当日配额未用尽。
     *
     * <p>顺序有意为之——先判断熔断（内存判断，最廉价且是"已知故障"），再查配额（要读 Redis）。
     * 「不可用」不等于「调用失败」：它不写入失败用量、也不计入 {@code tried}。</p>
     */
    private boolean available(AiModelRoute route) {
        if (circuitBreaker.isOpen(route)) {
            log.warn("模型熔断中，跳过 modelId={} name={}", route.getId(), route.getModelName());
            return false;
        }
        if (overQuota(route)) {
            log.warn("模型当日配额已用尽，顺延到下一候选 modelId={} name={}", route.getId(), route.getModelName());
            return false;
        }
        return true;
    }

    /** 当日 token/成本配额是否已用尽（无 Redis 或未配置配额时视为未用尽） */
    private boolean overQuota(AiModelRoute route) {
        Long limitTokens = route.getDailyTokenLimit();
        BigDecimal limitCost = route.getDailyCostLimit();
        boolean hasTokenLimit = limitTokens != null && limitTokens > 0;
        boolean hasCostLimit = limitCost != null && limitCost.signum() > 0;
        if (!hasTokenLimit && !hasCostLimit) {
            return false;
        }
        StringRedisTemplate redis = this.redisTemplate;
        if (redis == null || route.getId() == null) {
            return false;
        }
        try {
            String key = ModelUsageRecorder.dailyKey(route.getId());
            // 当日计数由 ModelUsageRecorder 用 HINCRBY 原子累加：
            //  - tokens：当日累计 token 数；
            //  - costMicro：当日累计成本，单位为「元 ×10^6」的整数（整数存储避免浮点误差），
            //    所以这里要除回 10^6 再与配置的元价比较。
            Object tokensValue = redis.opsForHash().get(key, "tokens");
            Object costValue = redis.opsForHash().get(key, "costMicro");
            if (hasTokenLimit && tokensValue != null && Long.parseLong(tokensValue.toString()) >= limitTokens) {
                return true;
            }
            if (hasCostLimit && costValue != null) {
                BigDecimal used = new BigDecimal(costValue.toString()).divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
                return used.compareTo(limitCost) >= 0;
            }
            return false;
        } catch (Exception e) {
            // 读不到就按「未超限」放行（fail-open）：配额是成本保护，不该因为 Redis 抖动阻断业务
            log.debug("读取模型当日配额失败（按未超限处理）: {}", e.getMessage());
            return false;
        }
    }

    /** 调用成功：复位熔断、标记健康、记录用量 */
    private void onCallSuccess(AiModelRoute route, String sessionId, ModelCallResult result) {
        circuitBreaker.onSuccess(route);
        route.setHealth(ModelHealthEnum.HEALTHY.getCode());
        usageRecorder.record(ModelUsageRecorder.success(route, sessionId, result), route);
        log.debug("模型调用成功 modelId={} 耗时={}ms tokens={}", route.getId(), result.getLatencyMs(), result.getTotalTokens());
    }

    /** 调用失败：累计熔断、必要时标记 DOWN、记录失败用量，返回包装后的异常 */
    private ModelCallException onCallFailure(AiModelRoute route, String sessionId, String scene,
                                            long latencyMs, Exception e, int attempt) {
        ModelCallException error = e instanceof ModelCallException mce ? mce : new ModelCallException(scene + "调用异常: " + e.getMessage(), e);
        if (circuitBreaker.onFailure(route)) {
            route.setHealth(ModelHealthEnum.DOWN.getCode());
        }
        usageRecorder.record(ModelUsageRecorder.failure(route, sessionId, latencyMs, error.getMessage()), route);
        log.warn("模型调用失败 modelId={} name={} 第{}次尝试: {}", route.getId(), route.getModelName(), attempt, error.getMessage());
        return error;
    }

    /** 回退 yml 兜底链路（对话） */
    private String legacyChat(String type, List<Map<String, String>> messages) {
        try {
            log.info("未登记 {} 模型（或候选不可用），回退兜底配置链路 model={}", type, "");
            return legacyClient.chatMessages(messages);
        } catch (ModelCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCallException("注册表未登记 " + type + " 模型，且兜底配置链路不可用: " + e.getMessage(), e);
        }
    }

    /** 回退 yml 兜底链路（向量） */
    private List<Float> legacyEmbed(String type, String text) {
        try {
            log.info("未登记 {} 模型（或候选不可用），回退兜底配置链路", type);
            return legacyClient.embed(text);
        } catch (ModelCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCallException("注册表未登记 " + type + " 模型，且兜底配置链路不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 候选排序：<b>生效模型（active=1）优先 → priority 升序（空值排最后）→ id 升序</b>。
     *
     * <p>三级排序各有用意：</p>
     * <ul>
     *   <li>第一级用 {@code 1 - nvl(isActive)} 把「降序」折叠成升序比较：active=1 → 0 排最前，
     *       active=0/null → 1 排后；</li>
     *   <li>第二级 priority：同是 active（或同为备用）时按配置的优先级；</li>
     *   <li>第三级 id：兜底保证顺序<b>稳定可复现</b>，否则同优先级候选在不同实例上顺序可能不同，
     *       故障转移结果就不可预期。</li>
     * </ul>
     *
     * <p>返回 {@code List.copyOf}：候选池会被多线程读取，返回不可变视图可防止调用方误改顺序。</p>
     */
    private static List<AiModelRoute> sort(List<AiModelRoute> routes) {
        List<AiModelRoute> sorted = new ArrayList<>(routes);
        sorted.sort(Comparator
                .comparingInt((AiModelRoute r) -> 1 - nvl(r.getIsActive()))
                .thenComparingInt(r -> r.getPriority() == null ? Integer.MAX_VALUE : r.getPriority())
                .thenComparingLong(r -> r.getId() == null ? Long.MAX_VALUE : r.getId()));
        return List.copyOf(sorted);
    }

    /** 解析注册表 value（该能力启用模型 JSON 数组），结构异常返回空列表 */
    private static List<AiModelRoute> parseRoutes(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            List<AiModelRoute> routes = JSON.parseArray(value.toString(), AiModelRoute.class);
            return routes == null ? List.of() : routes;
        } catch (Exception e) {
            log.warn("解析模型注册表条目失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 模型服务地址：优先用登记值，缺省按 provider 取常见 OpenAI 兼容入口 */
    private static String baseUrlOf(AiModelRoute route) {
        if (route.getBaseUrl() != null && !route.getBaseUrl().isBlank()) {
            return route.getBaseUrl();
        }
        String provider = route.getProvider() == null ? "" : route.getProvider().toLowerCase(Locale.ROOT);
        String fallback = DEFAULT_BASE_URLS.get(provider);
        if (fallback == null) {
            throw new ModelCallException("模型服务地址未配置且无默认值 provider=" + route.getProvider());
        }
        return fallback;
    }

    private static Integer routeTimeoutMs(AiModelRoute route) {
        Integer timeoutMs = route.getTimeoutMs();
        return timeoutMs == null || timeoutMs <= 0 ? null : timeoutMs;
    }

    /** 能力类型统一大写，避免大小写差异导致查不到候选池 */
    private static String normalizeType(String modelType) {
        return modelType == null || modelType.isBlank() ? DEFAULT_LLM_TYPE : modelType.trim().toUpperCase(Locale.ROOT);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    /** 候选池摘要，便于启动日志排查 */
    private static String describe(Map<String, List<AiModelRoute>> pool) {
        if (pool.isEmpty()) {
            return "空（走兜底配置链路）";
        }
        StringBuilder builder = new StringBuilder();
        pool.forEach((type, list) -> {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(type).append('=').append(list.size());
        });
        return builder.toString();
    }
}
