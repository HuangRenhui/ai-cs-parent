package com.ai.cs.base.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.List;

/**
 * 菜单权限实体
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
@TableName("cs_menu")
public class Menu extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String path;
    private String component;
    private String perms;
    private String icon;
    private Integer sortNum;
    private Integer visible;
    private Integer status;
    @TableLogic
    private Integer delFlag;
    /** 子菜单（非数据库字段） */
    @TableField(exist = false)
    private List<Menu> children;
}
