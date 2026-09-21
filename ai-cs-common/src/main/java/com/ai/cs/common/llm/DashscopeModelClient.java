package com.ai.cs.common.llm;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 兜底配置链路（yml bootstrap fallback）
 *
 * <p>读取 {@link AiModelProperties}（{@code ai.llm} / {@code ai.embedding}）里的静态配置，作为
 * 「没有登记任何注册表模型」时的兜底：{@link ModelRouter} 的候选池为空时，全系统回退到本客户端。</p>
 *
 * <p>协议统一走 {@link OpenAiCompatClient}（OpenAI 兼容）：yml 里若是 DashScope 原生地址，
 * 由 {@link OpenAiCompatClient#normalizeBaseUrl(String)} 自动映射到 compatible-mode 入口，
 * 因此无需改动各模块已有的 yml 配置，也不再单独维护一套通义原生协议实现。</p>
 *
 * <p>配置缺失（如未设 {@code AI_LLM_API_KEY}）时直接抛 {@link ModelCallException}，并给出明确提示，
 * 便于本地联调时一眼看出是配置问题而不是模型问题。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class DashscopeModelClient {

    /** 未配置超时时的默认读超时(秒) */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    /** embedding 维度缺省值（与 DashScope text-embedding-v3 一致） */
    private static final int DEFAULT_DIMENSION = 1024;

    @Resource
    private AiModelProperties properties;

    /** 内部 HTTP 客户端：由 yml 配置初始化，惰性兜底避免 PostConstruct 未执行时 NPE */
    private volatile OpenAiCompatClient client;

    /**
     * 初始化 HTTP 客户端（按 yml 的超时配置，下限 5 秒）
     */
    @PostConstruct
    public void init() {
        this.client = new OpenAiCompatClient(resolveTimeoutSeconds());
        log.info("兜底配置链路就绪: llm.model={} embedding.model={} dimension={}",
                properties.getLlm().getModel(), properties.getEmbedding().getModel(), embeddingDimension());
    }

    /**
     * 单轮对话
     */
    public String chat(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>(1);
        messages.add(message("user", prompt));
        return chatMessages(messages);
    }

    /**
     * 带系统提示词的对话
     */
    public String chatWithSystem(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>(2);
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        return chatMessages(messages);
    }

    /**
     * 多轮消息对话
     */
    public String chatMessages(List<Map<String, String>> messages) {
        AiModelProperties.Llm llm = properties.getLlm();
        if (!hasText(llm.getApiKey())) {
            throw new ModelCallException("未配置对话模型密钥（ai.llm.api-key 或环境变量 AI_LLM_API_KEY）");
        }
        return client().chat(llm.getUrl(), llm.getApiKey(), llm.getModel(), null, messages);
    }

    /**
     * 向量化：密钥优先取 {@code ai.embedding.api-key}，为空则回退 {@code ai.llm.api-key}
     */
    public List<Float> embed(String text) {
        AiModelProperties.Embedding embedding = properties.getEmbedding();
        String apiKey = hasText(embedding.getApiKey()) ? embedding.getApiKey() : properties.getLlm().getApiKey();
        if (!hasText(apiKey)) {
            throw new ModelCallException("未配置向量模型密钥（ai.embedding.api-key 或 ai.llm.api-key）");
        }
        return client().embed(embedding.getUrl(), apiKey, embedding.getModel(), text);
    }

    /**
     * 向量维度：取 {@code ai.embedding.dimension}，非法值回退 {@value #DEFAULT_DIMENSION}
     */
    public int embeddingDimension() {
        Integer dimension = properties.getEmbedding().getDimension();
        return dimension == null || dimension <= 0 ? DEFAULT_DIMENSION : dimension;
    }

    // ==================== 内部 ====================

    /** 惰性获取客户端（容器外单测直接 new 时也能用，不依赖 @PostConstruct） */
    private OpenAiCompatClient client() {
        OpenAiCompatClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = new OpenAiCompatClient(resolveTimeoutSeconds());
                }
                current = client;
            }
        }
        return current;
    }

    /** 对话与向量各配超时，取二者较大值并保证下限 */
    private int resolveTimeoutSeconds() {
        int llmTimeout = properties.getLlm().getTimeoutSeconds();
        int embeddingTimeout = properties.getEmbedding().getTimeoutSeconds();
        return Math.max(DEFAULT_TIMEOUT_SECONDS, Math.max(llmTimeout, embeddingTimeout));
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>(2);
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
