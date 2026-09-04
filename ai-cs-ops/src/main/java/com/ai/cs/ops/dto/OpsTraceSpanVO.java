package com.ai.cs.ops.dto;

import lombok.Data;

@Data
public class OpsTraceSpanVO {
    private String spanId;
    private String service;
    private String name;
    private String startTime;
    private String endTime;
    private String requestId;
}
