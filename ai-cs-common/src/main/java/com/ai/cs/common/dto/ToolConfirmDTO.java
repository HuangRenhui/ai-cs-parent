package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 坐席确认高危工具调用的请求。
 */
@Data
public class ToolConfirmDTO {
    /** 待确认记录 ID（invoke 返回 confirm-required 时给出） */
    private Long confirmId;
    /**
     * 幂等键（可选）。不传则由服务端按「租户 + 会话 + 工具 + 规范化参数」生成，
     * 保证同一笔业务重复确认不会二次退款/二次发券。
     */
    private String idempotencyKey;
}
