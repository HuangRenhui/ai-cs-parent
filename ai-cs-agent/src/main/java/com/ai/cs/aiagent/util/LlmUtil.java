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

    /**
     * 意图识别：把用户消息填入意图提示词，调用大模型并解析出结构化意图
     *
     * @param userMsg 用户消息原文
     * @return 解析后的意图 DTO；调用失败或解析失败时返回降级的"咨询"意图（llmDegraded=true）
     */
    public IntentDTO getIntent(String userMsg) {
        // 空消息按空串处理，避免提示词里出现 null
        String prompt = PromptConst.fill(PromptConst.INTENT_PROMPT, userMsg == null ? "" : userMsg);
        try {
            return parseIntent(modelRouter.chat(singleUser(prompt)));
        } catch (ModelCallException e) {
            // 模型不可用时降级为咨询意图，保证主流程不中断
            log.error("意图识别调用失败", e);
            return fallbackConsult();
        }
    }

    /**
     * 闲聊回复：结合历史上下文调用大模型生成回复
     *
     * @param userMsg 用户消息原文
     * @param history 对话历史文本
     * @return 模型回复；调用失败时返回"系统繁忙"兜底文案
     */
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

    /** 组装单轮 user 消息（大模型 chat 接口的标准入参格式） */
    private static List<Map<String, String>> singleUser(String prompt) {
        return List.of(Map.of("role", "user", "content", prompt));
    }

    /**
     * 解析模型输出的意图 JSON，并对结果做归一化校验
     *
     * @param raw 模型原始输出文本
     * @return 合法意图 DTO；任何解析异常都回退为"咨询"降级意图
     */
    private IntentDTO parseIntent(String raw) {
        try {
            // 模型输出可能带 markdown 代码块或多余文本，先提取出 JSON 主体
            String jsonStr = extractJsonObject(raw);
            IntentDTO dto = JSON.parseObject(jsonStr, IntentDTO.class);
            if (dto == null) {
                return fallbackConsult();
            }
            if (!StringUtils.hasText(dto.getIntent())) {
                // 模型没给出意图时默认按咨询处理
                dto.setIntent(IntentEnum.CONSULT.getName());
            } else {
                // fromName 会把未知名称归一为咨询，防止模型输出非法意图名
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

    /**
     * 从模型输出中提取 JSON 对象主体：兼容 ```json 代码块包裹与夹杂解释文字的情况
     */
    private static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        // 先剥掉 markdown 代码块围栏，只取首尾大括号之间的内容
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

    /**
     * 构造降级意图：统一回退为"咨询"，并打上 llmDegraded 标记供上层识别
     */
    private static IntentDTO fallbackConsult() {
        IntentDTO dto = new IntentDTO();
        dto.setIntent(IntentEnum.CONSULT.getName());
        dto.setEntity("");
        dto.setLlmDegraded(true);
        return dto;
    }
}
