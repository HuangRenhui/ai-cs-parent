package com.ai.cs.base.controller;

import com.ai.cs.base.entity.IntentConfig;
import com.ai.cs.base.service.IntentConfigService;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 可配置意图控制器
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@RestController
@RequestMapping("/system/intent")
public class IntentConfigController {

    @Resource
    private IntentConfigService intentConfigService;

    /**
     * 查询租户启用的意图列表
     *
     * @param tenantCode 租户编码
     * @return 意图列表
     */
    @GetMapping("/list")
    public Result<List<IntentConfig>> list(@RequestParam(required = false) String tenantCode) {
        try {
            List<IntentConfig> intents = intentConfigService.getEnabledIntents(tenantCode);
            return Result.success(intents);
        } catch (Exception e) {
            log.error("查询意图列表失败", e);
            return Result.error("查询意图列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询租户下全部意图（含禁用，管理页用）
     *
     * @param tenantCode 租户编码
     * @return 意图列表
     */
    @GetMapping("/listAll")
    public Result<List<IntentConfig>> listAll(@RequestParam(required = false) String tenantCode) {
        try {
            List<IntentConfig> intents = intentConfigService.listByTenant(tenantCode);
            return Result.success(intents);
        } catch (Exception e) {
            log.error("查询意图列表失败", e);
            return Result.error("查询意图列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询意图
     *
     * @param id 意图ID
     * @return 意图配置
     */
    @GetMapping("/{id}")
    public Result<IntentConfig> getById(@PathVariable Long id) {
        try {
            IntentConfig intent = intentConfigService.getById(id);
            return Result.success(intent);
        } catch (Exception e) {
            log.error("查询意图失败", e);
            return Result.error("查询意图失败: " + e.getMessage());
        }
    }

    /**
     * 保存或更新意图
     *
     * @param intentConfig 意图配置
     * @return 是否成功
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody IntentConfig intentConfig) {
        try {
            boolean success = intentConfigService.saveOrUpdateIntent(intentConfig);
            return Result.success(success);
        } catch (Exception e) {
            log.error("保存意图失败", e);
            return Result.error("保存意图失败: " + e.getMessage());
        }
    }

    /**
     * 删除意图
     *
     * @param id 意图ID
     * @return 是否成功
     */
    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        try {
            boolean success = intentConfigService.deleteIntent(id);
            return Result.success(success);
        } catch (Exception e) {
            log.error("删除意图失败", e);
            return Result.error("删除意图失败: " + e.getMessage());
        }
    }
}
