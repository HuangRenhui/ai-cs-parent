package com.ai.cs.base.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 系统配置实体
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
@TableName("cs_sys_config")
public class SysConfig extends BaseEntity {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 配置键（唯一） */
    private String configKey;
    /** 配置值 */
    private String configValue;
    /** 配置类型（如 system/business） */
    private String configType;
    /** 配置说明 */
    private String description;
    /** 状态：0-停用 1-启用 */
    private Integer status;
}
