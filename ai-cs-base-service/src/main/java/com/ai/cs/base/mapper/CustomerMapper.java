package com.ai.cs.base.mapper;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:13
 * @description TODO
 */
import com.ai.cs.base.entity.Customer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}