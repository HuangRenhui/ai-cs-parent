package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 开放工具调用请求：Agent 识别到工具类意图（查物流/退款等）后，
 * 由 agent 服务转发给 open-tool 服务执行。
 *
 * @author ai-cs
 */
@Data
public class ToolInvokeDTO {
    /** 工具名（注册表中的唯一标识） */
    private String toolName;
    /** 兼容当前电商演示意图：查物流、退款 */
    private String intentBind;
    /** 业务实体ID（如订单号） */
    private String entityId;
    /** 业务实体类型（如 order） */
    private String entityType;
    /** 会话ID（审计/日志关联用） */
    private String sessionId;
    /** 工具自定义入参（JSON 串） */
    private String payload;
    /** 高风险 write/critical 工具必须为 true 才执行 */
    private Boolean confirmed;
    /** 防重复退款/冻卡；相同键返回首次成功结果 */
    private String idempotencyKey;
}
