package com.ai.cs.ops.dto;

import lombok.Data;

@Data
public class OpsLogQuery {
    private String requestId;
    private String traceId;
    private String sessionId;
    private String tenantId;
    private String service;
    private String level;
    private String keyword;
    private String from;
    private String to;
    private Integer page = 1;
    private Integer size = 50;
}
