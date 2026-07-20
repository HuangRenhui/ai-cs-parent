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
    private Long id;
    private String configKey;
    private String configValue;
    private String configType;
    private String description;
    private Integer status;
}
