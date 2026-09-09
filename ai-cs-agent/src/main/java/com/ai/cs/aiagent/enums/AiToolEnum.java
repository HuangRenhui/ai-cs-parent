package com.ai.cs.aiagent.enums;

/**
 * 工具枚举（FunctionCall）
 *
 * @author huangrenhui
 * @date 2026/6/11 18:05
 */
public enum AiToolEnum {
    QUERY_ORDER("queryOrder", "查询订单"),
    QUERY_LOGISTICS("queryLogistics", "查询物流"),
    CREATE_ORDER("createWorkOrder", "创建工单"),
    QUERY_FAQ("queryFaq", "查询FAQ"),
    TRANSFER_AGENT("transferAgent", "转人工"),
    QUERY_KNOWLEDGE("queryKnowledge", "查询知识库");

    /** 工具编码（对外暴露的工具名） */
    private final String code;
    /** 工具中文描述 */
    private final String desc;

    AiToolEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return desc;
    }

    /** 工具名与编码一致，供按名查找/展示使用 */
    public String getName() {
        return code;
    }

    /**
     * 根据code查找枚举
     */
    public static AiToolEnum fromCode(String code) {
        for (AiToolEnum tool : values()) {
            if (tool.getCode().equals(code)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 根据名称查找枚举
     */
    public static AiToolEnum fromName(String name) {
        for (AiToolEnum tool : values()) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }
}
