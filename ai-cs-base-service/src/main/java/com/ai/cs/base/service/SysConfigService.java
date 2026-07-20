package com.ai.cs.base.service;

import com.ai.cs.base.entity.SysConfig;
import com.ai.cs.base.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 系统配置服务
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Service
public class SysConfigService extends ServiceImpl<SysConfigMapper, SysConfig> {

    /**
     * 根据配置键获取配置值（带缓存）
     */
    @Cacheable(value = "sysConfig", key = "#configKey")
    public String getConfigValue(String configKey) {
        SysConfig config = this.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .eq(SysConfig::getStatus, 1));
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 更新配置（清除缓存）
     */
    @CacheEvict(value = "sysConfig", key = "#entity.configKey")
    public boolean updateConfig(SysConfig entity) {
        return this.updateById(entity);
    }

    /**
     * 获取所有启用的配置
     */
    public java.util.List<SysConfig> getAllEnabled() {
        return this.list(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getStatus, 1));
    }
}
