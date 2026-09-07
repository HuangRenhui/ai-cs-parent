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
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelRouter {

    private static final String DEFAULT_LLM_TYPE = ModelTypeEnum.LLM.getCode();

    /** type -> 该能力下已启用模型列表（按 priority 升序 + active 优先） */
    private final Map<String, List<AiModelRoute>> candidates = new ConcurrentHashMap<>();

    private final OpenAiCompatClient client = new OpenAiCompatClient(20);

    @Resource
    private StringRedisTemplate redisTemplate;

    /** 旧配置回退客户端(DashScope 原生协议，来自 ai.llm/ai.embedding) */
    @Resource
    private DashscopeModelClient legacyClient;

    /** 最近一次从 Redis 拉取注册表的时间戳（毫秒），用于防抖 */
    private volatile long lastRefresh = 0L;

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
     * 指定能力类型的对话（预留：对话页可选模型/视觉等）
     */
    public String chatForType(String modelType, List<Map<String, String>> messages) {
        ensureLoaded();
        List<AiModelRoute> pool = pool(modelType);
        if (pool.isEmpty()) {
            // 兼容旧配置：未注册模型时走历史 DashScope 配置，不破坏原链路
            return legacyClient.chatMessages(messages);
        }
        Exception last = null;
        for (AiModelRoute route : pool) {
            // 跳过已 DOWN 的模型
            if (ModelHealthEnum.DOWN.getCode().equalsIgnoreCase(route.getHealth())) {
                continue;
            }
            try {
                String text = client.chat(route.getBaseUrl(), route.getApiKey(),
                        route.getRemoteModel(), route.getTemperature(), messages);
                markHealthy(route);
                return text;
            } catch (Exception e) {
                last = e;
                log.warn("模型[{}]调用失败，尝试切换到候选。原因: {}", route.getModelName(), e.getMessage());
                markDown(route);
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
     */
    public List<Float> embed(String text) {
        ensureLoaded();
        List<AiModelRoute> pool = pool(ModelTypeEnum.EMBEDDING.getCode());
        if (pool.isEmpty()) {
            return legacyClient.embed(text);
        }
        Exception last = null;
        for (AiModelRoute route : pool) {
            if (ModelHealthEnum.DOWN.getCode().equalsIgnoreCase(route.getHealth())) {
                continue;
            }
            try {
                List<Float> vector = client.embed(route.getBaseUrl(), route.getApiKey(),
                        route.getRemoteModel(), text);
                markHealthy(route);
                return vector;
            } catch (Exception e) {
                last = e;
                log.warn("向量模型[{}]调用失败，尝试切换到候选。原因: {}", route.getModelName(), e.getMessage());
                markDown(route);
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
     * 单模型连通性测试（供管理页"测试连接"与健康探测使用），不修改路由池。
     *
     * @return true=连接成功
     */
    public boolean testConnect(AiModelRoute route) {
        if (route == null) {
            return false;
        }
        try {
            if (ModelTypeEnum.EMBEDDING.getCode().equalsIgnoreCase(route.getModelType())) {
                List<Float> v = client.embed(route.getBaseUrl(), route.getApiKey(),
                        route.getRemoteModel(), "ping");
                return v != null && !v.isEmpty();
            }
            String text = client.chat(route.getBaseUrl(), route.getApiKey(),
                    route.getRemoteModel(), route.getTemperature(),
                    List.of(Map.of("role", "user", "content", "ping")));
            return StringUtils.hasText(text);
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
        candidates.clear();
        if (routes == null || routes.isEmpty()) {
            return;
        }
        for (AiModelRoute r : routes) {
            if (r == null || !isEnabled(r)) {
                continue;
            }
            candidates.computeIfAbsent(r.getModelType(), k -> new ArrayList<>()).add(r);
        }
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
        if (redisTemplate == null || now - lastRefresh < REFRESH_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - lastRefresh < REFRESH_INTERVAL_MS) {
                return;
            }
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

    private int prio(Integer p) {
        return p == null ? 0 : p;
    }

    private void markDown(AiModelRoute route) {
        route.setHealth(ModelHealthEnum.DOWN.getCode());
        // 运行时降级标记，不落 Redis（避免写放大），由后台健康探测决定是否恢复
    }

    private void markHealthy(AiModelRoute route) {
        route.setHealth(ModelHealthEnum.HEALTHY.getCode());
    }
}
