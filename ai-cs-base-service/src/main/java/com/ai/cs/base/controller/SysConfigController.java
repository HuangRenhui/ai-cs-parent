package com.ai.cs.base.controller;

import com.ai.cs.base.entity.SysConfig;
import com.ai.cs.base.service.SysConfigService;
import com.ai.cs.common.dto.SysConfigDTO;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 系统配置控制器
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/system/config")
public class SysConfigController {

    @Resource
    private SysConfigService configService;

    /**
     * 所有启用的配置列表
     */
    @GetMapping("/list")
    public Result<List<SysConfig>> list() {
        return Result.success(configService.getAllEnabled());
    }

    /**
     * 新增配置
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody SysConfig config) {
        configService.save(config);
        return Result.success("保存成功");
    }

    /**
     * 更新配置（同时清除对应缓存）
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody SysConfig config) {
        configService.updateConfig(config);
        return Result.success("更新成功");
    }

    /**
     * 按键获取配置值（走缓存）
     */
    @GetMapping("/get/{key}")
    public Result<String> getByKey(@PathVariable String key) {
        return Result.success(configService.getConfigValue(key));
    }
}
