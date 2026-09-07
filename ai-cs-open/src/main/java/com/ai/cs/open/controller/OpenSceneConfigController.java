package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.SceneConfig;
import com.ai.cs.open.service.OpenPlatformService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 会话入口场景配置管理：点订单/产品/售后进客服时的开场白与快捷动作。
 */
@RestController
@RequestMapping("/open/scene-config")
public class OpenSceneConfigController {

    @Resource
    private OpenPlatformService openPlatformService;

    @GetMapping("/list")
    public Result<List<SceneConfig>> list() {
        return Result.success(openPlatformService.listScenes());
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody SceneConfig config) {
        config.setId(null);
        openPlatformService.saveScene(config);
        return Result.success();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody SceneConfig config) {
        openPlatformService.saveScene(config);
        return Result.success();
    }

    @PutMapping("/{id}/enabled")
    public Result<Void> enabled(@PathVariable Long id, @RequestParam Integer enabled) {
        openPlatformService.setSceneEnabled(id, enabled);
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        openPlatformService.deleteScene(id);
        return Result.success();
    }
}
