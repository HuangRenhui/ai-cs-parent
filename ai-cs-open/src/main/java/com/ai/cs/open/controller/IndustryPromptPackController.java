package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.IndustryPromptPack;
import com.ai.cs.open.service.IndustryPromptPackService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 行业提示词包管理控制器。
 */
@RestController
@RequestMapping("/open/prompt/pack")
public class IndustryPromptPackController {

    @Resource
    private IndustryPromptPackService promptPackService;

    /**
     * 查询全部行业提示词包。
     */
    @GetMapping("/list")
    public Result<List<IndustryPromptPack>> listPromptPacks() {
        return Result.success(promptPackService.listPromptPacks());
    }

    /**
     * 按行业包查询提示词。
     */
    @GetMapping("/listByPack")
    public Result<List<IndustryPromptPack>> listPromptPacksByPack(@RequestParam String packCode) {
        return Result.success(promptPackService.listPromptPacksByPack(packCode));
    }

    /**
     * 按行业包和类型查询提示词。
     */
    @GetMapping("/listByPackAndType")
    public Result<List<IndustryPromptPack>> listPromptPacksByPackAndType(@RequestParam String packCode,
                                                                           @RequestParam String promptType) {
        return Result.success(promptPackService.listPromptPacksByPackAndType(packCode, promptType));
    }

    /**
     * 新增或更新行业提示词包。
     */
    @PostMapping("/save")
    public Result<Void> savePromptPack(@RequestBody IndustryPromptPack promptPack) {
        promptPackService.savePromptPack(promptPack);
        return Result.success();
    }

    /**
     * 删除行业提示词包。
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> deletePromptPack(@PathVariable Long id) {
        promptPackService.deletePromptPack(id);
        return Result.success();
    }

    /**
     * 启用/停用行业提示词包。
     */
    @PutMapping("/enable/{id}")
    public Result<Void> setPromptPackEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        promptPackService.setPromptPackEnabled(id, enabled);
        return Result.success();
    }

    /**
     * 加载行业包的完整提示词。
     */
    @GetMapping("/load/{packCode}")
    public Result<String> loadPromptPack(@PathVariable String packCode) {
        return Result.success(promptPackService.loadPromptPack(packCode));
    }

    /**
     * 批量导入行业提示词包。
     */
    @PostMapping("/batchImport")
    public Result<Void> batchImportPromptPacks(@RequestParam String packCode,
                                               @RequestBody List<IndustryPromptPack> packs) {
        promptPackService.batchImportPromptPacks(packCode, packs);
        return Result.success();
    }
}
