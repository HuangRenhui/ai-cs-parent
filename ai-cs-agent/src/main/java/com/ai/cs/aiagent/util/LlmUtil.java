package com.ai.cs.aiagent.util;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:54
 * @description TODO
 */

import com.ai.cs.aiagent.config.LlmProperties;
import com.ai.cs.common.dto.IntentDTO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LlmUtil {

    @Resource
    private LlmProperties llmProperties;

    // 意图识别Prompt
    private static final String INTENT_PROMPT = """
你是智能客服意图分析助手，请分析用户提问，严格只返回标准JSON，不要额外解释。
字段说明：
intent：取值只能是【咨询、查物流、退款、投诉、转人工】
entity：提取订单号、手机号、商品名称等关键信息，无则填空
用户问题：%s
""";

    // 普通对话Prompt
    private static final String CHAT_PROMPT = "你是专业电商客服，语气亲切简洁，根据上下文回答用户问题：%s\n对话历史：%s";

    /** 调用大模型通用方法 */
    private String callLlm(String prompt) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(llmProperties.getUrl());
            // 签名鉴权
            String auth = llmProperties.getApiKey() + ":" + llmProperties.getApiSecret();
            String base64Auth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64Auth);
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());

            // 组装请求体
            Map<String, Object> input = new HashMap<>();
            input.put("model", llmProperties.getModel());
            Map<String, String> content = new HashMap<>();
            content.put("role", "user");
            content.put("content", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", Map.of("messages", List.of(content)));
            requestBody.put("parameters", Map.of("result_format", "text"));

            StringEntity entity = new StringEntity(JSON.toJSONString(requestBody), ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String respJson = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject json = JSON.parseObject(respJson);
                return json.getJSONObject("output").getString("text");
            }
        } catch (Exception e) {
            log.error("大模型调用异常", e);
            return "服务繁忙，请稍后再试";
        }
    }

    /** 意图识别 */
    public IntentDTO getIntent(String userMsg) {
        String prompt = String.format(INTENT_PROMPT, userMsg);
        String jsonStr = callLlm(prompt);
        try {
            return JSON.parseObject(jsonStr, IntentDTO.class);
        } catch (Exception e) {
            IntentDTO dto = new IntentDTO();
            dto.setIntent("咨询");
            dto.setEntity("");
            return dto;
        }
    }

    /** 普通对话回答 */
    public String chatReply(String userMsg, String history) {
        String prompt = String.format(CHAT_PROMPT, userMsg, history);
        return callLlm(prompt);
    }
}
