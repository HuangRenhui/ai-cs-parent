package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流量与 IP 分析：把操作审计聚成请求量、来源地址和模块分布。
 */
@Data
public class AccessAnalysisDTO {
    /** 时间窗口内总请求数 */
    private long total;
    /** 成功次数 */
    private long success;
    /** 失败次数 */
    private long fail;
    /** 独立来源 IP 数 */
    private long uniqueIps;
    /** 平均耗时（毫秒） */
    private long avgDurationMs;
    /** 按小时的流量趋势 */
    private List<TrendPoint> traffic = new ArrayList<>();
    /** 来源 IP 排行 */
    private List<IpStat> topIps = new ArrayList<>();
    /** 模块分布 */
    private List<ModuleStat> modules = new ArrayList<>();
    /** 失败偏多、需要关注的 IP */
    private List<IpStat> riskIps = new ArrayList<>();

    /** 某一小时的请求量 */
    @Data
    public static class TrendPoint {
        /** 时间桶，如 2026-09-11 20:00 */
        private String bucket;
        /** 总次数 */
        private long count;
        /** 失败次数 */
        private long fail;
    }

    /** 单个 IP 的访问统计 */
    @Data
    public static class IpStat {
        /** 来源地址 */
        private String ip;
        /** 总次数 */
        private long count;
        /** 失败次数 */
        private long fail;
        /** 最近一次出现时间 */
        private String lastTime;
    }

    /** 单个模块的访问统计 */
    @Data
    public static class ModuleStat {
        /** 模块编码 */
        private String module;
        /** 总次数 */
        private long count;
        /** 失败次数 */
        private long fail;
    }
}
