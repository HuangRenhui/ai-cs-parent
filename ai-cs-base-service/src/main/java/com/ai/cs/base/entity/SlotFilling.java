package com.ai.cs.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 多轮填槽配置实体
 * 支持意图的多轮对话槽位填充
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Data
@TableName("cs_slot_filling")
public class SlotFilling implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 关联意图编码
     */
    private String intentCode;

    /**
     * 槽位名称
     */
    private String slotName;

    /**
     * 槽位类型: string/number/date/phone/order_id
     */
    private String slotType;

    /**
     * 是否必填: 0-否, 1-是
     */
    private Integer required;

    /**
     * 追问话术模板
     */
    private String promptTemplate;

    /**
     * 验证正则表达式
     */
    private String validationRegex;

    /**
     * 提取提示词
     */
    private String extractPrompt;

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
