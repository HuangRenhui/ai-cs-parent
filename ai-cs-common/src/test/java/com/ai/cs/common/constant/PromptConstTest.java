package com.ai.cs.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}
