package com.ai.cs.aiagent.service;

import com.ai.cs.api.feign.BaseServiceFeign;
import com.ai.cs.base.entity.IntentConfig;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.enums.IntentEnum;
import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 可配置意图识别服务
 * 支持从数据库加载租户自定义意图，替代硬编码的意图枚举
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@Service
public class ConfigurableIntentService {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private BaseServiceFeign baseServiceFeign;

    /**
     * 获取租户的意图配置
     */
    private List<IntentConfig> getIntents(String tenantCode) {
        try {
            var result = baseServiceFeign.getEnabledIntents(tenantCode);
            if (result != null && result.isOk()) {
                return result.getData();
            }
            return null;
        } catch (Exception e) {
            log.error("获取意图配置失败，回退到硬编码意图", e);
            return null;
        }
    }

    /**
     * 构建意图识别提示词
     */
    private String buildIntentPrompt(String userMsg, List<IntentConfig> intents) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是智能客服的意图分类器。根据用户最新一句话判断意图并抽取实体。\n");
        sb.append("只输出一个 JSON 对象，不要输出 markdown、解释或其它文字。\n\n");
        sb.append("JSON 格式：\n");
        sb.append("{\"intent\":\"<意图>\",\"entity\":\"<实体>\"}\n\n");
        
        if (intents != null && !intents.isEmpty()) {
            sb.append("intent 只能是以下之一：");
            for (IntentConfig intent : intents) {
                sb.append(intent.getIntentName()).append("、");
            }
            sb.deleteCharAt(sb.length() - 1); // 移除最后的顿号
            sb.append("\n");
            
            for (IntentConfig intent : intents) {
                sb.append("- ").append(intent.getIntentName()).append("：");
                if (StringUtils.hasText(intent.getDescription())) {
                    sb.append(intent.getDescription());
                }
                sb.append("\n");
            }
        } else {
            // 回退到硬编码意图
            sb.append("intent 只能是以下之一：").append(IntentEnum.allowedValues()).append("\n");
            sb.append("- 咨询：政策、账户、使用方法等一般问题\n");
            sb.append("- 查物流：询问进度、发货、物流（演示包；entity 填对方系统单号）\n");
            sb.append("- 退款：要求退款/退货（演示包；entity 填对方系统单号）\n");
            sb.append("- 投诉：表达不满、催促处理，需要升级\n");
            sb.append("- 转人工：明确要求人工客服\n");
        }
        
        sb.append("\nentity：单号、手机号、商品名等关键信息，没有则填空字符串。不要编造。\n\n");
        sb.append("用户问题：\n").append(userMsg);
        
        return sb.toString();
    }

    /**
     * 意图识别
     *
     * @param userMsg 用户消息
     * @param tenantCode 租户编码
     * @return 意图DTO
     */
    public IntentDTO getIntent(String userMsg, String tenantCode) {
        List<IntentConfig> intents = getIntents(tenantCode);
        String prompt = buildIntentPrompt(userMsg, intents);
        
        try {
            String response = modelRouter.chat(List.of(
                Map.of("role", "user", "content", prompt)
            ));
            return parseIntent(response, intents);
        } catch (ModelCallException e) {
            log.error("意图识别调用失败", e);
            return fallbackConsult();
        }
    }

    /**
     * 解析意图响应
     */
    private IntentDTO parseIntent(String raw, List<IntentConfig> intents) {
        try {
            String jsonStr = extractJsonObject(raw);
            IntentDTO dto = JSON.parseObject(jsonStr, IntentDTO.class);
            
            if (dto == null) {
                return fallbackConsult();
            }
            
            if (!StringUtils.hasText(dto.getIntent())) {
                dto.setIntent("咨询");
            } else {
                // 归一化意图名称
                dto.setIntent(normalizeIntentName(dto.getIntent(), intents));
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
     * 归一化意图名称
     */
    private String normalizeIntentName(String raw, List<IntentConfig> intents) {
        if (intents != null && !intents.isEmpty()) {
            // 在配置的意图中查找
            for (IntentConfig intent : intents) {
                if (intent.getIntentName().equals(raw) || intent.getIntentCode().equalsIgnoreCase(raw)) {
                    return intent.getIntentName();
                }
            }
            // 关键词匹配
            for (IntentConfig intent : intents) {
                if (StringUtils.hasText(intent.getKeywords())) {
                    try {
                        List<String> keywords = JSON.parseArray(intent.getKeywords(), String.class);
                        for (String keyword : keywords) {
                            if (raw.toLowerCase().contains(keyword.toLowerCase())) {
                                return intent.getIntentName();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析关键词失败: {}", intent.getKeywords(), e);
                    }
                }
            }
        }
        
        // 回退到硬编码意图
        IntentEnum intentEnum = IntentEnum.fromName(raw);
        return intentEnum.getName();
    }

    /**
     * 提取JSON对象
     */
    private String extractJsonObject(String raw) {
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

    /**
     * 降级意图
     */
    private IntentDTO fallbackConsult() {
        IntentDTO dto = new IntentDTO();
        dto.setIntent("咨询");
        dto.setEntity("");
        dto.setLlmDegraded(true);
        return dto;
    }
}
