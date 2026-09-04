package com.ai.cs.common.dto;

import lombok.Data;

@Data
public class SessionDTO {
    private String sessionId;
    private Long customerId;
    private Long agentId;
    private Integer sessionType;
    private Integer senderType;
    private String msgContent;
}
