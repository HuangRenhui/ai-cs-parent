package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 坐席辅助服务（占位）：推荐回复、会话摘要、下一句建议
 *
 * <p>TODO 后续实现：按各能力的提示词模板（推荐 3 条 50 字内／摘要 100 字内／下一句 30 字内）
 * 调用 {@code ModelRouter.chatForType(LLM)}，推荐回复按行切分并限制条数，模型异常时降级为空结果。</p>
 *
 * <p>当前不调用模型：推荐回复返回空列表，摘要与下一句返回空串，坐席辅助面板无内容。</p>
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class AgentAssistService {

    @Resource
    private ModelRouter modelRouter;

    /**
     * 推荐回复（占位：返回空列表）
     *
     * @param history  对话历史文本
     * @param userMsg  用户最新消息
     * @return 空列表
     */
    public List<String> recommendReplies(String history, String userMsg) {
        log.info("[占位] 坐席推荐回复未实现，返回空列表");
        return List.of();
    }

    /**
     * 会话摘要（占位：返回空串）
     *
     * @param history 对话历史文本
     * @return 空串
     */
    public String summarizeSession(String history) {
        log.info("[占位] 会话摘要未实现，返回空串");
        return "";
    }

    /**
     * 下一句建议（占位：返回空串）
     *
     * @param history 对话历史文本
     * @param userMsg 用户最新消息
     * @return 空串
     */
    public String suggestNextSentence(String history, String userMsg) {
        log.info("[占位] 下一句建议未实现，返回空串");
        return "";
    }
}
