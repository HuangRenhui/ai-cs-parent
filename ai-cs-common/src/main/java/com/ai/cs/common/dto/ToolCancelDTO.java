package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 取消待确认调用的请求（发起方改口 / 会话结束）。
 */
@Data
public class ToolCancelDTO {
    /** 待确认记录 ID */
    private Long confirmId;
    /** 取消原因（可选） */
    private String reason;
}
