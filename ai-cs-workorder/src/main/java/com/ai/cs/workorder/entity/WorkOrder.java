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
    /** 工单主键ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工单编号（WO_ 前缀的 UUID，对外展示的业务唯一标识） */
    private String orderNo;
    /** 关联的客服会话ID（由会话转工单时带入，可为空） */
    private String sessionId;
    /** 客户ID，0 表示未关联客户 */
    private Long customerId;
    /** 工单类型（退款/投诉/咨询/建议/物流等） */
    private String orderType;
    /** 工单内容（问题描述） */
    private String orderContent;
    /** 工单状态：0-已取消 1-待处理 2-处理中 3-已完成 4-已关闭 */
    private Integer orderStatus;
    /** 处理坐席ID，0 表示未分配 */
    private Long agentId;
}