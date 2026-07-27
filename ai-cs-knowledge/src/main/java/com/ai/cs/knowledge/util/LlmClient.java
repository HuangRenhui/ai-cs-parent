package com.ai.cs.knowledge.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 * @author huangrenhui
 * @date 2026/6/18 00:20
 * @description 大语言模型客户端
 */

@Slf4j
@Component
public class LlmClient {
    
    @Value("${llm.url:http://127.0.0.1:8000/v1/chat/completions}")
    private String llmUrl;
    
    @Value("${llm.api-key:xxx}")
    private String apiKey;
    
    @Value("${llm.model:qwen-7b}")
    private String model;
    
    @Value("${llm.temperature:0.2}")
    private Double temperature;
    
    private OkHttpClient client;

    @PostConstruct
    public void init() {
        this.client = new OkHttpClient();
        log.info("LlmClient初始化完成，URL: {}, Model: {}", llmUrl, model);
    }

    /**
     * 基础调用大模型方法（兼容旧接口，单 user 消息）
     * @param prompt 完整提示词（可携带约束/检索文档上下文）
     * @return 模型返回回答
     */
    public String call(String prompt) throws IOException {
        Map<String, Object> userMsg = Map.of("role", "user", "content", prompt);
        return callWithMessages(List.of(userMsg));
    }

    /**
     * 带 System Prompt 的调用方法（推荐）
     * <p>
     * 支持 system + user 双消息模式，充分利用模型的角色理解能力
     *
     * @param systemPrompt 系统提示词（角色限定、格式约束、边界约束）
     * @param userPrompt   用户提示词（问题、上下文、CoT、Few-shot）
     * @return 模型返回回答
     */
    public String callWithSystem(String systemPrompt, String userPrompt) throws IOException {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        return callWithMessages(messages);
    }

    /**
     * 通用多消息调用方法
     * <p>
     * 支持任意 roles 组合：system + user + assistant 多轮对话
     *
     * @param messages 消息列表，每项包含 "role" 和 "content"
     * @return 模型返回回答
     */
    @SuppressWarnings("unchecked")
    public String callWithMessages(List<Map<String, Object>> messages) throws IOException {
        // 组装请求体，通用OpenAI兼容格式
        JSONObject reqBody = new JSONObject();
        reqBody.put("model", model);
        reqBody.put("temperature", temperature); // 降低随机性，减少幻觉

        // 转换消息格式
        List<JSONObject> msgList = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("role", msg.get("role"));
            jsonMsg.put("content", msg.get("content"));
            msgList.add(jsonMsg);
        }
        reqBody.put("messages", msgList);

        RequestBody body = RequestBody.create(
                JSON.toJSONString(reqBody),
                MediaType.get("application/json;charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(llmUrl)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("调用大模型失败，状态码: {}", response.code());
                return "调用大模型失败";
            }
            JSONObject respJson = JSON.parseObject(response.body().string());
            // 提取模型回答内容
            return respJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            log.error("调用大模型异常", e);
            throw new IOException("调用大模型异常: " + e.getMessage(), e);
        }
    }
}
