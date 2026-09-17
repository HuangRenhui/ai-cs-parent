package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 连接器 URL 白名单实体（对应表 cs_connector_url_whitelist）。
 * 用于租户级别的出站 URL 白名单，增强 SSRF 防护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_connector_url_whitelist")
public class ConnectorUrlWhitelist extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户编码 */
    private String tenantCode;
    /** 白名单域名或 IP（支持通配符 *.example.com） */
    private String domainPattern;
    /** 备注说明 */
    private String remark;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
