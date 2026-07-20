package com.ai.cs.base.controller;

import com.ai.cs.base.entity.User;
import com.ai.cs.base.service.UserService;
import com.ai.cs.common.dto.LoginDTO;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.security.JwtContext;
import com.ai.cs.common.security.NoAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

/**
 * 认证与用户管理控制器
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private UserService userService;

    @NoAuth
    @PostMapping("/login")
    public Result<LoginDTO.Result> login(@RequestBody LoginDTO dto) {
        try {
            LoginDTO.Result result = userService.login(dto.getUsername(), dto.getPassword());
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.fail(401, e.getMessage());
        }
    }

    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userinfo() {
        Long userId = JwtContext.getCurrentUserId();
        User user = userService.getCurrentUserInfo(userId);
        if (user == null) {
            return Result.fail(401, "用户不存在");
        }
        Map<String, Object> info = new java.util.HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("email", user.getEmail());
        info.put("avatar", user.getAvatar());
        info.put("roles", userService.getUserRoles(userId));
        info.put("permissions", userService.getUserPermissions(userId));
        return Result.success(info);
    }

    @GetMapping("/menus")
    public Result<List<com.ai.cs.base.entity.Menu>> menus() {
        Long userId = JwtContext.getCurrentUserId();
        return Result.success(userService.getUserMenus(userId));
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        JwtContext.clear();
        return Result.success("退出成功");
    }
}
