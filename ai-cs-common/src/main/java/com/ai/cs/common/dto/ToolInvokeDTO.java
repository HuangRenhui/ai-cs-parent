package com.ai.cs.common.dto;

import lombok.Data;

@Data
public class ToolInvokeDTO {
    private String toolName;
    /** 兼容当前电商演示意图：查物流、退款 */
    private String intentBind;
    private String entityId;
    private String entityType;
    private String sessionId;
    private String payload;
    /** 高风险 write/critical 工具必须为 true 才执行 */
    private Boolean confirmed;
    /** 防重复退款/冻卡；相同键返回首次成功结果 */
    private String idempotencyKey;
}
