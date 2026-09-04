package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.OpenPack;
import com.ai.cs.open.service.OpenPlatformService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/open/pack")
public class OpenPackController {

    @Resource
    private OpenPlatformService openPlatformService;

    @GetMapping("/list")
    public Result<List<OpenPack>> list() {
        return Result.success(openPlatformService.listPacks());
    }

    @PutMapping("/{code}/enabled")
    public Result<Void> enabled(@PathVariable String code, @RequestParam Integer enabled) {
        openPlatformService.setPackEnabled(code, enabled);
        return Result.success();
    }

    /** 只启用这一个行业包，其余关闭（热切换） */
    @PutMapping("/{code}/activate")
    public Result<Void> activate(@PathVariable String code) {
        openPlatformService.activatePackExclusive(code);
        return Result.success();
    }
}
