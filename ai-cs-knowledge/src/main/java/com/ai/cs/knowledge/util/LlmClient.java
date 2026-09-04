package com.ai.cs.knowledge.util;

import com.ai.cs.common.llm.DashscopeModelClient;
import com.ai.cs.common.llm.ModelCallException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库侧 LLM 入口。实际调用 {@link DashscopeModelClient}，保留 call / callWithSystem 给现有 Service。
 */
@Slf4j
@Component
public class LlmClient {

    @Resource
    private DashscopeModelClient modelClient;

    public String call(String prompt) throws IOException {
        try {
            return modelClient.chat(prompt);
        } catch (ModelCallException e) {
            throw new IOException("调用大模型异常: " + e.getMessage(), e);
        }
    }

    public String callWithSystem(String systemPrompt, String userPrompt) throws IOException {
        try {
            return modelClient.chatWithSystem(systemPrompt, userPrompt);
        } catch (ModelCallException e) {
            throw new IOException("调用大模型异常: " + e.getMessage(), e);
        }
    }

    public String callWithMessages(List<Map<String, Object>> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            throw new IOException("调用大模型异常: 提示词为空");
        }
        List<Map<String, String>> converted = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            Object role = msg.get("role");
            Object content = msg.get("content");
            converted.add(Map.of(
                    "role", role == null ? "user" : String.valueOf(role),
                    "content", content == null ? "" : String.valueOf(content)));
        }
        try {
            return modelClient.chatMessages(converted);
        } catch (ModelCallException e) {
            throw new IOException("调用大模型异常: " + e.getMessage(), e);
        }
    }
}
