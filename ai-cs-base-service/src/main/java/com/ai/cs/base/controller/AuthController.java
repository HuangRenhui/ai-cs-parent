package com.ai.cs.base.controller;

import com.ai.cs.base.entity.User;
import com.ai.cs.base.service.AgentService;
import com.ai.cs.base.service.UserService;
import com.ai.cs.common.dto.LoginDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.security.JwtContext;
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
    @Resource
    private AgentService agentService;

    /**
     * 管理员登录：校验后台账密并签发 JWT（免鉴权）。平台账号请走 /auth/login/platform。
     *
     * <p>放行白名单在鉴权过滤器里配置，不依赖注解。</p>
     */
    @PostMapping("/login")
    public Result<LoginDTO.Result> login(@Valid @RequestBody LoginDTO dto) {
        if (dto != null && "platform".equalsIgnoreCase(dto.getLoginType())) {
            return Result.fail(400, "平台账号请从「普通用户登录」入口进入");
        }
        // 只把业务异常（账密错误/账号锁定）转 401；DB/Redis 等系统故障交由全局异常处理，
        // 避免「数据库不可用」被误报成「账号密码错误」而难以排障
        try {
            LoginDTO.Result result = userService.login(dto.getUsername(), dto.getPassword());
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.fail(401, e.getMessage());
        }
    }

    /**
     * 平台登录：按租户编码向对接平台/坐席账号做身份校验，与管理员入口隔离。
     *
     * <p>放行白名单在鉴权过滤器里配置，不依赖注解。</p>
     */
    @PostMapping("/login/platform")
    public Result<LoginDTO.Result> platformLogin(@Valid @RequestBody LoginDTO dto) {
        try {
            return Result.success(agentService.loginFromPlatform(dto));
        } catch (BusinessException e) {
            return Result.fail(401, e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息（基本信息 + 角色 + 权限）
     *
     * <p>注意与鉴权链路的差别：<b>接口鉴权用的是令牌里的 roles/perms</b>（签发时快照，防篡改），
     * 而这里展示的是<b>实时回查数据库</b>的结果——所以「改了角色/权限」后：接口权限要重新登录才变，
     * 但页面上显示的角色/权限立刻就是最新的。两者不一致时以令牌为准（更严格）。</p>
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
