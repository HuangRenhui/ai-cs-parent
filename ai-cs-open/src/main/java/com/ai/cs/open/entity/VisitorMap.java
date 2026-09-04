package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_visitor_map")
public class VisitorMap extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantCode;
    private String channel;
    private String visitorRef;
    private Long customerId;
    private String scene;
    private String entitiesJson;
}
