package com.ai.cs.common.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容协议客户端
 *
 * <p>chat 走 {@code {baseUrl}/chat/completions}、embedding 走 {@code {baseUrl}/embeddings}；
 * 本地 Ollama（{@code /v1}）、DashScope compatible-mode、OpenAI、DeepSeek 协议一致。
 * 鉴权统一用 {@code Authorization: Bearer <apiKey>}（本地 Ollama 可不传）。</p>
 *
 * <p>超时：构造值作为默认读超时（下限 5 秒），单次调用可用 {@code timeoutMs} 覆盖；
 * 覆盖时通过 {@link OkHttpClient#newBuilder()} 复用连接池，不额外创建线程池。</p>
 *
 * <p>返回结构异常、文本为空、非 2xx 时统一抛 {@link ModelCallException}，由上层（{@link ModelRouter}）
 * 走故障转移或回退旧配置链路。</p>
 *
 * @author ai-cs
 */
@Slf4j
public class OpenAiCompatClient {

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    /** 读超时下限(秒)：避免配置成 1、2 秒导致大模型必然超时 */
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final String CHAT_PATH = "/chat/completions";
    private static final String EMBEDDING_PATH = "/embeddings";
    /** DashScope 原生协议地址统一映射到 OpenAI 兼容入口 */
    private static final String DASHSCOPE_COMPATIBLE_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    /** 日志里回显响应体的最大长度 */
    private static final int LOG_BODY_LIMIT = 300;

    private final OkHttpClient httpClient;
    private final int defaultTimeoutMs;

    /**
     * @param timeoutSeconds 默认读超时(秒)，下限 5 秒；单次调用可用 timeoutMs 覆盖
     */
    public OpenAiCompatClient(int timeoutSeconds) {
        int seconds = Math.max(MIN_TIMEOUT_SECONDS, timeoutSeconds);
        this.defaultTimeoutMs = seconds * 1000;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(seconds, TimeUnit.SECONDS)
                .readTimeout(seconds, TimeUnit.SECONDS)
                .writeTimeout(seconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 对话补全
     *
     * @param baseUrl     服务根地址，例如 http://localhost:11434/v1
     * @param apiKey      密钥（可为空，本地 Ollama 通常不需要）
     * @param model       上游模型标识，例如 qwen-plus / llama3
     * @param temperature 温度（可为空）
     * @param messages    消息列表，元素为 {role: system|user|assistant, content: str}
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
        String url = resolveUrl(baseUrl, CHAT_PATH);
        if (messages == null || messages.isEmpty()) {
            throw new ModelCallException("对话消息为空，无法调用模型: " + url);
        }
        if (!hasText(model)) {
            throw new ModelCallException("对话模型标识未配置: " + url);
        }
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        long start = System.currentTimeMillis();
        JSONObject json = post(url, apiKey, body.toJSONString(), timeoutMs, "对话");
        long latency = System.currentTimeMillis() - start;

        String text = extractContent(json);
        if (!hasText(text)) {
            throw new ModelCallException("模型返回内容为空: " + abbreviate(json.toJSONString()));
        }
        JSONObject usage = json.getJSONObject("usage");
        return ModelCallResult.of(text,
                intOrNull(usage, "prompt_tokens"),
                intOrNull(usage, "completion_tokens"),
                intOrNull(usage, "total_tokens"),
                latency);
    }

    /**
     * 向量化
     *
     * @param text 待向量化文本
     * @return 向量；解析失败返回空集合
     */
    public List<Float> embed(String baseUrl, String apiKey, String model, String text) {
        return parseEmbedding(embedWithUsage(baseUrl, apiKey, model, text, null));
    }

    /**
     * 向量化（带用量与耗时）：结果 {@code text} 字段保存向量的 JSON 串，由 {@link #parseEmbedding} 解析
     */
    public ModelCallResult embedWithUsage(String baseUrl, String apiKey, String model, String text, Integer timeoutMs) {
        String url = resolveUrl(baseUrl, EMBEDDING_PATH);
        if (!hasText(text)) {
            throw new ModelCallException("向量化文本不能为空: " + url);
        }
        if (!hasText(model)) {
            throw new ModelCallException("向量模型标识未配置: " + url);
        }
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", text);

        long start = System.currentTimeMillis();
        JSONObject json = post(url, apiKey, body.toJSONString(), timeoutMs, "向量化");
        long latency = System.currentTimeMillis() - start;

        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new ModelCallException("向量化返回结构异常: " + abbreviate(json.toJSONString()));
        }
        JSONArray vector = data.getJSONObject(0).getJSONArray("embedding");
        if (vector == null || vector.isEmpty()) {
            throw new ModelCallException("向量化返回空向量: " + abbreviate(json.toJSONString()));
        }
        JSONObject usage = json.getJSONObject("usage");
        return ModelCallResult.of(vector.toJSONString(),
                intOrNull(usage, "prompt_tokens"),
                null,
                intOrNull(usage, "total_tokens"),
                latency);
    }

    /**
     * 从调用结果解析向量（结构异常/为空时返回空集合，不抛异常）
     */
    public static List<Float> parseEmbedding(ModelCallResult result) {
        if (result == null || !hasText(result.getText())) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(result.getText());
            if (array == null || array.isEmpty()) {
                return List.of();
            }
            List<Float> vector = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                Float value = array.getFloat(i);
                vector.add(value == null ? 0F : value);
            }
            return vector;
        } catch (Exception e) {
            log.warn("解析向量失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 归一化服务地址：去掉结尾斜杠与 {@code /chat/completions}、{@code /embeddings}；
     * DashScope 原生协议地址（{@code /api/v1/services/...}）自动映射到 compatible-mode 入口，
     * 使各模块 yml 里的历史地址无需修改即可复用本客户端。
     */
    public static String normalizeBaseUrl(String rawUrl) {
        if (!hasText(rawUrl)) {
            return null;
        }
        String url = rawUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith(CHAT_PATH)) {
            url = url.substring(0, url.length() - CHAT_PATH.length());
        } else if (url.endsWith(EMBEDDING_PATH)) {
            url = url.substring(0, url.length() - EMBEDDING_PATH.length());
        }
        if (url.contains("dashscope.aliyuncs.com") && url.contains("/api/v1/")) {
            return DASHSCOPE_COMPATIBLE_BASE;
        }
        return url;
    }

    // ==================== 内部 ====================

    /** 拼出最终请求地址，base url 缺失直接失败（不猜默认值） */
    private String resolveUrl(String baseUrl, String path) {
        String base = normalizeBaseUrl(baseUrl);
        if (!hasText(base)) {
            throw new ModelCallException("模型服务地址(baseUrl)未配置");
        }
        return base + path;
    }

    /** 发起 POST 并解析响应体为 JSON，非 2xx 或响应体异常抛 {@link ModelCallException} */
    private JSONObject post(String url, String apiKey, String jsonBody, Integer timeoutMs, String scene) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON_TYPE, jsonBody));
        if (hasText(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        OkHttpClient client = withTimeout(timeoutMs);
        try (Response response = client.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new ModelCallException(scene + "调用失败 HTTP " + response.code() + ": " + abbreviate(text));
            }
            if (!hasText(text)) {
                throw new ModelCallException(scene + "调用返回空响应: " + url);
            }
            try {
                JSONObject json = JSON.parseObject(text);
                if (json == null) {
                    throw new ModelCallException(scene + "返回结构异常: " + abbreviate(text));
                }
                return json;
            } catch (ModelCallException e) {
                throw e;
            } catch (Exception e) {
                throw new ModelCallException(scene + "返回非 JSON: " + abbreviate(text), e);
            }
        } catch (ModelCallException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelCallException(scene + "调用网络异常: " + e.getMessage(), e);
        }
    }

    /** 单次调用超时覆盖：复用连接池，避免为每个模型新建客户端 */
    private OkHttpClient withTimeout(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0 || timeoutMs == defaultTimeoutMs) {
            return httpClient;
        }
        return httpClient.newBuilder()
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    /** 取 choices[0].message.content；兼容部分服务返回 content 数组的结构 */
    private String extractContent(JSONObject json) {
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        if (message == null) {
            return null;
        }
        Object content = message.get("content");
        if (content instanceof String str) {
            return str;
        }
        if (content instanceof JSONArray array && !array.isEmpty()) {
            return array.getJSONObject(0).getString("text");
        }
        return null;
    }

    /** usage 可能缺失（本地模型常见），缺失返回 null */
    private static Integer intOrNull(JSONObject usage, String field) {
        if (usage == null) {
            return null;
        }
        return usage.getInteger(field);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 响应体截断，避免日志刷屏 */
    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= LOG_BODY_LIMIT ? text : text.substring(0, LOG_BODY_LIMIT) + "...";
    }
}
