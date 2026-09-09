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

/**
 * 行业包管理控制器：行业包（电商/金融等）的查询、启停与热切换。
 */
@RestController
@RequestMapping("/open/pack")
public class OpenPackController {

    @Resource
    private OpenPlatformService openPlatformService;

    /**
     * 查询全部行业包（按排序号升序）。
     */
    @GetMapping("/list")
    public Result<List<OpenPack>> list() {
        return Result.success(openPlatformService.listPacks());
    }

    /**
     * 启用/停用指定行业包；停用后包下工具立即不可调用。
     *
     * @param code    行业包编码
     * @param enabled 1 启用 0 停用
     */
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
