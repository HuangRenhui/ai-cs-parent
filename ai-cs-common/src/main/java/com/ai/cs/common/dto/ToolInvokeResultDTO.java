package com.ai.cs.common.dto;

import lombok.Data;

@Data
public class ToolInvokeResultDTO {
    private boolean success;
    private String toolName;
    private String output;
    private String source;
}
