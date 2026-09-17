package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 系统配置DTO
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
public class SysConfigDTO {
    /** 主键ID */
    private Long id;
    /** 配置键 */
    private String configKey;
    /** 配置值 */
    private String configValue;
    /** 配置类型（text/json/number 等，决定前端渲染控件） */
    private String configType;
    /** 配置说明 */
    private String description;
    /** 状态 1-启用 0-停用 */
    private Integer status;
}
