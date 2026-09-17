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
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 父菜单ID（0 表示顶级菜单） */
    private Long parentId;
    /** 菜单名称 */
    private String menuName;
    /** 菜单类型：0-目录 1-菜单 2-按钮 */
    private Integer menuType;
    /** 前端路由路径 */
    private String path;
    /** 前端组件路径 */
    private String component;
    /** 权限标识（如 system:user:list） */
    private String perms;
    /** 菜单图标 */
    private String icon;
    /** 排序号（越小越靠前） */
    private Integer sortNum;
    /** 是否可见：0-隐藏 1-显示 */
    private Integer visible;
    /** 状态：0-停用 1-启用 */
    private Integer status;
    /** 逻辑删除标记：0-正常 1-已删除 */
    @TableLogic
    private Integer delFlag;
    /** 子菜单（非数据库字段） */
    @TableField(exist = false)
    private List<Menu> children;
}
