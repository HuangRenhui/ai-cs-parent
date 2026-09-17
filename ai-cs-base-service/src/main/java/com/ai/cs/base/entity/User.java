package com.ai.cs.base.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
@TableName("cs_user")
public class User extends BaseEntity {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录用户名 */
    private String username;
    /** 登录密码（BCrypt密文） */
    private String password;
    /** 真实姓名 */
    private String realName;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 头像地址 */
    private String avatar;
    /** 性别：1-男 2-女 */
    private Integer gender;
    /** 状态：0-停用 1-启用 */
    private Integer status;
    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
    /** 最后登录IP */
    private String lastLoginIp;
    /** 逻辑删除标记：0-正常 1-已删除 */
    @TableLogic
    private Integer delFlag;
}
