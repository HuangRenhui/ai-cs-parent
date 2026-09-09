package com.ai.cs.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录请求/响应DTO
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
public class LoginDTO {
    /** 登录账号 */
    private String username;
    /** 登录密码（明文传输，依赖 HTTPS；服务端比对哈希） */
    private String password;

    /** 登录成功响应 */
    @Data
    public static class Result {
        /** JWT 访问令牌 */
        private String token;
        /** 登录账号 */
        private String username;
        /** 真实姓名（前端展示用） */
        private String realName;
        /** 角色编码列表 */
        private List<String> roles;
        /** 权限标识列表（前端按钮级鉴权用） */
        private List<String> permissions;
    }
}
