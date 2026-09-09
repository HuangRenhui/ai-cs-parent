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

    /**
     * 单轮对话调用（仅用户提示词）
     * @param prompt 用户提示词
     * @return 模型回答文本
     * @throws IOException 提示词为空或模型调用失败时抛出
     */
    public String call(String prompt) throws IOException {
        if (!StringUtils.hasText(prompt)) {
            throw new IOException("调用大模型异常: 提示词为空");
        }
        try {
            return modelRouter.chat(List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            // 包装为IOException，保持对上层Service的异常语义不变
            throw new IOException("调用大模型异常: " + e.getMessage(), e);
        }
    }

    /**
     * 带系统提示词的对话调用
     * @param systemPrompt 系统提示词（可为空，空则不携带system消息）
     * @param userPrompt 用户提示词
     * @return 模型回答文本
     * @throws IOException 用户提示词为空或模型调用失败时抛出
     */
    public String callWithSystem(String systemPrompt, String userPrompt) throws IOException {
        List<Map<String, String>> messages = new ArrayList<>();
        // 系统提示词非空才加入消息列表
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

    /**
     * 多消息列表对话调用
     * 将宽松的Map消息结构归一化为role/content字符串对，role缺省按user处理
     * @param messages 消息列表（每条含role、content键）
     * @return 模型回答文本
     * @throws IOException 消息为空或模型调用失败时抛出
     */
    public String callWithMessages(List<Map<String, Object>> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            throw new IOException("调用大模型异常: 提示词为空");
        }
        // 归一化消息结构，防止role/content为非字符串类型导致模型接口报错
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
