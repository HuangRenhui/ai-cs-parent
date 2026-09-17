package com.ai.cs.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工单 DTO
 *
 * @author huangrenhui
 * @date 2026/6/11 18:04
 */
@Data
public class WorkOrderDTO {
    /** 工单类型：咨询、投诉、建议、退款 */
    @NotBlank(message = "工单类型不能为空")
    private String orderType;
    /** 工单内容（用户问题描述） */
    @NotBlank(message = "工单内容不能为空")
    private String content;
    /** 关联会话ID */
    private String sessionId;
    /** 客户ID */
    private Long customerId;
}