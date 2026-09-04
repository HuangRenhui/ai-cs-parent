package com.ai.cs.common.enums;

public enum WorkOrderStatusEnum {
    PENDING(1, "待处理"),
    PROCESSING(2, "处理中"),
    DONE(3, "已完成"),
    CLOSED(4, "已关闭");

    private final Integer code;
    private final String label;

    WorkOrderStatusEnum(Integer code, String label) {
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
        if (code == null) {
            return "未知";
        }
        for (WorkOrderStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item.label;
            }
        }
        return "未知";
    }

    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        for (WorkOrderStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
