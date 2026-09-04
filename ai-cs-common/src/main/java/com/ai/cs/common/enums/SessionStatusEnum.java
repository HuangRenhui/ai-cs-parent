package com.ai.cs.common.enums;

public enum SessionStatusEnum {
    ONGOING(1, "进行中"),
    ENDED(2, "已结束");

    private final Integer code;
    private final String label;

    SessionStatusEnum(Integer code, String label) {
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
        for (SessionStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item.label;
            }
        }
        return "未知";
    }
}
