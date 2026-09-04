package com.ai.cs.ops.dto;

import lombok.Data;

@Data
public class OpsHealthItemVO {
    private String name;
    private String url;
    private String status;
    private Integer httpStatus;
    private Long latencyMs;
    private String message;
}
