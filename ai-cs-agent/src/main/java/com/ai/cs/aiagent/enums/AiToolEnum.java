package com.ai.cs.aiagent.enums;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:05
 * @description 工具枚举（FunctionCall）
 */
public enum AiToolEnum {
    QUERY_ORDER("queryOrder", "查询订单"),
    QUERY_LOGISTICS("queryLogistics", "查询物流"),
    CREATE_WORK_ORDER("createWorkOrder", "创建工单");

    private final String code;
    private final String desc;
    AiToolEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public String getCode() {
        return code;
    }
}