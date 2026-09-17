package com.ai.cs.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 数据保留策略实体
 * 配置数据出域与保留策略
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Data
@TableName("cs_data_retention")
public class DataRetention implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 数据类型: session/message/knowledge/tool_invoke
     */
    private String dataType;

    /**
     * 保留天数
     */
    private Integer retentionDays;

    /**
     * 是否允许出域: 0-否, 1-是
     */
    private Integer allowExternalDomain;

    /**
     * 是否允许用户删除: 0-否, 1-是
     */
    private Integer allowUserDelete;

    /**
     * 匿名化天数
     */
    private Integer anonymizeAfterDays;

    /**
     * 描述
     */
    private String description;

    /**
     * 状态: 0-禁用, 1-启用
     */
    private Integer status;
}
