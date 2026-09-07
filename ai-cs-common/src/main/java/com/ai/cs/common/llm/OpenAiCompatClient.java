package com.ai.cs.common.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容协议客户端：chat 走 {@code /chat/completions}，embedding 走 {@code /embeddings}。
 * 本地 Ollama({@code /v1}) 与在线(DashScope compatible-mode / OpenAI / DeepSeek) 协议一致，
 * 是实现"本地/在线零差异切换"的底座。
 *
 * <p>说明：纯工具类，无 Spring 依赖，字段通过构造/方法参数传入，便于复用与测试。</p>
 *
 * @author ai-cs
 */
@Slf4j
public class OpenAiCompatClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;

    public OpenAiCompatClient(int timeoutSeconds) {
        int sec = Math.max(5, timeoutSeconds);
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(sec, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 对话补全（OpenAI 兼容）
     *
     * @param baseUrl    服务根地址，例如 http://localhost:11434/v1 或 https://dashscope.aliyuncs.com/compatible-mode/v1
     * @param apiKey     密钥（可为空，本地 Ollama 通常不需要）
     * @param model      上游模型标识，例如 qwen-plus / llama3
     * @param temperature 温度
     * @param messages   消息列表，元素为 {role: system|user|assistant, content: str}
     * @return 模型输出文本
     */
    public String chat(String baseUrl, String apiKey, String model, BigDecimal temperature,
                       List<Map<String, String>> messages) {
        return chatWithUsage(baseUrl, apiKey, model, temperature, messages, null).getText();
    }

    /**
     * 对话补全（带用量与耗时）
     *
     * @param timeoutMs 单次调用超时(毫秒)，null 则用构造默认
     */
    public ModelCallResult chatWithUsage(String baseUrl, String apiKey, String model, BigDecimal temperature,
                                         List<Map<String, String>> messages, Integer timeoutMs) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new ModelCallException("模型 baseUrl/模型名未配置");
        }
        if (messages == null || messages.isEmpty()) {
            throw new ModelCallException("提示词为空");
        }
        String url = trimSlash(baseUrl) + "/chat/completions";
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        long start = System.currentTimeMillis();
        String respJson = post(url, apiKey, body, timeoutMs);
        long latency = System.currentTimeMillis() - start;

        JSONObject json = JSON.parseObject(respJson);
        JSONArray choices = json == null ? null : json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            log.error("chat/completions 返回无 choices: {}", respJson);
            throw new ModelCallException("对话模型返回结构异常");
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        String text = message == null ? null : message.getString("content");
        if (!StringUtils.hasText(text)) {
            throw new ModelCallException("对话模型返回空文本");
        }

        JSONObject usage = json.getJSONObject("usage");
        Integer promptTokens = usage == null ? null : usage.getInteger("prompt_tokens");
        Integer completionTokens = usage == null ? null : usage.getInteger("completion_tokens");
        Integer totalTokens = usage == null ? null : usage.getInteger("total_tokens");
        return ModelCallResult.of(text.trim(), promptTokens, completionTokens, totalTokens, latency);
    }

    /**
     * 向量化（OpenAI 兼容）
     *
     * @param baseUrl   服务根地址
     * @param apiKey    密钥
     * @param model     上游模型标识
     * @param text      待向量化文本
     * @return 向量
     */
    public List<Float> embed(String baseUrl, String apiKey, String model, String text) {
        return parseEmbedding(embedWithUsage(baseUrl, apiKey, model, text, null));
    }

    /**
     * 向量化（带用量与耗时）
     */
    public ModelCallResult embedWithUsage(String baseUrl, String apiKey, String model, String text, Integer timeoutMs) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new ModelCallException("模型 baseUrl/模型名未配置");
        }
        if (!StringUtils.hasText(text)) {
            throw new ModelCallException("待向量化文本为空");
        }
        String url = trimSlash(baseUrl) + "/embeddings";
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", List.of(text));

        long start = System.currentTimeMillis();
        String respJson = post(url, apiKey, body, timeoutMs);
        long latency = System.currentTimeMillis() - start;

        JSONObject json = JSON.parseObject(respJson);
        JSONArray data = json == null ? null : json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            log.error("/embeddings 返回无 data: {}", respJson);
            throw new ModelCallException("Embedding 返回结构异常");
        }
        JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
        if (embedding == null || embedding.isEmpty()) {
            throw new ModelCallException("Embedding 向量为空");
        }
        List<Float> values = new ArrayList<>(embedding.size());
        for (int i = 0; i < embedding.size(); i++) {
            values.add(embedding.getFloat(i));
        }
        // 把向量暂存到 text 字段（JSON 字符串），由调用方解析；用量从 usage 取
        JSONObject usage = json.getJSONObject("usage");
        Integer promptTokens = usage == null ? null : usage.getInteger("prompt_tokens");
        Integer totalTokens = usage == null ? null : usage.getInteger("total_tokens");
        ModelCallResult r = ModelCallResult.of(JSON.toJSONString(values), promptTokens, null, totalTokens, latency);
        return r;
    }

    /** 从 embedWithUsage 的 text 字段解析回向量 */
    @SuppressWarnings("unchecked")
    public static List<Float> parseEmbedding(ModelCallResult result) {
        if (result == null || !StringUtils.hasText(result.getText())) {
            return null;
        }
        return JSON.parseArray(result.getText(), Float.class);
    }

    private String post(String url, String apiKey, JSONObject body, Integer timeoutMs) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toJSONString(), JSON_TYPE));
        if (StringUtils.hasText(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        OkHttpClient client = this.http;
        if (timeoutMs != null && timeoutMs > 0) {
            client = this.http.newBuilder()
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .connectTimeout(Math.min(timeoutMs, 10000), TimeUnit.MILLISECONDS)
                    .writeTimeout(Math.min(timeoutMs, 10000), TimeUnit.MILLISECONDS)
                    .build();
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            String respJson = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("HTTP {} body={}", response.code(), respJson);
                throw new ModelCallException("模型服务调用失败: HTTP " + response.code());
            }
            return respJson;
        } catch (ModelCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCallException("模型服务连接异常: " + e.getMessage(), e);
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
