package com.ai.cs.ops.dto;

import lombok.Data;

@Data
public class OpsAlertEventVO {
    private String ruleCode;
    private String title;
    private String status;
    private String message;
    private String time;
}
