package com.ai.cs.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 可配置意图实体
 * 支持租户自定义意图，替代硬编码的意图枚举
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Data
@TableName("cs_intent_config")
public class IntentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 意图编码
     */
    private String intentCode;

    /**
     * 意图名称
     */
    private String intentName;

    /**
     * 意图类型: business/system/transfer
     */
    private String intentType;

    /**
     * 意图描述
     */
    private String description;

    /**
     * 关键词JSON数组
     */
    private String keywords;

    /**
     * 示例话术JSON数组
     */
    private String examples;

    /**
     * 绑定的工具名称
     */
    private String toolBind;

    /**
     * 回复模板
     */
    private String responseTemplate;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    /**
     * 删除标记: 0-未删除, 1-已删除
     */
    private Integer delFlag;
}
