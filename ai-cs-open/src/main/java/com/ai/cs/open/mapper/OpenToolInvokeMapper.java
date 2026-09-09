package com.ai.cs.open.mapper;

import com.ai.cs.open.entity.OpenToolInvoke;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用记录表（cs_open_tool_invoke）Mapper，基础 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface OpenToolInvokeMapper extends BaseMapper<OpenToolInvoke> {
}
