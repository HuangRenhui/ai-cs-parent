package com.ai.cs.ops.dto;

import lombok.Data;

@Data
public class OpsLogEntryVO {
    private String timestamp;
    private String level;
    private String service;
    private String logger;
    private String message;
    private String requestId;
    private String traceId;
    private String sessionId;
    private String tenantId;
    private String file;
}
