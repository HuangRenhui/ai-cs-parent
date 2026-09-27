package com.ai.cs.base.controller;

import com.ai.cs.base.dto.UserVO;
import com.ai.cs.base.entity.User;
import com.ai.cs.base.service.UserService;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 用户管理控制器
 *
 * <p>出参统一为 {@link UserVO}：不直接返回 {@link User} 实体，避免 {@code password} 密文外泄。</p>
 *
 * <p><b>权限校验</b>：每个方法都标注 {@link RequirePermission}，由切面在进入方法前校验当前令牌里的权限点
 * （超管持有通配权限 {@code *:*:*}）。权限点取值口径与 {@code cs_menu.perms} 一致，
 * 查操作对应 {@code system:user:list}，写操作对应 {@code system:user:save/update/delete}。</p>
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/system/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户列表（全量）
     */
    @RequirePermission("system:user:list")
    @GetMapping("/list")
    public Result<List<UserVO>> list() {
        return Result.success(userService.list().stream().map(UserVO::from).toList());
    }

    /**
     * 用户分页查询
     */
    @RequirePermission("system:user:list")
    @GetMapping("/page")
    public Result<IPage<UserVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        IPage<UserVO> page = userService.page(new Page<>(pageNum, pageSize)).convert(UserVO::from);
        return Result.success(page);
    }

    /**
     * 创建用户（密码在 service 层加密入库）
     */
    @RequirePermission("system:user:save")
    @PostMapping("/save")
    public Result<String> save(@RequestBody User user) {
        userService.createUser(user);
        return Result.success("创建成功");
    }

    /**
     * 更新用户（密码留空表示不修改）
     */
    @RequirePermission("system:user:update")
    @PutMapping("/update")
    public Result<String> update(@RequestBody User user) {
        userService.updateUser(user);
        return Result.success("更新成功");
    }

    /**
     * 删除用户（逻辑删除）
     */
    @RequirePermission("system:user:delete")
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success("删除成功");
    }

    /**
     * 用户详情
     */
    @RequirePermission("system:user:list")
    @GetMapping("/info/{id}")
    public Result<UserVO> info(@PathVariable Long id) {
        return Result.success(UserVO.from(userService.getById(id)));
    }
}
