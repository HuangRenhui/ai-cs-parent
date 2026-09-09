package com.ai.cs.common.enums;

/**
 * 工单状态枚举：待处理 -> 处理中 -> 已完成 / 已关闭
 *
 * @author huangrenhui
 */
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

    /** 按 code 取中文标签，null 或未匹配返回「未知」 */
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

    /** 校验 code 是否为合法工单状态（状态流转校验用） */
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
