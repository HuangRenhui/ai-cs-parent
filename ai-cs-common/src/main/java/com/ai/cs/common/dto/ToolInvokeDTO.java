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
    /** 兼容字段：invoke 不采信；write/critical 走人工确认接口执行 */
    private Boolean confirmed;
    /** 防重复退款/冻卡；相同键返回首次成功结果 */
    private String idempotencyKey;
    /** 租户编码，用于连接器出站白名单校验 */
    private String tenantCode;
    /**
     * 发起方标识（可选）：坐席用户名 / 访客 ref / ai。
     * 未传时服务端按「JWT 用户名 → session:会话ID → ai」兜底，用于待确认列表展示与取消归属判断。
     */
    private String requestedBy;
}
