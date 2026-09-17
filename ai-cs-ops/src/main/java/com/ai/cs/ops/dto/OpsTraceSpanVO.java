package com.ai.cs.ops.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 链路树节点：服务 → 类 → 方法。文件适配器按日志聚合，不是 OTel 精确 Span。
 */
@Data
public class OpsTraceSpanVO {
    /** 节点 ID */
    private String spanId;
    /** 父节点 ID，根节点为空 */
    private String parentSpanId;
    /** 节点类型：service / class / method */
    private String nodeType;
    /** 服务名 */
    private String service;
    /** 类名（全限定或短名） */
    private String className;
    /** 方法名 */
    private String methodName;
    /** 源码行号 */
    private Integer line;
    /** 展示名（步骤名或中文摘要） */
    private String name;
    /** 开始时间 */
    private String startTime;
    /** 结束时间 */
    private String endTime;
    /** 本节点耗时（毫秒） */
    private Long durationMs;
    /** 关联请求 ID */
    private String requestId;
    /** 本节点最高日志级别 */
    private String level;
    /** 摘要/日志内容 */
    private String message;
    /** 排障提示：慢调用、告警、失败时给出下一步该看什么 */
    private String analysis;
    /** 子节点（类下挂方法，服务下挂类） */
    private List<OpsTraceSpanVO> children = new ArrayList<>();
}
