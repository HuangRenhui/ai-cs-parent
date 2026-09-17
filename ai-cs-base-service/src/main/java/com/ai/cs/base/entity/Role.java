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
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色名称 */
    private String roleName;
    /** 角色编码（唯一标识，如 admin） */
    private String roleCode;
    /** 角色描述 */
    private String description;
    /** 排序号（越小越靠前） */
    private Integer sortNum;
    /** 状态：0-停用 1-启用 */
    private Integer status;
    /** 逻辑删除标记：0-正常 1-已删除 */
    @TableLogic
    private Integer delFlag;
}
