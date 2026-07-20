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
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private Integer gender;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    @TableLogic
    private Integer delFlag;
}
