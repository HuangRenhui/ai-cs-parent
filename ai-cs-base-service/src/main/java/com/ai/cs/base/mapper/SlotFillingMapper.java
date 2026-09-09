package com.ai.cs.base.mapper;

import com.ai.cs.base.entity.SlotFilling;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 多轮填槽配置Mapper
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Mapper
public interface SlotFillingMapper extends BaseMapper<SlotFilling> {

    /**
     * 查询意图关联的槽位配置
     *
     * @param tenantCode 租户编码
     * @param intentCode 意图编码
     * @return 槽位列表
     */
    List<SlotFilling> selectByIntent(String tenantCode, String intentCode);
}
