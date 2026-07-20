package com.ai.cs.common.enums;

/**
 * 消息类型枚举
 *
 * @author huangrenhui
 * @date 2026/6/11 17:43
 */
public enum MsgTypeEnum {
    USER(1, "用户消息"),
    AI(2, "AI消息"),
    AGENT(3, "人工消息");

    private final Integer code;
    private final String desc;
    MsgTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public Integer getCode() {
        return code;
    }
}
