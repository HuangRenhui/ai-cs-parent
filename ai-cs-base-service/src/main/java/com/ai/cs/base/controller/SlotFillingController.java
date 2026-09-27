package com.ai.cs.base.controller;

import com.ai.cs.base.entity.SlotFilling;
import com.ai.cs.base.service.SlotFillingService;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 多轮填槽配置控制器
 *
 * <p>异常统一交给 {@code GlobalExceptionHandler}：这里不再 try/catch，避免把内部异常信息
 * 拼进返回体、也避免绕过全局错误处理。</p>
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@RestController
@RequestMapping("/system/slot")
public class SlotFillingController {

    @Resource
    private SlotFillingService slotFillingService;

    /**
     * 查询槽位列表
     *
     * @param tenantCode 租户编码
     * @param intentCode 意图编码
     * @return 槽位列表
     */
    @GetMapping("/list")
    public Result<List<SlotFilling>> list(@RequestParam(required = false) String tenantCode,
                                          @RequestParam(required = false) String intentCode) {
        // TODO(租户令牌优先) 与 IntentConfigController 同一口径：绑定租户的令牌必须忽略入参 tenantCode。
        //  实现：String tenant = JwtContext.resolveTenantCode(tenantCode);（需 import com.ai.cs.common.security.JwtContext）
        //  然后把下面两处 service 调用里的 tenantCode 换成 tenant。
        List<SlotFilling> slots = (intentCode != null && !intentCode.isEmpty())
                ? slotFillingService.getSlotsByIntent(tenantCode, intentCode)
                : slotFillingService.listByTenant(tenantCode);
        return Result.success(slots);
    }

    /**
     * 查询租户下全部槽位（含禁用，管理页用）
     *
     * @param tenantCode 租户编码
     * @return 槽位列表
     */
    @GetMapping("/listAll")
    public Result<List<SlotFilling>> listAll(@RequestParam(required = false) String tenantCode) {
        // TODO(租户令牌优先) 同 list：改为 String tenant = JwtContext.resolveTenantCode(tenantCode); 再传 service。
        return Result.success(slotFillingService.listByTenant(tenantCode));
    }

    /**
     * 根据ID查询槽位
     *
     * @param id 槽位ID
     * @return 槽位配置
     */
    @GetMapping("/{id}")
    public Result<SlotFilling> getById(@PathVariable Long id) {
        return Result.success(slotFillingService.getById(id));
    }

    /**
     * 保存或更新槽位
     *
     * @param slotFilling 槽位配置
     * @return 是否成功
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody SlotFilling slotFilling) {
        return Result.success(slotFillingService.saveOrUpdateSlot(slotFilling));
    }

    /**
     * 删除槽位
     *
     * @param id 槽位ID
     * @return 是否成功
     */
    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(slotFillingService.deleteSlot(id));
    }
}
