package com.ai.cs.ops.dto;

import lombok.Data;

@Data
public class OpsAlertRuleVO {
    private String code;
    private String name;
    private String description;
    private Integer enabled;
    private String channel;
}
