package com.ai.cs.common.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通义原生协议：chat 走 generation，embed 走 text-embedding。知识库与 Agent 共用，避免再依赖 localhost:8000。
 *
 * <p>本类是"旧配置链路"的客户端：读取 {@link AiModelProperties}（{@code ai.llm} / {@code ai.embedding}）
 * 直接调用阿里云 DashScope 原生接口。当模型注册表（{@link ModelRouter}）中未登记任何模型时，
 * 全系统回退到本客户端，保证不登记模型时原对话/向量链路完全不变。</p>
 */
@Slf4j
@Component
public class DashscopeModelClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    @Resource
    private AiModelProperties properties;

    private OkHttpClient chatHttp;
    private OkHttpClient embedHttp;

    /**
     * 初始化 HTTP 客户端：对话与向量各一份，读超时分别取 ai.llm / ai.embedding 配置。
     * 超时下限 5 秒，避免配置过小导致频繁超时。
     */
    @PostConstruct
    public void init() {
        int chatSec = Math.max(5, properties.getLlm().getTimeoutSeconds());
        int embedSec = Math.max(5, properties.getEmbedding().getTimeoutSeconds());
        this.chatHttp = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(chatSec, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.embedHttp = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(embedSec, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 单轮对话：把 prompt 包装成一条 user 消息后走多消息接口
     *
     * @param prompt 用户提示词
     * @return 模型输出文本
     */
    public String chat(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new ModelCallException("提示词为空");
        }
        return chatMessages(List.of(Map.of("role", "user", "content", prompt)));
    }

    /**
     * 带 system 设定的对话
     *
     * @param systemPrompt 系统提示词（为空则不带 system 消息）
     * @param userPrompt   用户提示词
     * @return 模型输出文本
     */
    public String chatWithSystem(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (!StringUtils.hasText(userPrompt)) {
            throw new ModelCallException("用户提示词为空");
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        return chatMessages(messages);
    }

    /**
     * 多消息对话：DashScope generation 接口
     *
     * @param messages 消息列表，元素为 {role: system|user|assistant, content: str}
     * @return 模型输出文本
     */
    public String chatMessages(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new ModelCallException("提示词为空");
        }
        AiModelProperties.Llm llm = properties.getLlm();
        // 未配置密钥/地址直接失败，提示配置项名，便于排查
        if (!StringUtils.hasText(llm.getUrl()) || !StringUtils.hasText(llm.getApiKey())) {
            throw new ModelCallException("未配置 ai.llm.api-key / url");
        }
        // DashScope 原生协议报文：input.messages + parameters.result_format=text 表示只要纯文本
        Map<String, Object> body = new HashMap<>();
        body.put("model", llm.getModel());
        body.put("input", Map.of("messages", messages));
        body.put("parameters", Map.of("result_format", "text"));

        // 原生协议使用 Basic 认证（apiKey:apiSecret），与兼容模式的 Bearer 不同
        String auth = defaultString(llm.getApiKey()) + ":" + defaultString(llm.getApiSecret());
        String basic = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        Request request = new Request.Builder()
                .url(llm.getUrl())
                .header("Authorization", "Basic " + basic)
                .post(RequestBody.create(JSON.toJSONString(body), JSON_TYPE))
                .build();
        try (Response response = chatHttp.newCall(request).execute()) {
            String respJson = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("对话模型 HTTP {} body={}", response.code(), respJson);
                throw new ModelCallException("对话模型调用失败: HTTP " + response.code());
            }
            JSONObject json = JSON.parseObject(respJson);
            if (json == null || json.getJSONObject("output") == null) {
                log.error("对话模型返回结构异常: {}", respJson);
                throw new ModelCallException("对话模型返回结构异常");
            }
            String text = json.getJSONObject("output").getString("text");
            if (!StringUtils.hasText(text)) {
                throw new ModelCallException("对话模型返回空文本");
            }
            return text.trim();
        } catch (ModelCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCallException("对话模型调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * 文本向量化：DashScope text-embedding 接口
     *
     * @param text 待向量化文本
     * @return 向量（维度应与 ai.embedding.dimension 配置一致）
     */
    public List<Float> embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new ModelCallException("待向量化文本为空");
        }
        AiModelProperties.Embedding embedding = properties.getEmbedding();
        // embedding 未单独配置密钥时回退复用 LLM 的 apiKey（同一 DashScope 账号场景）
        String apiKey = StringUtils.hasText(embedding.getApiKey()) ? embedding.getApiKey() : properties.getLlm().getApiKey();
        if (!StringUtils.hasText(embedding.getUrl()) || !StringUtils.hasText(apiKey)) {
            throw new ModelCallException("未配置 Embedding（ai.embedding 或 ai.llm.api-key）");
        }
        JSONObject body = new JSONObject();
        body.put("model", embedding.getModel());
        body.put("input", Map.of("texts", List.of(text)));
        body.put("parameters", Map.of("dimension", embedding.getDimension()));

        Request request = new Request.Builder()
                .url(embedding.getUrl())
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                .build();
        try (Response response = embedHttp.newCall(request).execute()) {
            String respJson = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Embedding HTTP {} body={}", response.code(), respJson);
                throw new ModelCallException("Embedding 调用失败: HTTP " + response.code());
            }
            JSONObject json = JSON.parseObject(respJson);
            JSONArray embeddings = json == null || json.getJSONObject("output") == null
                    ? null : json.getJSONObject("output").getJSONArray("embeddings");
            if (embeddings == null || embeddings.isEmpty()) {
                log.error("Embedding 返回结构异常: {}", respJson);
                throw new ModelCallException("Embedding 返回结构异常");
            }
            JSONArray vector = embeddings.getJSONObject(0).getJSONArray("embedding");
            if (vector == null || vector.isEmpty()) {
                throw new ModelCallException("Embedding 向量为空");
            }
            // 只取第一条（本接口每次只向量了一段文本）
            List<Float> values = new ArrayList<>(vector.size());
            for (int i = 0; i < vector.size(); i++) {
                values.add(vector.getFloat(i));
            }
            // 维度与配置不一致仅告警不中断：通常意味着换了模型但没同步改配置
            if (values.size() != embedding.getDimension()) {
                log.warn("Embedding 维度 {} 与配置 {} 不一致", values.size(), embedding.getDimension());
            }
            return values;
        } catch (ModelCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCallException("Embedding 调用异常: " + e.getMessage(), e);
        }
    }

    /** 当前配置的向量维度（知识库建表/校验用） */
    public int embeddingDimension() {
        return properties.getEmbedding().getDimension();
    }

    /** null 安全转空串（拼接 Basic 认证时避免 "null" 字面量） */
    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
