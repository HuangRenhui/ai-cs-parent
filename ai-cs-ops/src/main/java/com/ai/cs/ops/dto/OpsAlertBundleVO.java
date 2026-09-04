package com.ai.cs.ops.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpsAlertBundleVO {
    private List<OpsAlertRuleVO> rules = new ArrayList<>();
    private List<OpsAlertEventVO> events = new ArrayList<>();
    private String hint;
}
