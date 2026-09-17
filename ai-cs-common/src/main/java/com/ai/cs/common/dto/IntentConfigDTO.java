package com.ai.cs.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 可配置意图跨服务传输对象。
 * 放在 common 供 Feign 使用，避免 api 模块依赖 base-service 实体导致循环编译。
 */
@Data
public class IntentConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;
    /** 租户编码 */
    private String tenantCode;
    /** 意图编码 */
    private String intentCode;
    /** 意图名称（识别结果归一化用） */
    private String intentName;
    /** 意图类型: business/system/transfer */
    private String intentType;
    /** 意图描述，写入意图识别提示词 */
    private String description;
    /** 关键词 JSON 数组字符串 */
    private String keywords;
    /** 示例话术 JSON 数组字符串 */
    private String examples;
    /** 绑定的开放工具名称 */
    private String toolBind;
    /** 回复模板 */
    private String responseTemplate;
    /** 优先级，数值越小越优先 */
    private Integer priority;
    /** 是否启用: 0-禁用, 1-启用 */
    private Integer enabled;
}
