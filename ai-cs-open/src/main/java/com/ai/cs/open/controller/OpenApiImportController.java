package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.OpenApiImport;
import com.ai.cs.open.service.OpenApiImportService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * OpenAPI 导入管理控制器。
 */
@RestController
@RequestMapping("/open/openapi")
public class OpenApiImportController {

    @Resource
    private OpenApiImportService openApiImportService;

    /**
     * 查询全部导入记录。
     */
    @GetMapping("/list")
    public Result<List<OpenApiImport>> listImports() {
        return Result.success(openApiImportService.listImports());
    }

    /**
     * 按租户查询导入记录。
     */
    @GetMapping("/listByTenant")
    public Result<List<OpenApiImport>> listImportsByTenant(@RequestParam String tenantCode) {
        return Result.success(openApiImportService.listImportsByTenant(tenantCode));
    }

    /**
     * 创建导入任务。
     */
    @PostMapping("/create")
    public Result<OpenApiImport> createImport(@RequestBody OpenApiImport importRecord) {
        return Result.success(openApiImportService.createImport(importRecord));
    }

    /**
     * 执行导入任务。
     */
    @PostMapping("/execute/{id}")
    public Result<Void> executeImport(@PathVariable Long id) {
        openApiImportService.executeImport(id);
        return Result.success();
    }

    /**
     * 删除导入记录。
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteImport(@PathVariable Long id) {
        openApiImportService.deleteImport(id);
        return Result.success();
    }
}
