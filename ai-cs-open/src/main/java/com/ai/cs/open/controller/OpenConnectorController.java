package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.OpenConnector;
import com.ai.cs.open.service.OpenPlatformService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 开放工具连接器管理控制器：连接器（MOCK/REST）的增删改查。
 */
@RestController
@RequestMapping("/open/connector")
public class OpenConnectorController {

    @Resource
    private OpenPlatformService openPlatformService;

    /**
     * 查询全部连接器（按 ID 倒序）。
     */
    @GetMapping("/list")
    public Result<List<OpenConnector>> list() {
        return Result.success(openPlatformService.listConnectors());
    }

    /**
     * 新增连接器；强制清空 ID，防止调用方伪装成更新。
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody OpenConnector connector) {
        connector.setId(null);
        openPlatformService.saveConnector(connector);
        return Result.success();
    }

    /**
     * 更新连接器（按 ID 全量更新）。
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody OpenConnector connector) {
        openPlatformService.saveConnector(connector);
        return Result.success();
    }

    /**
     * 删除连接器（逻辑删除）。
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        openPlatformService.deleteConnector(id);
        return Result.success();
    }
}
