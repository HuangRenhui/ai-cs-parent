package com.ai.cs.base.controller;

import com.ai.cs.base.entity.User;
import com.ai.cs.base.service.UserService;
import com.ai.cs.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 用户管理控制器
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/system/user")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/list")
    public Result<List<User>> list() {
        return Result.success(userService.list());
    }

    @GetMapping("/page")
    public Result<Page<User>> page(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(userService.page(new Page<>(pageNum, pageSize)));
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody User user) {
        userService.createUser(user);
        return Result.success("创建成功");
    }

    @PutMapping("/update")
    public Result<String> update(@RequestBody User user) {
        userService.updateUser(user);
        return Result.success("更新成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success("删除成功");
    }

    @GetMapping("/info/{id}")
    public Result<User> info(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }
}
