package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 统计查询DTO
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
public class StatisticsDTO {
    /** 统计开始日期（yyyy-MM-dd） */
    private String startDate;
    /** 统计结束日期（yyyy-MM-dd） */
    private String endDate;
    /** 统计类型（会话量/工单量/响应时长等） */
    private String statType;
    /** 分组维度（按天/按客服/按渠道等） */
    private String groupBy;
    /** 返回条数上限（排行榜类统计用） */
    private Integer limit;
}
