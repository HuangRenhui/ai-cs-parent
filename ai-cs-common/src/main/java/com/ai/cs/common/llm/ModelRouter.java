package com.ai.cs.common.llm;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型路由器（占位）
 *
 * <p>原实现：按能力（模型类型）选取当前生效模型并做多模型故障转移；注册表由 base-service
 * （{@code /system/ai-model}）写入 Redis（Hash {@code ai:model:registry}），本组件在 base-service、
 * agent、knowledge 中复用。某能力未登记任何模型时回退旧配置 {@link DashscopeModelClient}；
 * 连续失败达到 {@code failThreshold} 由 {@link ModelCircuitBreaker} 摘除并冷却 60s；
 * 每次调用经 {@link ModelUsageRecorder} 写 Redis 流，由 ai-cs-job 落库。</p>
 *
 * <p>TODO 后续实现：候选池刷新（{@link #refreshFromRedis} 带 5 秒防抖、{@link #registerLocal} 同时写 Redis）、
 * 按 priority 排序与 active 优先、超限顺延降级、熔断摘除与健康标记、用量记录。</p>
 *
 * <p>当前候选池恒为空（不读 Redis、不注册），因此 {@link #pool} 返回空列表、{@link #active} 返回 null、
 * {@link #isEmpty} 恒 true；{@link #chat}／{@link #chatForType}／{@link #embed} 一律抛
 * {@link ModelCallException}（由上层降级处理），{@link #testConnect} 恒 false。</p>
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

    /**
     * 默认能力对话（占位：抛异常）
     *
     * @return 不返回（抛异常）
     */
    public String chat(List<Map<String, String>> messages) {
        return chatForType(DEFAULT_LLM_TYPE, messages, null);
    }

    /**
     * 对话（带会话ID，用于用量关联）（占位：抛异常）
     */
    public String chat(List<Map<String, String>> messages, String sessionId) {
        return chatForType(DEFAULT_LLM_TYPE, messages, sessionId);
    }

    /**
     * 指定能力类型的对话（占位：抛异常）
     */
    public String chatForType(String modelType, List<Map<String, String>> messages) {
        return chatForType(modelType, messages, null);
    }

    /**
     * 指定能力类型的对话（带会话ID）（占位：抛异常）
     *
     * @return 不返回（抛异常）
     */
    public String chatForType(String modelType, List<Map<String, String>> messages, String sessionId) {
        log.warn("[占位] 模型路由对话未实现 modelType={} sessionId={}", modelType, sessionId);
        throw new ModelCallException("对话模型调用为占位实现，后端未接入模型路由");
    }

    /**
     * 向量化（占位：抛异常）
     *
     * @return 不返回（抛异常）
     */
    public List<Float> embed(String text) {
        return embed(text, null);
    }

    /**
     * 向量化（带会话ID）（占位：抛异常）
     */
    public List<Float> embed(String text, String sessionId) {
        log.warn("[占位] 模型路由向量化未实现 sessionId={}", sessionId);
        throw new ModelCallException("向量化调用为占位实现，后端未接入模型路由");
    }

    /**
     * 测试模型连通性（占位：恒 false）
     *
     * @return false（未执行真实探测）
     */
    public boolean testConnect(AiModelRoute route) {
        log.info("[占位] 模型连通性测试未实现 routeId={}", route == null ? null : route.getId());
        return false;
    }

    /**
     * 注册本地模型池（占位：不注册、不写 Redis）
     *
     * <p>TODO 后续实现：写入本地候选池并按 priority/active 排序，同时同步写 Redis 注册表。</p>
     */
    public void registerLocal(List<AiModelRoute> routes) {
        log.info("[占位] 本地模型池注册未实现，routes={}", routes == null ? 0 : routes.size());
    }

    /**
     * 从 Redis 拉取启用模型刷新本地池（占位：不读 Redis）
     *
     * <p>TODO 后续实现：带 5 秒防抖读取 {@code ai:model:registry}，重建候选池；
     * base-service 的「设为生效/启停」据此在数秒内传播到各服务，又不至于每次请求打 Redis。</p>
     */
    public void refreshFromRedis() {
        log.info("[占位] 模型注册表刷新未实现（不读 Redis）");
    }

    /**
     * 本地模型池是否为空（占位：池恒为空，故恒 true）
     */
    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    /**
     * 某能力的候选列表（占位：返回空列表）
     */
    public List<AiModelRoute> pool(String modelType) {
        return List.of();
    }

    /**
     * 某能力当前生效模型（占位：返回 null）
     */
    public AiModelRoute active(String modelType) {
        return null;
    }
}
