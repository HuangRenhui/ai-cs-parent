package com.ai.cs.common.dto;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:04
 * @description 工单 DTO
 */
import lombok.Data;

@Data
public class WorkOrderDTO {
    private String orderType;
    private String content;
    private String sessionId;
    private Long customerId;
}