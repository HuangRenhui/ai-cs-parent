package com.ai.cs.base.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:10
 * @description 客户实体
 */

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("cs_customer")
public class Customer extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String email;
    private String nickname;
    /** 1-男，2-女，空/0-未选 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer gender;
    private String avatar;
    private String customerTag;
    @TableLogic
    private Integer delFlag;
}
