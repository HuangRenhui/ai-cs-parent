package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 坐席辅助服务：推荐回复、会话摘要、下一句建议
 * 补全功能清单 P1 缺失「坐席辅助」
 * 坐席确认后发送，不自动回复用户
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class AgentAssistService {

    @Resource
    private ModelRouter modelRouter;

    private static final String RECOMMEND_PROMPT = """
            你是智能客服坐席助手。根据对话历史，为坐席推荐3条可能的回复。
            要求：
            1. 每条回复简洁专业，控制在50字以内
            2. 按推荐优先级排列
            3. 只输出回复内容，每条一行，不要编号和解释

            对话历史：
            %s

            用户最新消息：%s
            """;

    private static final String SUMMARY_PROMPT = """
            你是智能客服坐席助手。请对以下对话历史生成简洁摘要。
            要求：
            1. 突出核心问题和处理进展
            2. 控制在100字以内
            3. 只输出摘要内容，不要解释

            对话历史：
            %s
            """;

    private static final String NEXT_SENTENCE_PROMPT = """
            你是智能客服坐席助手。根据对话历史，建议坐席下一句最合适的回复。
            要求：
            1. 只输出一句话
            2. 语气专业亲切
            3. 控制在30字以内

            对话历史：
            %s

            用户最新消息：%s
            """;

    /**
     * 推荐回复：为坐席生成多条候选回复
     *
     * @param history  对话历史文本
     * @param userMsg  用户最新消息
     * @return 推荐回复列表（最多3条）
     */
    public List<String> recommendReplies(String history, String userMsg) {
        String prompt = String.format(RECOMMEND_PROMPT,
                history == null ? "（无历史）" : history,
                userMsg == null ? "" : userMsg);
        try {
            String response = modelRouter.chatForType(ModelTypeEnum.LLM.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
            return parseLineReplies(response, 3);
        } catch (ModelCallException e) {
            log.error("推荐回复生成失败", e);
            return List.of();
        }
    }

    /**
     * 会话摘要：对长对话生成简洁摘要
     *
     * @param history 对话历史文本
     * @return 摘要文本
     */
    public String summarizeSession(String history) {
        if (history == null || history.isBlank()) {
            return "";
        }
        String prompt = String.format(SUMMARY_PROMPT, history);
        try {
            return modelRouter.chatForType(ModelTypeEnum.LLM.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            log.error("会话摘要生成失败", e);
            return "";
        }
    }

    /**
     * 下一句建议：为坐席建议最合适的单句回复
     *
     * @param history 对话历史文本
     * @param userMsg 用户最新消息
     * @return 建议的下一句回复
     */
    public String suggestNextSentence(String history, String userMsg) {
        String prompt = String.format(NEXT_SENTENCE_PROMPT,
                history == null ? "（无历史）" : history,
                userMsg == null ? "" : userMsg);
        try {
            return modelRouter.chatForType(ModelTypeEnum.LLM.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            log.error("下一句建议生成失败", e);
            return "";
        }
    }

    private List<String> parseLineReplies(String response, int maxCount) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        return response.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .limit(maxCount)
                .toList();
    }
}