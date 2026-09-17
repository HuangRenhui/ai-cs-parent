package com.ai.cs.open.mapper;

import com.ai.cs.open.entity.OpenConnector;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 连接器表（cs_open_connector）Mapper，基础 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface OpenConnectorMapper extends BaseMapper<OpenConnector> {
}
