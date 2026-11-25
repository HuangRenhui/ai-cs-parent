package com.ai.cs.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话内的高危工具确认卡：与前端 ToolConfirmCard.vue 的 props 一一对应。
 */
@Data
public class ToolConfirmCardDTO {
    /** 待确认记录 ID，确认/拒绝/取消都用它 */
    private Long confirmId;
    /** 工具名 */
    private String toolName;
    /** read / write / critical，决定卡片配色 */
    private String risk;
    /** CONFIRMING / CONFIRMED / SUCCEEDED / FAILED / REJECTED / EXPIRED / CANCELLED */
    private String status;
    /** 空表示不过期 */
    private LocalDateTime expireAt;
    /** 白名单参数摘要；禁止把完整 payload 丢给确认人 */
    private String summary;
    /** 发起方：ai / visitor:xxx / 坐席用户名 */
    private String requestedBy;
}
