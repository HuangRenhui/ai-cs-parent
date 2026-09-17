package com.ai.cs.base.controller;

import com.ai.cs.base.entity.Menu;
import com.ai.cs.base.service.MenuService;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 菜单管理控制器
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/system/menu")
public class MenuController {

    @Resource
    private MenuService menuService;

    /**
     * 菜单树（层级结构，供前端渲染侧边栏）
     */
    @GetMapping("/tree")
    public Result<List<Menu>> tree() {
        return Result.success(menuService.getMenuTree());
    }

    /**
     * 菜单平铺列表（不过滤状态，管理页用）
     */
    @GetMapping("/list")
    public Result<List<Menu>> list() {
        return Result.success(menuService.list());
    }

    /**
     * 新增菜单
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Menu menu) {
        menuService.save(menu);
        return Result.success("创建成功");
    }

    /**
     * 更新菜单
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Menu menu) {
        menuService.updateById(menu);
        return Result.success("更新成功");
    }

    /**
     * 删除菜单（逻辑删除）
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        menuService.removeById(id);
        return Result.success("删除成功");
    }
}
