package com.ai.cs.ops.dto;

import lombok.Data;

/**
 * 日志查询条件：多条件组合过滤 + 分页参数。
 */
@Data
public class OpsLogQuery {
    /** 按请求 ID 过滤（模糊包含） */
    private String requestId;
    /** 按链路 ID 过滤（模糊包含） */
    private String traceId;
    /** 按会话 ID 过滤（模糊包含） */
    private String sessionId;
    /** 按租户 ID 过滤（模糊包含） */
    private String tenantId;
    /** 按服务名过滤（模糊包含） */
    private String service;
    /** 按日志级别精确过滤（不区分大小写） */
    private String level;
    /** 关键词（匹配日志内容或 Logger 名） */
    private String keyword;
    /** 起始时间（字符串比较，含边界） */
    private String from;
    /** 结束时间（字符串比较，含边界） */
    private String to;
    /** 页码（从 1 开始） */
    private Integer page = 1;
    /** 每页条数（上限 200） */
    private Integer size = 50;
}
