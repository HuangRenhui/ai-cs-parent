package com.ai.cs.base.service;

import com.ai.cs.base.entity.IntentConfig;
import com.ai.cs.base.mapper.IntentConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 可配置意图服务
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@Service
public class IntentConfigService extends ServiceImpl<IntentConfigMapper, IntentConfig> {

    /**
     * 查询租户启用的意图列表
     *
     * @param tenantCode 租户编码
     * @return 意图列表
     */
    public List<IntentConfig> getEnabledIntents(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            tenantCode = "default";
        }
        LambdaQueryWrapper<IntentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentConfig::getTenantCode, tenantCode)
                .eq(IntentConfig::getEnabled, 1)
                .eq(IntentConfig::getDelFlag, 0)
                .orderByAsc(IntentConfig::getPriority);
        return list(wrapper);
    }

    /**
     * 查询租户下全部意图（含禁用，不含已删除），供管理页使用
     *
     * @param tenantCode 租户编码
     * @return 意图列表
     */
    public List<IntentConfig> listByTenant(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            tenantCode = "default";
        }
        LambdaQueryWrapper<IntentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentConfig::getTenantCode, tenantCode)
                .eq(IntentConfig::getDelFlag, 0)
                .orderByAsc(IntentConfig::getPriority);
        return list(wrapper);
    }

    /**
     * 根据意图编码查询
     *
     * @param tenantCode 租户编码
     * @param intentCode 意图编码
     * @return 意图配置
     */
    public IntentConfig getByCode(String tenantCode, String intentCode) {
        if (!StringUtils.hasText(tenantCode)) {
            tenantCode = "default";
        }
        LambdaQueryWrapper<IntentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentConfig::getTenantCode, tenantCode)
                .eq(IntentConfig::getIntentCode, intentCode)
                .eq(IntentConfig::getDelFlag, 0);
        return getOne(wrapper);
    }

    /**
     * 保存或更新意图配置
     *
     * @param intentConfig 意图配置
     * @return 是否成功
     */
    public boolean saveOrUpdateIntent(IntentConfig intentConfig) {
        if (!StringUtils.hasText(intentConfig.getTenantCode())) {
            intentConfig.setTenantCode("default");
        }
        if (intentConfig.getEnabled() == null) {
            intentConfig.setEnabled(1);
        }
        if (intentConfig.getDelFlag() == null) {
            intentConfig.setDelFlag(0);
        }
        if (intentConfig.getPriority() == null) {
            intentConfig.setPriority(0);
        }
        return saveOrUpdate(intentConfig);
    }

    /**
     * 删除意图配置（逻辑删除）
     *
     * @param id 意图ID
     * @return 是否成功
     */
    public boolean deleteIntent(Long id) {
        IntentConfig intentConfig = getById(id);
        if (intentConfig != null) {
            intentConfig.setDelFlag(1);
            return updateById(intentConfig);
        }
        return false;
    }
}
