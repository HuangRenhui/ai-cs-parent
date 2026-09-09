package com.ai.cs.base.service;

import com.ai.cs.base.entity.DataRetention;
import com.ai.cs.base.mapper.DataRetentionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据保留策略服务
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@Service
public class DataRetentionService extends ServiceImpl<DataRetentionMapper, DataRetention> {

    /**
     * 查询租户下全部数据保留策略（含禁用），供管理页使用
     *
     * @param tenantCode 租户编码
     * @return 策略列表
     */
    public List<DataRetention> listByTenant(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            tenantCode = "default";
        }
        LambdaQueryWrapper<DataRetention> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataRetention::getTenantCode, tenantCode)
                .orderByAsc(DataRetention::getDataType);
        return list(wrapper);
    }

    /**
     * 获取数据保留策略
     *
     * @param tenantCode 租户编码
     * @param dataType 数据类型
     * @return 数据保留策略
     */
    public DataRetention getDataRetention(String tenantCode, String dataType) {
        if (!StringUtils.hasText(tenantCode)) {
            tenantCode = "default";
        }
        LambdaQueryWrapper<DataRetention> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataRetention::getTenantCode, tenantCode)
                .eq(DataRetention::getDataType, dataType)
                .eq(DataRetention::getStatus, 1);
        DataRetention retention = getOne(wrapper);
        // 如果没有配置，返回默认策略
        if (retention == null) {
            retention = new DataRetention();
            retention.setTenantCode(tenantCode);
            retention.setDataType(dataType);
            retention.setRetentionDays(90);
            retention.setAllowExternalDomain(0);
            retention.setAllowUserDelete(1);
        }
        return retention;
    }

    /**
     * 检查是否允许数据出域
     *
     * @param tenantCode 租户编码
     * @param dataType 数据类型
     * @return 是否允许
     */
    public boolean isAllowExternalDomain(String tenantCode, String dataType) {
        DataRetention retention = getDataRetention(tenantCode, dataType);
        return retention != null && retention.getAllowExternalDomain() != null
                && retention.getAllowExternalDomain() == 1;
    }

    /**
     * 检查是否允许用户删除
     *
     * @param tenantCode 租户编码
     * @param dataType 数据类型
     * @return 是否允许
     */
    public boolean isAllowUserDelete(String tenantCode, String dataType) {
        DataRetention retention = getDataRetention(tenantCode, dataType);
        return retention != null && retention.getAllowUserDelete() != null
                && retention.getAllowUserDelete() == 1;
    }

    /**
     * 获取数据保留天数
     *
     * @param tenantCode 租户编码
     * @param dataType 数据类型
     * @return 保留天数
     */
    public int getRetentionDays(String tenantCode, String dataType) {
        DataRetention retention = getDataRetention(tenantCode, dataType);
        return retention != null && retention.getRetentionDays() != null
                ? retention.getRetentionDays() : 90;
    }

    /**
     * 保存或更新数据保留策略
     *
     * @param dataRetention 数据保留策略
     * @return 是否成功
     */
    public boolean saveOrUpdateRetention(DataRetention dataRetention) {
        if (!StringUtils.hasText(dataRetention.getTenantCode())) {
            dataRetention.setTenantCode("default");
        }
        if (dataRetention.getStatus() == null) {
            dataRetention.setStatus(1);
        }
        if (dataRetention.getRetentionDays() == null) {
            dataRetention.setRetentionDays(90);
        }
        if (dataRetention.getAllowExternalDomain() == null) {
            dataRetention.setAllowExternalDomain(0);
        }
        if (dataRetention.getAllowUserDelete() == null) {
            dataRetention.setAllowUserDelete(1);
        }
        return saveOrUpdate(dataRetention);
    }
}
