package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.ConnectorFieldMapping;
import com.ai.cs.open.service.ConnectorFieldMappingService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 连接器字段映射管理控制器。
 */
@RestController
@RequestMapping("/open/connector/mapping")
public class ConnectorFieldMappingController {

    @Resource
    private ConnectorFieldMappingService fieldMappingService;

    /**
     * 查询指定连接器的全部字段映射。
     */
    @GetMapping("/list/{connectorId}")
    public Result<List<ConnectorFieldMapping>> listMappings(@PathVariable Long connectorId) {
        return Result.success(fieldMappingService.listMappingsByConnector(connectorId));
    }

    /**
     * 新增或更新字段映射。
     */
    @PostMapping("/save")
    public Result<Void> saveMapping(@RequestBody ConnectorFieldMapping mapping) {
        fieldMappingService.saveMapping(mapping);
        return Result.success();
    }

    /**
     * 删除字段映射。
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteMapping(@PathVariable Long id) {
        fieldMappingService.deleteMapping(id);
        return Result.success();
    }

    /**
     * 批量保存字段映射。
     */
    @PostMapping("/batchSave")
    public Result<Void> batchSaveMappings(@RequestParam Long connectorId, 
                                          @RequestBody List<ConnectorFieldMapping> mappings) {
        fieldMappingService.batchSaveMappings(connectorId, mappings);
        return Result.success();
    }

    /**
     * 测试字段映射（对方系统格式 -> 内核格式）。
     */
    @PostMapping("/testApply")
    public Result<Map<String, Object>> testApplyMapping(@RequestParam Long connectorId,
                                                        @RequestBody Map<String, Object> sourceData) {
        return Result.success(fieldMappingService.applyMapping(connectorId, sourceData));
    }

    /**
     * 测试字段映射（内核格式 -> 对方系统格式）。
     */
    @PostMapping("/testReverse")
    public Result<Map<String, Object>> testReverseMapping(@RequestParam Long connectorId,
                                                           @RequestBody Map<String, Object> targetData) {
        return Result.success(fieldMappingService.reverseMapping(connectorId, targetData));
    }
}
