package com.ai.cs.common.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 客服意图枚举
 * 定义智能客服系统支持的客户意图类型，用于意图识别与路由分发
 *
 * @author huangrenhui
 * @date 2026/6/11 17:44
 */
public enum IntentEnum {
    CONSULT("咨询"),
    QUERY_LOGISTICS("查物流"),
    REFUND("退款"),
    COMPLAINT("投诉"),
    TO_AGENT("转人工");

    private final String name;
    IntentEnum(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    public static String allowedValues() {
        return Arrays.stream(values()).map(IntentEnum::getName).collect(Collectors.joining("、"));
    }

    public static IntentEnum fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return CONSULT;
        }
        String text = raw.trim();
        for (IntentEnum item : values()) {
            if (item.name.equals(text) || item.name().equalsIgnoreCase(text)) {
                return item;
            }
        }
        if (text.contains("物流") || text.contains("快递") || text.contains("发货")) {
            return QUERY_LOGISTICS;
        }
        if (text.contains("退款") || text.contains("退货")) {
            return REFUND;
        }
        if (text.contains("投诉") || text.contains("差评") || text.contains("不满")) {
            return COMPLAINT;
        }
        if (text.contains("转人工") || text.contains("人工客服") || text.contains("转接")) {
            return TO_AGENT;
        }
        return CONSULT;
    }
}