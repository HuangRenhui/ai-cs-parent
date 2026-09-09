package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 会话/消息 DTO：会话管理与消息收发共用
 *
 * @author huangrenhui
 */
@Data
public class SessionDTO {
    /** 会话ID */
    private String sessionId;
    /** 客户ID */
    private Long customerId;
    /** 客服(坐席)ID */
    private Long agentId;
    /** 会话类型（如 1-AI会话 2-人工会话） */
    private Integer sessionType;
    /** 发送方类型（对应 MsgTypeEnum：1-用户 2-AI 3-人工） */
    private Integer senderType;
    /** 消息内容 */
    private String msgContent;
}
