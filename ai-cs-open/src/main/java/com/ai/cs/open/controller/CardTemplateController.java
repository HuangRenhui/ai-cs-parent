package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.CardTemplate;
import com.ai.cs.open.service.CardTemplateService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 卡片模板管理控制器。
 */
@RestController
@RequestMapping("/open/card")
public class CardTemplateController {

    @Resource
    private CardTemplateService cardTemplateService;

    /**
     * 查询全部卡片模板。
     */
    @GetMapping("/list")
    public Result<List<CardTemplate>> listCardTemplates() {
        return Result.success(cardTemplateService.listCardTemplates());
    }

    /**
     * 按租户查询卡片模板。
     */
    @GetMapping("/listByTenant")
    public Result<List<CardTemplate>> listCardTemplatesByTenant(@RequestParam String tenantCode) {
        return Result.success(cardTemplateService.listCardTemplatesByTenant(tenantCode));
    }

    /**
     * 按行业包查询卡片模板。
     */
    @GetMapping("/listByPack")
    public Result<List<CardTemplate>> listCardTemplatesByPack(@RequestParam String packCode) {
        return Result.success(cardTemplateService.listCardTemplatesByPack(packCode));
    }

    /**
     * 新增或更新卡片模板。
     */
    @PostMapping("/save")
    public Result<Void> saveCardTemplate(@RequestBody CardTemplate template) {
        cardTemplateService.saveCardTemplate(template);
        return Result.success();
    }

    /**
     * 删除卡片模板。
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteCardTemplate(@PathVariable Long id) {
        cardTemplateService.deleteCardTemplate(id);
        return Result.success();
    }

    /**
     * 启用/停用卡片模板。
     */
    @PutMapping("/enable/{id}")
    public Result<Void> setCardTemplateEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        cardTemplateService.setCardTemplateEnabled(id, enabled);
        return Result.success();
    }

    /**
     * 渲染卡片模板。
     */
    @PostMapping("/render")
    public Result<String> renderCardTemplate(@RequestParam String templateCode,
                                            @RequestParam String tenantCode,
                                            @RequestBody Map<String, Object> data) {
        return Result.success(cardTemplateService.renderCardTemplate(templateCode, tenantCode, data));
    }
}
