package com.ai.cs.common.llm;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议客户端（占位）
 *
 * <p>TODO 后续实现：chat 走 {@code {baseUrl}/chat/completions}、embedding 走 {@code {baseUrl}/embeddings}
 * （本地 Ollama {@code /v1} 与在线 DashScope compatible-mode / OpenAI / DeepSeek 协议一致）。
 * 需按 {@code timeoutMs}（缺省取构造值）设超时；解析 {@code choices[0].message.content} 与
 * {@code usage.prompt_tokens/completion_tokens/total_tokens}（部分本地模型不返回 usage，需按可空处理）；
 * 返回结构异常或文本为空时抛 {@link ModelCallException}。本类为纯工具类，无 Spring 依赖。</p>
 *
 * <p>当前不发起任何 HTTP 调用：四个调用方法一律抛 {@link ModelCallException}，
 * 使上层既有的降级路径（回退旧配置/返回繁忙文案）自然生效；调用方需自行捕获。</p>
 *
 * @author ai-cs
 */
@Slf4j
public class OpenAiCompatClient {

    /**
     * @param timeoutSeconds 默认读超时(秒)，下限 5 秒；单次调用可用 timeoutMs 覆盖
     */
    public OpenAiCompatClient(int timeoutSeconds) {
        log.info("[占位] OpenAI 兼容客户端构造 timeoutSeconds={}（不建立 HTTP 连接）", timeoutSeconds);
    }

    /**
     * 对话补全（占位：直接抛异常）
     *
     * @param baseUrl     服务根地址，例如 http://localhost:11434/v1
     * @param apiKey      密钥（可为空，本地 Ollama 通常不需要）
     * @param model       上游模型标识，例如 qwen-plus / llama3
     * @param temperature 温度
     * @param messages    消息列表，元素为 {role: system|user|assistant, content: str}
     * @return 不返回（抛异常）
     */
    public String chat(String baseUrl, String apiKey, String model, BigDecimal temperature,
                       List<Map<String, String>> messages) {
        throw new ModelCallException("对话模型调用为占位实现，后端未接入 OpenAI 兼容协议");
    }

    /**
     * 对话补全（带用量与耗时，占位：直接抛异常）
     *
     * @param timeoutMs 单次调用超时(毫秒)，null 则用构造默认
     */
    public ModelCallResult chatWithUsage(String baseUrl, String apiKey, String model, BigDecimal temperature,
                                         List<Map<String, String>> messages, Integer timeoutMs) {
        throw new ModelCallException("对话模型调用为占位实现，后端未接入 OpenAI 兼容协议");
    }

    /**
     * 向量化（占位：直接抛异常）
     *
     * @param text 待向量化文本
     * @return 不返回（抛异常）
     */
    public List<Float> embed(String baseUrl, String apiKey, String model, String text) {
        throw new ModelCallException("向量化调用为占位实现，后端未接入 OpenAI 兼容协议");
    }

    /**
     * 向量化（带用量与耗时，占位：直接抛异常）
     */
    public ModelCallResult embedWithUsage(String baseUrl, String apiKey, String model, String text, Integer timeoutMs) {
        throw new ModelCallException("向量化调用为占位实现，后端未接入 OpenAI 兼容协议");
    }

    /**
     * 从调用结果解析向量（占位：返回 null）
     *
     * <p>TODO 后续实现：解析 {@code data[0].embedding} 为 {@code List<Float>}，结构异常返回空集合。</p>
     */
    public static List<Float> parseEmbedding(ModelCallResult result) {
        log.info("[占位] 向量解析未实现，返回 null");
        return null;
    }
}
