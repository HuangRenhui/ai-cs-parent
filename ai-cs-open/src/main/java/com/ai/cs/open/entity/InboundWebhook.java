package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 入站 Webhook 实体（对应表 cs_inbound_webhook）。
 * 用于接收外部系统推送的事件，如物流变更、退款结果、工单进展等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_inbound_webhook")
public class InboundWebhook extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** Webhook 名称 */
    private String name;
    /** Webhook 路径（相对路径，如 /webhook/logistics） */
    private String path;
    /** 事件类型（logistics_update/refund_result/workorder_progress） */
    private String eventType;
    /** 鉴权方式（none/signature/token） */
    private String authType;
    /** 鉴权配置 JSON（签名密钥、Token 等） */
    private String authConfig;
    /** 所属租户编码 */
    private String tenantCode;
    /** 1 启用 0 停用 */
    private Integer enabled;
    /** 备注说明 */
    private String remark;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
