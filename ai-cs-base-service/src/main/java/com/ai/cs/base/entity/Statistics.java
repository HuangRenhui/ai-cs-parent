package com.ai.cs.base.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据统计实体
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
@TableName("cs_statistics")
public class Statistics {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private String statType;
    private String statKey;
    private BigDecimal statValue;
    private Long statCount;
    private String remark;
    private LocalDateTime createTime;
}
