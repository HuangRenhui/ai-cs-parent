package com.ai.cs.base.service;

import com.ai.cs.base.entity.Customer;
import com.ai.cs.base.mapper.CustomerMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 客户服务
 *
 * @author huangrenhui
 * @date 2026/6/11 18:14
 */
@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {
}
