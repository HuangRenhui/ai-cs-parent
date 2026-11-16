package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 坐席拒绝高危工具调用的请求。
 */
@Data
public class ToolRejectDTO {
    /** 待确认记录 ID */
    private Long confirmId;
    /** 拒绝理由（会写入审计，并回给用户） */
    private String reason;
}
