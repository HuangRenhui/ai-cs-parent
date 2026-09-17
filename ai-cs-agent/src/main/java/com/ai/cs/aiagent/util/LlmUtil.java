package com.ai.cs.aiagent.util;

import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.enums.IntentEnum;
import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 意图与闲聊（占位）
 *
 * <p>TODO 后续实现：意图识别把用户消息填入 {@code PromptConst.INTENT_PROMPT}，经 {@code ModelRouter}
 * 调用 INTENT 类型模型，再从输出中提取 JSON 主体（兼容 ```json 代码块与夹杂解释文字）解析为
 * {@link IntentDTO}，并用 {@code IntentEnum.fromName} 归一化非法意图名；闲聊回复走 {@code PromptConst.CHAT_PROMPT}。
 * 模型不可用时降级为「咨询」并置 {@code llmDegraded=true}。</p>
 *
 * <p>当前不调用模型：意图恒返回「咨询」降级结果（{@code llmDegraded=true}），闲聊恒返回系统繁忙文案。</p>
 */
@Slf4j
@Component
public class LlmUtil {

    @Resource
    private ModelRouter modelRouter;

    /**
     * 意图识别（占位：不调用模型，降级为「咨询」）
     *
     * @param userMsg 用户消息原文
     * @return 降级意图（llmDegraded=true）
     */
    public IntentDTO getIntent(String userMsg) {
        log.warn("[占位] 意图识别未实现，降级为「咨询」（llmDegraded=true）");
        return fallbackConsult();
    }

    /**
     * 闲聊回复（占位：不调用模型，返回系统繁忙兜底文案）
     *
     * @param userMsg 用户消息原文
     * @param history 对话历史文本
     * @return 兜底文案
     */
    public String chatReply(String userMsg, String history) {
        log.warn("[占位] 闲聊回复未实现，返回系统繁忙兜底文案");
        return PromptConst.LLM_BUSY_REPLY;
    }

    /**
     * 构造降级意图：统一回退为「咨询」，并打上 llmDegraded 标记供上层识别
     */
    private static IntentDTO fallbackConsult() {
        IntentDTO dto = new IntentDTO();
        dto.setIntent(IntentEnum.CONSULT.getName());
        dto.setEntity("");
        dto.setLlmDegraded(true);
        return dto;
    }
}
