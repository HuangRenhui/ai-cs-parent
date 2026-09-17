package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 出站 Webhook 实体（对应表 cs_outbound_webhook）。
 * 用于向外部系统推送事件，如会话开始/转人工/结束/评价等，
 * 供对方 CRM 或业务系统订阅。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_outbound_webhook")
public class OutboundWebhook extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** Webhook 名称 */
    private String name;
    /** 回调 URL（对方系统的接收地址） */
    private String callbackUrl;
    /** 事件类型（session_start/transfer_agent/session_end/rating） */
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
