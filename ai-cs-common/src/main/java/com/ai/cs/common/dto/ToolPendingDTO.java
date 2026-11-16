package com.ai.cs.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待确认调用视图。
 *
 * <p>只回白名单字段（工具名 / 实体 / 参数摘要 / 有效期），
 * 完整 {@code requestJson} 仅在详情接口返回，避免把整个 payload 丢给确认人。</p>
 */
@Data
public class ToolPendingDTO {
    /** 记录 ID，即确认接口的 confirmId */
    private Long id;
    /** 工具名 */
    private String toolName;
    /** 会话 ID */
    private String sessionId;
    /** 状态：见 ToolInvokeStatus */
    private String status;
    /** 发起时风险等级快照 */
    private String risk;
    /** 发起方标识 */
    private String requestedBy;
    /** 确认人（已处理时有值） */
    private String confirmedBy;
    /** 确认时间（已处理时有值） */
    private LocalDateTime confirmedAt;
    /** 拒绝理由（REJECTED 时有值） */
    private String rejectedReason;
    /** 失效时间，空表示不过期 */
    private LocalDateTime expireAt;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 业务实体 ID（订单号等） */
    private String entityId;
    /** 业务实体类型 */
    private String entityType;
    /** 工具入参 JSON 串（白名单字段之一） */
    private String payload;
    /** 完整请求参数快照，仅详情接口返回 */
    private String requestJson;
}
