package com.ai.cs.aiagent.util;

import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.enums.IntentEnum;
import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 意图与闲聊：提示词走 {@link PromptConst}，模型统一走 {@link ModelRouter}
 * （未登记对话模型时回退历史配置；登记后支持本地/在线切换与故障转移）。
 */
@Slf4j
@Component
public class LlmUtil {

    @Resource
    private ModelRouter modelRouter;

    public IntentDTO getIntent(String userMsg) {
        String prompt = PromptConst.fill(PromptConst.INTENT_PROMPT, userMsg == null ? "" : userMsg);
        try {
            return parseIntent(modelRouter.chat(singleUser(prompt)));
        } catch (ModelCallException e) {
            log.error("意图识别调用失败", e);
            return fallbackConsult();
        }
    }

    public String chatReply(String userMsg, String history) {
        String prompt = PromptConst.fill(PromptConst.CHAT_PROMPT,
                history == null ? "" : history,
                userMsg == null ? "" : userMsg);
        try {
            return modelRouter.chat(singleUser(prompt));
        } catch (ModelCallException e) {
            log.error("闲聊调用失败", e);
            return PromptConst.LLM_BUSY_REPLY;
        }
    }

    private static List<Map<String, String>> singleUser(String prompt) {
        return List.of(Map.of("role", "user", "content", prompt));
    }

    private IntentDTO parseIntent(String raw) {
        try {
            String jsonStr = extractJsonObject(raw);
            IntentDTO dto = JSON.parseObject(jsonStr, IntentDTO.class);
            if (dto == null) {
                return fallbackConsult();
            }
            if (!StringUtils.hasText(dto.getIntent())) {
                dto.setIntent(IntentEnum.CONSULT.getName());
            } else {
                dto.setIntent(IntentEnum.fromName(dto.getIntent()).getName());
            }
            if (dto.getEntity() == null) {
                dto.setEntity("");
            }
            return dto;
        } catch (Exception e) {
            log.warn("意图 JSON 解析失败，回退咨询: {}", raw);
            return fallbackConsult();
        }
    }

    private static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int startFence = text.indexOf('{');
            int endFence = text.lastIndexOf('}');
            if (startFence >= 0 && endFence > startFence) {
                return text.substring(startFence, endFence + 1);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static IntentDTO fallbackConsult() {
        IntentDTO dto = new IntentDTO();
        dto.setIntent(IntentEnum.CONSULT.getName());
        dto.setEntity("");
        dto.setLlmDegraded(true);
        return dto;
    }
}
