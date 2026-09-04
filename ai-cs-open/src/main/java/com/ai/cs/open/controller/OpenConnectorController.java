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

@RestController
@RequestMapping("/open/connector")
public class OpenConnectorController {

    @Resource
    private OpenPlatformService openPlatformService;

    @GetMapping("/list")
    public Result<List<OpenConnector>> list() {
        return Result.success(openPlatformService.listConnectors());
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody OpenConnector connector) {
        connector.setId(null);
        openPlatformService.saveConnector(connector);
        return Result.success();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody OpenConnector connector) {
        openPlatformService.saveConnector(connector);
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        openPlatformService.deleteConnector(id);
        return Result.success();
    }
}
