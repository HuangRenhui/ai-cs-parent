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
    private String username;
    private String password;

    @Data
    public static class Result {
        private String token;
        private String username;
        private String realName;
        private List<String> roles;
        private List<String> permissions;
    }
}
