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

    @GetMapping("/tree")
    public Result<List<Menu>> tree() {
        return Result.success(menuService.getMenuTree());
    }

    @GetMapping("/list")
    public Result<List<Menu>> list() {
        return Result.success(menuService.list());
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody Menu menu) {
        menuService.save(menu);
        return Result.success("创建成功");
    }

    @PutMapping("/update")
    public Result<String> update(@RequestBody Menu menu) {
        menuService.updateById(menu);
        return Result.success("更新成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        menuService.removeById(id);
        return Result.success("删除成功");
    }
}
