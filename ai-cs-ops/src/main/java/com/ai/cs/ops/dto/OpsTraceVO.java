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
    /** 整条链路耗时（毫秒） */
    private Long durationMs;
    /** 片段数（叶子方法节点数，便于列表展示） */
    private int spanCount;
    /** 树根：按服务分组，子节点为类与方法 */
    private List<OpsTraceSpanVO> spans = new ArrayList<>();
    /** 纯文本调用树，方便复制到工单/群聊分享 */
    private String shareText;
}
