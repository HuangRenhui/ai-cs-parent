package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 卡片模板实体（对应表 cs_card_template）。
 * 定义通用卡片协议：实体卡/按钮/表单，由行业包渲染。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_card_template")
public class CardTemplate extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 模板编码 */
    private String templateCode;
    /** 模板名称 */
    private String templateName;
    /** 卡片类型（entity/button/form） */
    private String cardType;
    /** 卡片内容 JSON（标题、字段、按钮等） */
    private String contentJson;
    /** 所属行业包 */
    private String packCode;
    /** 所属租户编码 */
    private String tenantCode;
    /** 1 启用 0 停用 */
    private Integer enabled;
    /** 备注说明 */
    private String remark;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
