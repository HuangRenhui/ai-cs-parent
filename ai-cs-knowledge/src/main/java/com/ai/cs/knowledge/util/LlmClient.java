package com.ai.cs.knowledge.util;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库侧 LLM 入口。底层统一走 {@link ModelRouter}：
 * 未登记对话模型时回退历史配置；登记后可实现本地/在线切换与故障转移。
 * 保留 call / callWithSystem / callWithMessages 给现有 Service，签名与异常语义不变。
 */
@Slf4j
@Component
public class LlmClient {

    @Resource
    private ModelRouter modelRouter;

    public String call(String prompt) throws IOException {
        if (!StringUtils.hasText(prompt)) {
            throw new IOException("调用大模型异常: 提示词为空");
        }
        try {
            return modelRouter.chat(List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            throw new IOException("调用大模型异常: " + e.getMessage(), e);
        }
    }

    public String callWithSystem(String systemPrompt, String userPrompt) throws IOException {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (!StringUtils.hasText(userPrompt)) {
            throw new IOException("调用大模型异常: 提示词为空");
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        try {
            return modelRouter.chat(messages);
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
            return modelRouter.chat(converted);
        } catch (ModelCallException e) {
            throw new IOException("调用大模型异常: " + e.getMessage(), e);
        }
    }
}
