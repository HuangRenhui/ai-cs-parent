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
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 昵称 */
    private String nickname;
    /** 1-男，2-女，空/0-未选 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer gender;
    /** 头像地址（系统默认头像或上传文件路径） */
    private String avatar;
    /** 客户标签（多个标签逗号分隔） */
    private String customerTag;
    /** 逻辑删除标记：0-正常 1-已删除 */
    @TableLogic
    private Integer delFlag;
}
