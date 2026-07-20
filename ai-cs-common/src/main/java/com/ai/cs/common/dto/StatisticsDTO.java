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
    private String startDate;
    private String endDate;
    private String statType;
    private String groupBy;
    private Integer limit;
}
