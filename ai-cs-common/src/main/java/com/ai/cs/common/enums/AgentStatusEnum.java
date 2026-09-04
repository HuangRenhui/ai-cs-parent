package com.ai.cs.common.enums;

public enum AgentStatusEnum {
    OFFLINE(0, "离线"),
    ONLINE(1, "在线"),
    BUSY(2, "忙碌");

    private final Integer code;
    private final String label;

    AgentStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static String labelOf(Integer code) {
        for (AgentStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item.label;
            }
        }
        return "未知";
    }
}
