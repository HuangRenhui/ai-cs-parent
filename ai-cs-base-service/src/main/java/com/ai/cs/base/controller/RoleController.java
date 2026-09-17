package com.ai.cs.base.controller;

import com.ai.cs.base.entity.Role;
import com.ai.cs.base.service.RoleService;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 角色管理控制器
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/system/role")
public class RoleController {

    @Resource
    private RoleService roleService;

    /**
     * 启用角色列表（用于用户分配角色时的下拉选项）
     */
    @GetMapping("/list")
    public Result<List<Role>> list() {
        return Result.success(roleService.getAllEnabledRoles());
    }

    /**
     * 新增角色
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Role role) {
        roleService.save(role);
        return Result.success("创建成功");
    }

    /**
     * 更新角色
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Role role) {
        roleService.updateById(role);
        return Result.success("更新成功");
    }

    /**
     * 删除角色（逻辑删除）
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.success("删除成功");
    }
}
