package com.ai.cs.base.controller;

import com.ai.cs.base.entity.User;
import com.ai.cs.base.service.UserService;
import com.ai.cs.common.dto.LoginDTO;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.security.JwtContext;
import com.ai.cs.common.security.NoAuth;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 用户登录：校验账密并签发 JWT（免鉴权接口）
     */
    @NoAuth
    @PostMapping("/login")
    public Result<LoginDTO.Result> login(@Valid @RequestBody LoginDTO dto) {
        try {
            LoginDTO.Result result = userService.login(dto.getUsername(), dto.getPassword());
            return Result.success(result);
        } catch (RuntimeException e) {
            // 登录失败统一返回 401，消息由业务异常给出（如"用户名或密码错误"）
            return Result.fail(401, e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息（基本信息 + 角色 + 权限）
     */
    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userinfo() {
        // 用户ID由网关解析 JWT 后写入上下文
        Long userId = JwtContext.getCurrentUserId();
        User user = userService.getCurrentUserInfo(userId);
        if (user == null) {
            return Result.fail(401, "用户不存在");
        }
        // 手动挑选返回字段，避免密码等敏感字段外泄
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

    /**
     * 获取当前用户的菜单列表（用于前端动态路由）
     */
    @GetMapping("/menus")
    public Result<List<com.ai.cs.base.entity.Menu>> menus() {
        Long userId = JwtContext.getCurrentUserId();
        return Result.success(userService.getUserMenus(userId));
    }

    /**
     * 退出登录：清理线程上下文（JWT 无状态，实际失效依赖前端丢弃 token）
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        JwtContext.clear();
        return Result.success("退出成功");
    }
}
