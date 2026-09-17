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

    /** 全部意图名拼接（顿号分隔），用于拼进意图识别提示词 */
    public static String allowedValues() {
        return Arrays.stream(values()).map(IntentEnum::getName).collect(Collectors.joining("、"));
    }

    /**
     * 把模型输出的意图文本解析为枚举。
     * 解析策略：精确匹配（中文名/英文枚举名）→ 关键词兜底 → 默认「咨询」，
     * 保证模型输出不规范时链路也不中断。
     */
    public static IntentEnum fromName(String raw) {
        // 模型没输出意图时按咨询处理
        if (raw == null || raw.isBlank()) {
            return CONSULT;
        }
        String text = raw.trim();
        // 第一轮：精确匹配中文名或英文枚举名（忽略大小写）
        for (IntentEnum item : values()) {
            if (item.name.equals(text) || item.name().equalsIgnoreCase(text)) {
                return item;
            }
        }
        // 第二轮：关键词兜底，兼容模型输出了带解释的文本（如"用户想查物流"）
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
        // 都不命中时按咨询走知识库问答，是最安全的默认路由
        return CONSULT;
    }
}