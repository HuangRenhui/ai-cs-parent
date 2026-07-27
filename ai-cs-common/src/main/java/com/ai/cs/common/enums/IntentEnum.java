package com.ai.cs.common.enums;

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
}