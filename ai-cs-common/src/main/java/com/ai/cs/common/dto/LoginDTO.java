package com.ai.cs.common.dto;

import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = "用户名不能为空")
    private String username;
    /** 登录密码（明文传输，依赖 HTTPS；服务端比对哈希） */
    @NotBlank(message = "密码不能为空")
    private String password;
    /**
     * 登录入口：admin 管理员后台；platform 对接平台校验。
     * 两套入口不得混用，由对应接口分别处理。
     */
    private String loginType;
    /** 对接平台租户编码，仅平台登录需要 */
    private String tenantCode;
    /** 平台单点登录票据（有值时走 SSO 换票，演示环境可空） */
    private String ssoTicket;

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
        /** 登录入口：admin / platform，前端据此切菜单 */
        private String loginType;
        /** 平台登录时回显的租户编码 */
        private String tenantCode;
    }
}
