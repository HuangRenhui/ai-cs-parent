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
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configValue;
    private String configType;
    private String description;
    private Integer status;
}
