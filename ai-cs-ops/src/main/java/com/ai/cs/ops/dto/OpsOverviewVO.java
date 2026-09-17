package com.ai.cs.ops.dto;

import lombok.Data;

/**
 * 运营总览视图对象：聚合健康探测、日志错误数与待处理告警，供运营大盘一屏展示。
 */
@Data
public class OpsOverviewVO {
    /** 当前日志/链路数据源适配器标识（如 file） */
    private String adapter;
    /** 页面提示文案（说明当前数据口径与局限） */
    private String hint;
    /** 被探测服务总数 */
    private int servicesTotal;
    /** 可达（UP）服务数 */
    private int servicesUp;
    /** 不可达（DOWN）服务数 */
    private int servicesDown;
    /** 扫描到的日志文件数 */
    private int logFiles;
    /** 近期 WARN/ERROR 级别日志条数 */
    private int recentErrorCount;
    /** 当前触发中（FIRING）的告警事件数 */
    private int pendingAlerts;
}
