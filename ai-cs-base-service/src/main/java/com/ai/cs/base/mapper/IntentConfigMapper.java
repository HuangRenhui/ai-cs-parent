package com.ai.cs.base.mapper;

import com.ai.cs.base.entity.IntentConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 可配置意图Mapper
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Mapper
public interface IntentConfigMapper extends BaseMapper<IntentConfig> {

    /**
     * 查询租户启用的意图列表
     *
     * @param tenantCode 租户编码
     * @return 意图列表
     */
    List<IntentConfig> selectEnabledByTenant(String tenantCode);
}
