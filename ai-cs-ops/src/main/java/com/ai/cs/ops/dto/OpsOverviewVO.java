package com.ai.cs.ops.dto;

import lombok.Data;

@Data
public class OpsOverviewVO {
    private String adapter;
    private String hint;
    private int servicesTotal;
    private int servicesUp;
    private int servicesDown;
    private int logFiles;
    private int recentErrorCount;
    private int pendingAlerts;
}
