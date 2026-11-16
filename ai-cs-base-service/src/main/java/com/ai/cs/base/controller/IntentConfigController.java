package com.ai.cs.base.controller;

import com.ai.cs.base.entity.IntentConfig;
import com.ai.cs.base.service.IntentConfigService;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.security.JwtContext;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 可配置意图控制器
 *
 * <p>异常统一交给 {@code GlobalExceptionHandler}：这里不再 try/catch，避免把内部异常信息
 * 拼进返回体、也避免绕过全局错误处理。</p>
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@RestController
@RequestMapping("/system/intent")
public class IntentConfigController {

    @Resource
    private IntentConfigService intentConfigService;

    /**
     * 查询租户启用的意图列表
     *
     * @param tenantCode 租户编码
     * @return 意图列表
     */
    @GetMapping("/list")
    public Result<List<IntentConfig>> list(@RequestParam(required = false) String tenantCode) {
        // 令牌带租户 → 一律以令牌为准（忽略入参）；平台级账号 → 用入参；都为空 → default
        String tenant = JwtContext.resolveTenantCode(tenantCode);
        return Result.success(intentConfigService.getEnabledIntents(tenant));
    }

    /**
     * 查询租户下全部意图（含禁用，管理页用）
     *
     * @param tenantCode 租户编码
     * @return 意图列表
     */
    @GetMapping("/listAll")
    public Result<List<IntentConfig>> listAll(@RequestParam(required = false) String tenantCode) {
        // 同 list：管理页也按令牌租户收敛，避免翻到别的租户（含禁用）的意图配置
        String tenant = JwtContext.resolveTenantCode(tenantCode);
        return Result.success(intentConfigService.listByTenant(tenant));
    }

    /**
     * 根据ID查询意图
     *
     * @param id 意图ID
     * @return 意图配置
     */
    @GetMapping("/{id}")
    public Result<IntentConfig> getById(@PathVariable Long id) {
        return Result.success(intentConfigService.getById(id));
    }

    /**
     * 保存或更新意图
     *
     * @param intentConfig 意图配置
     * @return 是否成功
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody IntentConfig intentConfig) {
        return Result.success(intentConfigService.saveOrUpdateIntent(intentConfig));
    }

    /**
     * 删除意图
     *
     * @param id 意图ID
     * @return 是否成功
     */
    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(intentConfigService.deleteIntent(id));
    }
}
