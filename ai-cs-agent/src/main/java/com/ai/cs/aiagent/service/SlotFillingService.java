package com.ai.cs.aiagent.service;

import com.ai.cs.api.feign.BaseServiceFeign;
import com.ai.cs.base.entity.SlotFilling;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.SlotFillResultDTO;
import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 多轮填槽服务
 * 负责管理对话中的槽位填充状态和验证
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@Service
public class SlotFillingService {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private BaseServiceFeign baseServiceFeign;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis key前缀
     */
    private static final String SLOT_STATE_KEY_PREFIX = "slot:state:";

    /**
     * 槽位状态过期时间（30分钟）
     */
    private static final long SLOT_STATE_EXPIRE_MINUTES = 30;

    /**
     * 检查是否需要填槽
     *
     * @param dto 对话请求
     * @param intentCode 意图编码
     * @return 填槽结果
     */
    public SlotFillResultDTO checkAndFillSlots(ChatDTO dto, String intentCode) {
        String tenantCode = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode() : "default";
        
        try {
            // 获取该意图的槽位配置
            var result = baseServiceFeign.getSlotsByIntent(tenantCode, intentCode);
            List<SlotFilling> slots = (result != null && result.isOk()) ? result.getData() : null;
            if (slots == null || slots.isEmpty()) {
                // 没有配置槽位，直接返回成功
                return SlotFillResultDTO.complete();
            }
            
            // 获取当前会话的槽位状态（从Redis或内存）
            Map<String, String> currentSlots = getSessionSlots(dto.getSessionId());
            
            // 尝试从用户消息中提取槽位值
            Map<String, String> extractedSlots = extractSlotsFromMessage(dto.getMsg(), slots);
            
            // 合并槽位值
            for (Map.Entry<String, String> entry : extractedSlots.entrySet()) {
                currentSlots.put(entry.getKey(), entry.getValue());
            }
            
            // 检查是否所有必填槽位都已填充
            for (SlotFilling slot : slots) {
                if (slot.getRequired() == 1 && !StringUtils.hasText(currentSlots.get(slot.getSlotName()))) {
                    // 必填槽位缺失，返回追问
                    String prompt = generateSlotPrompt(slot);
                    saveSessionSlots(dto.getSessionId(), currentSlots);
                    return SlotFillResultDTO.incomplete(slot.getSlotName(), prompt);
                }
                
                // 验证已填充的槽位
                if (StringUtils.hasText(currentSlots.get(slot.getSlotName()))) {
                    boolean valid = validateSlot(slot, currentSlots.get(slot.getSlotName()));
                    if (!valid) {
                        String prompt = generateValidationPrompt(slot);
                        saveSessionSlots(dto.getSessionId(), currentSlots);
                        return SlotFillResultDTO.incomplete(slot.getSlotName(), prompt);
                    }
                }
            }
            
            // 所有槽位都已填充且验证通过
            saveSessionSlots(dto.getSessionId(), currentSlots);
            return SlotFillResultDTO.complete(currentSlots);
            
        } catch (Exception e) {
            log.error("填槽检查失败", e);
            // 出错时返回完成，避免阻塞流程
            return SlotFillResultDTO.complete();
        }
    }
    
    /**
     * 从用户消息中提取槽位值
     */
    private Map<String, String> extractSlotsFromMessage(String message, List<SlotFilling> slots) {
        Map<String, String> extracted = new HashMap<>();
        
        for (SlotFilling slot : slots) {
            if (StringUtils.hasText(slot.getExtractPrompt())) {
                try {
                    // 使用LLM提取槽位值
                    String prompt = String.format(
                        "从以下用户消息中提取%s的值。只返回提取的值，不要其他文字。\n用户消息：%s",
                        slot.getSlotName(),
                        message
                    );
                    String extractedValue = modelRouter.chat(List.of(
                        Map.of("role", "user", "content", prompt)
                    ));
                    
                    if (StringUtils.hasText(extractedValue)) {
                        extracted.put(slot.getSlotName(), extractedValue.trim());
                    }
                } catch (Exception e) {
                    log.warn("使用LLM提取槽位失败: {}", slot.getSlotName(), e);
                }
            }
            
            // 如果LLM提取失败，尝试简单的正则匹配
            if (!extracted.containsKey(slot.getSlotName())) {
                String value = extractByRegex(message, slot);
                if (StringUtils.hasText(value)) {
                    extracted.put(slot.getSlotName(), value);
                }
            }
        }
        
        return extracted;
    }
    
    /**
     * 使用正则表达式提取槽位值
     */
    private String extractByRegex(String message, SlotFilling slot) {
        if (StringUtils.hasText(slot.getValidationRegex())) {
            try {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(slot.getValidationRegex());
                java.util.regex.Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    return matcher.group();
                }
            } catch (Exception e) {
                log.warn("正则提取失败: {}", slot.getValidationRegex(), e);
            }
        }
        
        // 根据槽位类型使用默认正则
        return switch (slot.getSlotType()) {
            case "phone" -> extractPhone(message);
            case "number" -> extractNumber(message);
            case "order_id" -> extractOrderId(message);
            default -> null;
        };
    }
    
    private String extractPhone(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("1[3-9]\\d{9}");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }
    
    private String extractNumber(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }
    
    private String extractOrderId(String message) {
        // 订单号通常是6位以上的字母数字组合
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Za-z0-9]{6,}");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }
    
    /**
     * 验证槽位值
     */
    private boolean validateSlot(SlotFilling slot, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        
        if (StringUtils.hasText(slot.getValidationRegex())) {
            try {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(slot.getValidationRegex());
                return pattern.matcher(value).matches();
            } catch (Exception e) {
                log.warn("槽位验证正则错误: {}", slot.getValidationRegex(), e);
                return false;
            }
        }
        
        return switch (slot.getSlotType()) {
            case "phone" -> value.matches("^1[3-9]\\d{9}$");
            case "number" -> value.matches("^\\d+$");
            case "order_id" -> value.length() >= 6;
            default -> true;
        };
    }
    
    /**
     * 生成槽位追问话术
     */
    private String generateSlotPrompt(SlotFilling slot) {
        if (StringUtils.hasText(slot.getPromptTemplate())) {
            return slot.getPromptTemplate();
        }
        return "请提供" + slot.getSlotName();
    }
    
    /**
     * 生成验证失败提示
     */
    private String generateValidationPrompt(SlotFilling slot) {
        return String.format("您提供的%s格式不正确，请重新输入", slot.getSlotName());
    }
    
    /**
     * 获取会话的槽位状态
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getSessionSlots(String sessionId) {
        try {
            String key = SLOT_STATE_KEY_PREFIX + sessionId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof Map) {
                return (Map<String, String>) value;
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("从Redis获取槽位状态失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 保存会话的槽位状态
     */
    private void saveSessionSlots(String sessionId, Map<String, String> slots) {
        try {
            String key = SLOT_STATE_KEY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(key, slots, SLOT_STATE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存槽位状态到Redis失败: {}", e.getMessage());
        }
    }
    
    /**
     * 清除会话的槽位状态
     */
    public void clearSessionSlots(String sessionId) {
        try {
            String key = SLOT_STATE_KEY_PREFIX + sessionId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("清除槽位状态失败: {}", e.getMessage());
        }
    }
}
