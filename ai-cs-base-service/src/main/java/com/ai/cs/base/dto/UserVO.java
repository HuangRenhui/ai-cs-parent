package com.ai.cs.base.dto;

import com.ai.cs.base.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户出参视图对象
 *
 * <p>对外接口一律返回本 VO，不直接回传 {@link User} 实体——实体含 {@code password}（BCrypt 密文）
 * 与内部审计字段，直出会造成敏感字段泄露。</p>
 */
@Data
public class UserVO {

    /** 主键ID */
    private Long id;
    /** 登录用户名 */
    private String username;
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
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 实体转 VO；入参为 null 时返回 null（便于详情接口直接转换）
     */
    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
