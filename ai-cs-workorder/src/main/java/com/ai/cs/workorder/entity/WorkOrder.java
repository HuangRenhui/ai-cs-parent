package com.ai.cs.workorder.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:16
 * @description 工单实体
 */

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cs_work_order")
public class WorkOrder extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String sessionId;
    private Long customerId;
    private String orderType;
    private String orderContent;
    private Integer orderStatus;
    private Long agentId;
}