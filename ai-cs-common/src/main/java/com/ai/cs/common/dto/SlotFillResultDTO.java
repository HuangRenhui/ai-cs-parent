package com.ai.cs.common.dto;

import lombok.Data;

import java.util.Map;

/**
 * 槽位填充结果DTO
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Data
public class SlotFillResultDTO {
    
    /**
     * 是否完成
     */
    private boolean complete;
    
    /**
     * 缺失的槽位名称
     */
    private String missingSlot;
    
    /**
     * 追问话术
     */
    private String prompt;
    
    /**
     * 已填充的槽位值
     */
    private Map<String, String> slots;
    
    /**
     * 创建完成结果
     */
    public static SlotFillResultDTO complete() {
        SlotFillResultDTO result = new SlotFillResultDTO();
        result.setComplete(true);
        return result;
    }
    
    /**
     * 创建完成结果（带槽位值）
     */
    public static SlotFillResultDTO complete(Map<String, String> slots) {
        SlotFillResultDTO result = new SlotFillResultDTO();
        result.setComplete(true);
        result.setSlots(slots);
        return result;
    }
    
    /**
     * 创建未完成结果
     */
    public static SlotFillResultDTO incomplete(String missingSlot, String prompt) {
        SlotFillResultDTO result = new SlotFillResultDTO();
        result.setComplete(false);
        result.setMissingSlot(missingSlot);
        result.setPrompt(prompt);
        return result;
    }
}
