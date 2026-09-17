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
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 统计日期 */
    private LocalDate statDate;
    /** 统计类型（如 chat_session / work_order） */
    private String statType;
    /** 统计维度键（如状态、类型取值） */
    private String statKey;
    /** 统计数值 */
    private BigDecimal statValue;
    /** 统计条数 */
    private Long statCount;
    /** 备注 */
    private String remark;
    /** 记录创建时间 */
    private LocalDateTime createTime;
}
