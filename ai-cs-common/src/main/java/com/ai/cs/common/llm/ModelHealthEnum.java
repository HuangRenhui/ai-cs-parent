package com.ai.cs.common.llm;

/**
 * 模型健康状态枚举
 *
 * @author ai-cs
 */
public enum ModelHealthEnum {
    UNKNOWN("UNKNOWN", "未知"),
    HEALTHY("HEALTHY", "健康"),
    DOWN("DOWN", "故障");

    private final String code;
    private final String label;

    ModelHealthEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
