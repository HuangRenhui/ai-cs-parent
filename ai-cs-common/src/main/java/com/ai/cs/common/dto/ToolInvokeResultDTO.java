package com.ai.cs.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放工具调用结果
 *
 * @author ai-cs
 */
@Data
public class ToolInvokeResultDTO {
    /** 是否执行成功 */
    private boolean success;
    /** 工具名（回显，便于调用方核对） */
    private String toolName;
    /** 工具输出（成功为结果文本，失败为错误说明） */
    private String output;
    /** 结果来源（real/mock/cache/idempotent/confirm-required/...） */
    private String source;
    /** 待确认记录 ID：source=confirm-required 时有值，前端据此渲染确认入口 */
    private Long confirmId;
    /** 当前状态：confirm-required 时为 CONFIRMING */
    private String status;
    /** 确认失效时间：空表示不过期 */
    private LocalDateTime expireAt;
}
