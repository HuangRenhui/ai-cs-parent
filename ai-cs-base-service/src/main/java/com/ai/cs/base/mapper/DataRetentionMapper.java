package com.ai.cs.base.mapper;

import com.ai.cs.base.entity.DataRetention;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据保留策略Mapper
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Mapper
public interface DataRetentionMapper extends BaseMapper<DataRetention> {
}
