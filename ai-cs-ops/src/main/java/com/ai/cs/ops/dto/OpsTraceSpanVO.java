package com.ai.cs.ops.dto;

import lombok.Data;

/**
 * 链路片段（Span）视图对象：文件适配器下按服务聚合日志得到，并非真正的埋点 Span。
 */
@Data
public class OpsTraceSpanVO {
    /** 片段 ID（文件适配器下即服务名） */
    private String spanId;
    /** 服务名 */
    private String service;
    /** 片段名称（固定 logs，表示由日志聚合而来） */
    private String name;
    /** 该服务首条日志时间 */
    private String startTime;
    /** 该服务末条日志时间 */
    private String endTime;
    /** 关联请求 ID */
    private String requestId;
}
