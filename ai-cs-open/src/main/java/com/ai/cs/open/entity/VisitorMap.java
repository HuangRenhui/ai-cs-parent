package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 访客映射实体（对应表 cs_visitor_map）。
 * 记录外部访客标识（visitorRef）在「租户+渠道」维度下与内部客户的映射，
 * Widget 初始化时据此复用既有访客身份，避免重复建档。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_visitor_map")
public class VisitorMap extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户编码，缺省为 default */
    private String tenantCode;
    /** 接入渠道（web/h5/app 等），缺省为 web */
    private String channel;
    /** 外部访客唯一标识（由接入方生成，如同一浏览器指纹/会员号） */
    private String visitorRef;
    /** 映射到的内部客户 ID，未识别时为空 */
    private Long customerId;
    /** 最近一次进入的场景编码（ORDER/PRODUCT/GENERAL 等） */
    private String scene;
    /** 最近一次携带的业务实体 JSON（订单号、产品号等） */
    private String entitiesJson;
}
