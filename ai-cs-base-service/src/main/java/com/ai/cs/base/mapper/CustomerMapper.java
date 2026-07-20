package com.ai.cs.base.mapper;

import com.ai.cs.base.entity.Customer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户 Mapper
 *
 * @author huangrenhui
 * @date 2026/6/11 18:13
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}