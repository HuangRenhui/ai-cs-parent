package com.ai.cs.ops.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpsTraceVO {
    private String traceId;
    private String sessionId;
    private String startTime;
    private String endTime;
    private int spanCount;
    private List<OpsTraceSpanVO> spans = new ArrayList<>();
}
