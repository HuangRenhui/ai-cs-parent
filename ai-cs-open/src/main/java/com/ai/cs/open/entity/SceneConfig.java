package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话入口场景配置（对应表 cs_scene_config）。
 * 访客从订单/产品/售后等入口打开聊窗时，按场景下发开场白与快捷动作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_scene_config")
public class SceneConfig extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景编码: ORDER/PRODUCT/AFTER_SALE/GENERAL 等 */
    private String scene;

    /** 场景名称 */
    private String sceneName;

    /** 开场白模板，支持 {entityId} 占位符 */
    private String greeting;

    /** 无实体时的开场白 */
    private String greetingEmpty;

    /** 快捷动作 JSON 数组: [{label, send}] */
    private String quickActions;

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 排序号，越小越靠前 */
    private Integer sortNum;
}
