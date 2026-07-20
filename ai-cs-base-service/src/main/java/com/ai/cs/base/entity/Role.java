package com.ai.cs.base.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 角色实体
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
@TableName("cs_role")
public class Role extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private Integer sortNum;
    private Integer status;
    @TableLogic
    private Integer delFlag;
}
