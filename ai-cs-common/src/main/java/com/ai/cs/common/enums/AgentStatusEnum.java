package com.ai.cs.common.enums;

/**
 * 客服（人工坐席）状态枚举
 *
 * @author huangrenhui
 */
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

    /** 按 code 取中文标签，未匹配返回「未知」 */
    public static String labelOf(Integer code) {
        for (AgentStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item.label;
            }
        }
        return "未知";
    }
}
