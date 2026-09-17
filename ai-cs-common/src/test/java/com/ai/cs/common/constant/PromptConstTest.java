package com.ai.cs.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptConst.fill 安全填充测试：提示词模板用 {@code %s} 占位，
 * 重点验证用户输入带百分号时不会触发 String.format 类异常或错位替换。
 */
@DisplayName("PromptConst 安全填充")
class PromptConstTest {

    @Test
    @DisplayName("用户文本含百分号不会抛异常")
    void fillIgnoresUserPercent() {
        String filled = PromptConst.fill(PromptConst.INTENT_PROMPT, "折扣 50%s 怎么算");
        assertTrue(filled.contains("折扣 50%s 怎么算"));
        assertFalse(filled.contains("用户问题：\n            %s"));
    }

    @Test
    @DisplayName("按顺序替换多个占位符")
    void fillMultiple() {
        String filled = PromptConst.fill(PromptConst.CHAT_PROMPT, "历史A", "问题B");
        assertTrue(filled.contains("历史A"));
        assertTrue(filled.contains("问题B"));
    }

    @Test
    @DisplayName("行业提示词叠加到通用模板前面")
    void overlayIndustryPrepends() {
        String overlay = PromptConst.overlayIndustry("你是金融客服", "通用规则");
        assertTrue(overlay.startsWith("【行业人设与拒答】"));
        assertTrue(overlay.contains("你是金融客服"));
        assertTrue(overlay.contains("通用规则"));
        assertEquals("通用规则", PromptConst.overlayIndustry(null, "通用规则"));
        assertEquals("通用规则", PromptConst.overlayIndustry("  ", "通用规则"));
    }
}
