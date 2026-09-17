package com.ai.cs.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知 DTO
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
public class NotificationDTO {

    /** 通知ID */
    private Long id;

    /** 通知类型：SYSTEM-系统通知, TICKET-工单通知, CHAT-会话通知, ALERT-告警通知 */
    private String type;

    /** 通知方式：IN_APP-站内信, EMAIL-邮件, SMS-短信 */
    private String channel;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 接收人用户ID */
    private Long receiverId;

    /** 发送人用户ID（系统通知为0） */
    private Long senderId;

    /** 是否已读 */
    private Boolean isRead;

    /** 相关业务ID（工单ID/会话ID等） */
    private String bizId;

    /** 发送状态：PENDING-待发送, SENT-已发送, FAILED-发送失败 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 发送时间 */
    private LocalDateTime sendTime;
}
