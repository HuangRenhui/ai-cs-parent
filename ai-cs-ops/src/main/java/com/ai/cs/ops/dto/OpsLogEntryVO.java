package com.ai.cs.ops.dto;

import lombok.Data;

/**
 * 单条日志视图对象（message 已经过脱敏处理）。
 */
@Data
public class OpsLogEntryVO {
    /** 日志时间 */
    private String timestamp;
    /** 日志级别（INFO/WARN/ERROR 等） */
    private String level;
    /** 服务名（取自日志字段，缺失时按文件名推断） */
    private String service;
    /** Logger 名称（通常为全限定类名） */
    private String logger;
    /** 类名（调用栈或 logger 短名，排障定位用） */
    private String className;
    /** 方法名 */
    private String methodName;
    /** 源码行号，缺失则为空 */
    private Integer line;
    /** 线程名 */
    private String thread;
    /** 本步耗时（毫秒）；日志未带时由相邻时间戳补齐 */
    private Long durationMs;
    /** 日志内容（已脱敏） */
    private String message;
    /** 请求 ID */
    private String requestId;
    /** 链路 ID */
    private String traceId;
    /** 会话 ID */
    private String sessionId;
    /** 租户 ID */
    private String tenantId;
    /** 来源日志文件名 */
    private String file;
}
