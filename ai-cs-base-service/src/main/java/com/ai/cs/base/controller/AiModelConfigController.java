package com.ai.cs.base.controller;

import com.ai.cs.base.entity.AiModelConfig;
import com.ai.cs.base.entity.ModelUsageRecord;
import com.ai.cs.base.service.AiModelConfigService;
import com.ai.cs.base.service.ModelUsageService;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.security.RequirePermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

/**
 * AI 模型注册与管理控制器：注册/编辑/删除、启停、设为生效、测试连接、健康状态。
 *
 * <p><b>权限校验</b>：查询类接口要求 {@code ai-model:list}，写操作（注册/启停/生效/删除/测试连接）
 * 要求更高的 {@code ai-model:manage}——模型密钥与生效切换属于高危操作，不能只靠登录态。
 * 超管持有通配权限 {@code *:*:*}，不受权限点未配置的影响。</p>
 *
 * @author ai-cs
 */
@RestController
@RequestMapping("/system/ai-model")
public class AiModelConfigController {

    @Resource
    private AiModelConfigService aiModelConfigService;

    @Resource
    private ModelUsageService modelUsageService;

    /** 全部模型（含启用/停用） */
    @RequirePermission("ai-model:list")
    @GetMapping("/list")
    public Result<List<AiModelConfig>> list() {
        return Result.success(aiModelConfigService.listAll());
    }

    /** 某能力已启用的模型 */
    @RequirePermission("ai-model:list")
    @GetMapping("/enabled")
    public Result<List<AiModelConfig>> enabled(@RequestParam(required = false) String modelType) {
        return Result.success(aiModelConfigService.listEnabled(modelType));
    }

    /** 某能力当前生效模型 */
    @RequirePermission("ai-model:list")
    @GetMapping("/active")
    public Result<AiModelConfig> active(@RequestParam String modelType) {
        return Result.success(aiModelConfigService.getActive(modelType));
    }

    /** 用量/失败记录分页 */
    @RequirePermission("ai-model:list")
    @GetMapping("/usage/page")
    public Result<Page<ModelUsageRecord>> usagePage(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) Long modelId,
                                                    @RequestParam(required = false) String modelType,
                                                    @RequestParam(required = false) Integer success,
                                                    @RequestParam(required = false) String startTime,
                                                    @RequestParam(required = false) String endTime) {
        return Result.success(modelUsageService.page(page, size, modelId, modelType, success, startTime, endTime));
    }

    /** 用量汇总 */
    @RequirePermission("ai-model:list")
    @GetMapping("/usage/summary")
    public Result<Map<String, Object>> usageSummary(@RequestParam(required = false) String modelType,
                                                    @RequestParam(required = false) String startTime,
                                                    @RequestParam(required = false) String endTime) {
        return Result.success(modelUsageService.summary(modelType, startTime, endTime));
    }

    /** 近 N 分钟失败次数（告警用） */
    @RequirePermission("ai-model:list")
    @GetMapping("/usage/recent-fail")
    public Result<Long> recentFail(@RequestParam(defaultValue = "5") int minutes) {
        return Result.success(modelUsageService.recentFailCount(minutes));
    }

    /** 注册或修改模型 */
    @RequirePermission("ai-model:manage")
    @PostMapping("/save")
    public Result<String> save(@RequestBody AiModelConfig config) {
        aiModelConfigService.saveModel(config);
        return Result.success("保存成功");
    }

    /** 设为生效（同能力内唯一） */
    @RequirePermission("ai-model:manage")
    @PutMapping("/active/{id}")
    public Result<String> setActive(@PathVariable Long id) {
        aiModelConfigService.setActive(id);
        return Result.success("已设为生效");
    }

    /** 启停模型 */
    @RequirePermission("ai-model:manage")
    @PutMapping("/enabled/{id}")
    public Result<String> setEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        aiModelConfigService.setEnabled(id, enabled);
        return Result.success(enabled ? "已启用" : "已停用");
    }

    /** 删除模型 */
    @RequirePermission("ai-model:manage")
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        aiModelConfigService.removeModel(id);
        return Result.success("删除成功");
    }

    /** 测试连接并刷新健康状态 */
    @RequirePermission("ai-model:manage")
    @PostMapping("/test/{id}")
    public Result<String> test(@PathVariable Long id) {
        String msg = aiModelConfigService.testConnect(id);
        return Result.success(msg);
    }
}
