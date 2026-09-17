package com.ai.cs.base.controller;

import com.ai.cs.base.entity.DataRetention;
import com.ai.cs.base.service.DataRetentionService;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 数据保留策略控制器
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@RestController
@RequestMapping("/system/data-retention")
public class DataRetentionController {

    @Resource
    private DataRetentionService dataRetentionService;

    /**
     * 查询数据保留策略列表
     *
     * @param tenantCode 租户编码
     * @return 策略列表
     */
    @GetMapping("/list")
    public Result<List<DataRetention>> list(@RequestParam(required = false) String tenantCode) {
        try {
            List<DataRetention> list = dataRetentionService.listByTenant(tenantCode);
            return Result.success(list);
        } catch (Exception e) {
            log.error("查询数据保留策略列表失败", e);
            return Result.error("查询数据保留策略列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定数据类型的保留策略
     *
     * @param tenantCode 租户编码
     * @param dataType 数据类型
     * @return 数据保留策略
     */
    @GetMapping("/get")
    public Result<DataRetention> get(@RequestParam(required = false) String tenantCode,
                                     @RequestParam String dataType) {
        try {
            DataRetention retention = dataRetentionService.getDataRetention(tenantCode, dataType);
            return Result.success(retention);
        } catch (Exception e) {
            log.error("查询数据保留策略失败", e);
            return Result.error("查询数据保留策略失败: " + e.getMessage());
        }
    }

    /**
     * 保存或更新数据保留策略
     *
     * @param dataRetention 数据保留策略
     * @return 是否成功
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody DataRetention dataRetention) {
        try {
            boolean success = dataRetentionService.saveOrUpdateRetention(dataRetention);
            return Result.success(success);
        } catch (Exception e) {
            log.error("保存数据保留策略失败", e);
            return Result.error("保存数据保留策略失败: " + e.getMessage());
        }
    }

    /**
     * 删除数据保留策略
     *
     * @param id 策略ID
     * @return 是否成功
     */
    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        try {
            boolean success = dataRetentionService.removeById(id);
            return Result.success(success);
        } catch (Exception e) {
            log.error("删除数据保留策略失败", e);
            return Result.error("删除数据保留策略失败: " + e.getMessage());
        }
    }
}
