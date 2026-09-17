package com.ai.cs.base.service;

import com.ai.cs.base.entity.SlotFilling;
import com.ai.cs.base.mapper.SlotFillingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 多轮填槽配置服务
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@Service
public class SlotFillingService extends ServiceImpl<SlotFillingMapper, SlotFilling> {

    /**
     * 查询意图关联的槽位配置
     *
     * @param tenantCode 租户编码
     * @param intentCode 意图编码
     * @return 槽位列表
     */
    public List<SlotFilling> getSlotsByIntent(String tenantCode, String intentCode) {
        if (!StringUtils.hasText(tenantCode)) {
            tenantCode = "default";
        }
        LambdaQueryWrapper<SlotFilling> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlotFilling::getTenantCode, tenantCode)
                .eq(SlotFilling::getIntentCode, intentCode)
                .eq(SlotFilling::getEnabled, 1)
                .eq(SlotFilling::getDelFlag, 0)
                .orderByAsc(SlotFilling::getPriority);
        return list(wrapper);
    }

    /**
     * 查询租户下全部槽位（含禁用，不含已删除），供管理页使用
     *
     * @param tenantCode 租户编码
     * @return 槽位列表
     */
    public List<SlotFilling> listByTenant(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            tenantCode = "default";
        }
        LambdaQueryWrapper<SlotFilling> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlotFilling::getTenantCode, tenantCode)
                .eq(SlotFilling::getDelFlag, 0)
                .orderByAsc(SlotFilling::getPriority);
        return list(wrapper);
    }

    /**
     * 验证槽位值是否符合规则
     *
     * @param slot 槽位配置
     * @param value 槽位值
     * @return 是否验证通过
     */
    public boolean validateSlot(SlotFilling slot, String value) {
        if (slot == null || !StringUtils.hasText(value)) {
            return false;
        }
        // 如果配置了正则表达式，进行验证
        if (StringUtils.hasText(slot.getValidationRegex())) {
            try {
                Pattern pattern = Pattern.compile(slot.getValidationRegex());
                return pattern.matcher(value).matches();
            } catch (Exception e) {
                log.warn("槽位验证正则表达式错误: {}", slot.getValidationRegex(), e);
                return false;
            }
        }
        // 根据槽位类型进行基本验证
        return switch (slot.getSlotType()) {
            case "phone" -> value.matches("^1[3-9]\\d{9}$");
            case "number" -> value.matches("^\\d+$");
            case "order_id" -> StringUtils.hasText(value) && value.length() >= 6;
            default -> true; // string, date 等类型不做严格验证
        };
    }

    /**
     * 获取槽位的追问话术
     *
     * @param slot 槽位配置
     * @return 追问话术
     */
    public String getSlotPrompt(SlotFilling slot) {
        if (slot == null) {
            return "请提供相关信息";
        }
        if (StringUtils.hasText(slot.getPromptTemplate())) {
            return slot.getPromptTemplate();
        }
        // 默认追问话术
        return "请提供" + slot.getSlotName();
    }

    /**
     * 保存或更新槽位配置
     *
     * @param slotFilling 槽位配置
     * @return 是否成功
     */
    public boolean saveOrUpdateSlot(SlotFilling slotFilling) {
        if (!StringUtils.hasText(slotFilling.getTenantCode())) {
            slotFilling.setTenantCode("default");
        }
        if (slotFilling.getEnabled() == null) {
            slotFilling.setEnabled(1);
        }
        if (slotFilling.getDelFlag() == null) {
            slotFilling.setDelFlag(0);
        }
        if (slotFilling.getRequired() == null) {
            slotFilling.setRequired(1);
        }
        if (slotFilling.getPriority() == null) {
            slotFilling.setPriority(0);
        }
        return saveOrUpdate(slotFilling);
    }

    /**
     * 删除槽位配置（逻辑删除）
     *
     * @param id 槽位ID
     * @return 是否成功
     */
    public boolean deleteSlot(Long id) {
        SlotFilling slotFilling = getById(id);
        if (slotFilling != null) {
            slotFilling.setDelFlag(1);
            return updateById(slotFilling);
        }
        return false;
    }
}
