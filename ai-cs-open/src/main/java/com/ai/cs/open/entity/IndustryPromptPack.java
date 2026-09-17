package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行业提示词包实体（对应表 cs_industry_prompt_pack）。
 * 用于加载行业特定的人设、拒答、槽位等提示词，不写死电商。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_industry_prompt_pack")
public class IndustryPromptPack extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 行业包编码（ecommerce/finance/retail/custom） */
    private String packCode;
    /** 提示词类型（persona/rejection/slot/system） */
    private String promptType;
    /** 提示词内容 */
    private String promptContent;
    /** 适用场景（可选，如 order/refund/account） */
    private String scene;
    /** 优先级 */
    private Integer priority;
    /** 1 启用 0 停用 */
    private Integer enabled;
    /** 备注说明 */
    private String remark;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
