package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.ConnectorUrlWhitelist;
import com.ai.cs.open.service.ConnectorUrlWhitelistService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 连接器出站 URL 白名单管理接口。
 */
@RestController
@RequestMapping("/open/connector/whitelist")
public class ConnectorUrlWhitelistController {

    @Resource
    private ConnectorUrlWhitelistService whitelistService;

    /**
     * 查询白名单，可按租户过滤。
     */
    @GetMapping("/list")
    public Result<List<ConnectorUrlWhitelist>> list(@RequestParam(required = false) String tenantCode) {
        return Result.success(whitelistService.listByTenant(tenantCode));
    }

    /**
     * 新增或更新白名单条目。
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody ConnectorUrlWhitelist entry) {
        whitelistService.saveEntry(entry);
        return Result.success();
    }

    /**
     * 删除白名单条目。
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        whitelistService.removeById(id);
        return Result.success();
    }
}
