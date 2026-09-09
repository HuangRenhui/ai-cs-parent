package com.ai.cs.ops.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 链路视图对象：同一 traceId 下各服务日志聚合出的调用链（排障线索，非精确埋点链路）。
 */
@Data
public class OpsTraceVO {
    /** 链路 ID */
    private String traceId;
    /** 关联会话 ID */
    private String sessionId;
    /** 链路开始时间（首条日志时间） */
    private String startTime;
    /** 链路结束时间（末条日志时间） */
    private String endTime;
    /** 片段数（即涉及的服务数） */
    private int spanCount;
    /** 片段列表（按服务聚合） */
    private List<OpsTraceSpanVO> spans = new ArrayList<>();
}
