package com.ai.cs.base.controller;

import com.ai.cs.base.entity.SlotFilling;
import com.ai.cs.base.service.SlotFillingService;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 多轮填槽配置控制器
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@RestController
@RequestMapping("/system/slot")
public class SlotFillingController {

    @Resource
    private SlotFillingService slotFillingService;

    /**
     * 查询意图关联的槽位列表
     *
     * @param tenantCode 租户编码
     * @param intentCode 意图编码
     * @return 槽位列表
     */
    @GetMapping("/list")
    public Result<List<SlotFilling>> list(@RequestParam(required = false) String tenantCode,
                                          @RequestParam(required = false) String intentCode) {
        try {
            List<SlotFilling> slots;
            if (intentCode != null && !intentCode.isEmpty()) {
                slots = slotFillingService.getSlotsByIntent(tenantCode, intentCode);
            } else {
                slots = slotFillingService.listByTenant(tenantCode);
            }
            return Result.success(slots);
        } catch (Exception e) {
            log.error("查询槽位列表失败", e);
            return Result.error("查询槽位列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询租户下全部槽位（含禁用，管理页用）
     *
     * @param tenantCode 租户编码
     * @return 槽位列表
     */
    @GetMapping("/listAll")
    public Result<List<SlotFilling>> listAll(@RequestParam(required = false) String tenantCode) {
        try {
            List<SlotFilling> slots = slotFillingService.listByTenant(tenantCode);
            return Result.success(slots);
        } catch (Exception e) {
            log.error("查询槽位列表失败", e);
            return Result.error("查询槽位列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询槽位
     *
     * @param id 槽位ID
     * @return 槽位配置
     */
    @GetMapping("/{id}")
    public Result<SlotFilling> getById(@PathVariable Long id) {
        try {
            SlotFilling slot = slotFillingService.getById(id);
            return Result.success(slot);
        } catch (Exception e) {
            log.error("查询槽位失败", e);
            return Result.error("查询槽位失败: " + e.getMessage());
        }
    }

    /**
     * 保存或更新槽位
     *
     * @param slotFilling 槽位配置
     * @return 是否成功
     */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody SlotFilling slotFilling) {
        try {
            boolean success = slotFillingService.saveOrUpdateSlot(slotFilling);
            return Result.success(success);
        } catch (Exception e) {
            log.error("保存槽位失败", e);
            return Result.error("保存槽位失败: " + e.getMessage());
        }
    }

    /**
     * 删除槽位
     *
     * @param id 槽位ID
     * @return 是否成功
     */
    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        try {
            boolean success = slotFillingService.deleteSlot(id);
            return Result.success(success);
        } catch (Exception e) {
            log.error("删除槽位失败", e);
            return Result.error("删除槽位失败: " + e.getMessage());
        }
    }
}
