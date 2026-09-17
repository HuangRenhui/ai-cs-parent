package com.ai.cs.common.llm;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 通义原生协议客户端（占位）
 *
 * <p>本类是「旧配置链路」的客户端：读取 {@link AiModelProperties}（{@code ai.llm} / {@code ai.embedding}）
 * 直接调用阿里云 DashScope 原生接口（chat 走 generation、embed 走 text-embedding）。当模型注册表
 * （{@link ModelRouter}）中未登记任何模型时，全系统回退到本客户端。</p>
 *
 * <p>TODO 后续实现：{@link #init} 按配置分别创建对话/向量两个 OkHttp 客户端（读超时分别取
 * {@code ai.llm} / {@code ai.embedding}）；{@link #chat}／{@link #chatWithSystem}／{@link #chatMessages}
 * 走 generation 接口，{@link #embed} 走 text-embedding 接口，{@link #embeddingDimension} 取配置的向量维度。</p>
 *
 * <p>当前不建立 HTTP 连接、不发起调用：三个对话方法与 {@link #embed} 一律抛
 * {@link ModelCallException}（由上层降级处理），{@link #embeddingDimension} 返回 0。</p>
 */
@Slf4j
@Component
public class DashscopeModelClient {

    @Resource
    private AiModelProperties properties;

    /**
     * 初始化 HTTP 客户端（占位：不创建连接）
     */
    @PostConstruct
    public void init() {
        log.info("[占位] DashScope 客户端初始化未实现（未建立 HTTP 连接）");
    }

    /**
     * 单轮对话（占位：直接抛异常）
     */
    public String chat(String prompt) {
        throw new ModelCallException("DashScope 对话调用为占位实现，后端未接入原生协议");
    }

    /**
     * 带系统提示词的对话（占位：直接抛异常）
     */
    public String chatWithSystem(String systemPrompt, String userPrompt) {
        throw new ModelCallException("DashScope 对话调用为占位实现，后端未接入原生协议");
    }

    /**
     * 多轮消息对话（占位：直接抛异常）
     */
    public String chatMessages(List<Map<String, String>> messages) {
        throw new ModelCallException("DashScope 对话调用为占位实现，后端未接入原生协议");
    }

    /**
     * 向量化（占位：直接抛异常）
     */
    public List<Float> embed(String text) {
        throw new ModelCallException("DashScope 向量化调用为占位实现，后端未接入原生协议");
    }

    /**
     * 向量维度（占位：返回 0 表示未知）
     *
     * <p>TODO 后续实现：返回 {@code ai.embedding} 配置的向量维度，用于集合维度校验。</p>
     */
    public int embeddingDimension() {
        log.info("[占位] 向量维度读取未实现，返回 0");
        return 0;
    }
}
