package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 工单 DTO
 *
 * @author huangrenhui
 * @date 2026/6/11 18:04
 */
@Data
public class WorkOrderDTO {
    private String orderType;
    private String content;
    private String sessionId;
    private Long customerId;
}