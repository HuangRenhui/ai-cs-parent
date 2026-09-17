package com.ai.cs.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 多轮填槽配置跨服务传输对象。
 * 放在 common 供 Feign 使用，避免 api 模块依赖 base-service 实体。
 */
@Data
public class SlotFillingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;
    /** 租户编码 */
    private String tenantCode;
    /** 关联意图编码 */
    private String intentCode;
    /** 槽位名称 */
    private String slotName;
    /** 槽位类型: string/number/date/phone/order_id */
    private String slotType;
    /** 是否必填: 0-否, 1-是 */
    private Integer required;
    /** 追问话术模板 */
    private String promptTemplate;
    /** 验证正则表达式 */
    private String validationRegex;
    /** 从用户话术中抽取槽位的提示词 */
    private String extractPrompt;
    /** 优先级 */
    private Integer priority;
    /** 是否启用: 0-禁用, 1-启用 */
    private Integer enabled;
}
