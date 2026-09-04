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
 */
@Slf4j
@Component
public class DashscopeModelClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    @Resource
    private AiModelProperties properties;

    private OkHttpClient chatHttp;
    private OkHttpClient embedHttp;

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

    public String chat(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new ModelCallException("提示词为空");
        }
        return chatMessages(List.of(Map.of("role", "user", "content", prompt)));
    }

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

    public String chatMessages(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new ModelCallException("提示词为空");
        }
        AiModelProperties.Llm llm = properties.getLlm();
        if (!StringUtils.hasText(llm.getUrl()) || !StringUtils.hasText(llm.getApiKey())) {
            throw new ModelCallException("未配置 ai.llm.api-key / url");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", llm.getModel());
        body.put("input", Map.of("messages", messages));
        body.put("parameters", Map.of("result_format", "text"));

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

    public List<Float> embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new ModelCallException("待向量化文本为空");
        }
        AiModelProperties.Embedding embedding = properties.getEmbedding();
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
            List<Float> values = new ArrayList<>(vector.size());
            for (int i = 0; i < vector.size(); i++) {
                values.add(vector.getFloat(i));
            }
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

    public int embeddingDimension() {
        return properties.getEmbedding().getDimension();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
