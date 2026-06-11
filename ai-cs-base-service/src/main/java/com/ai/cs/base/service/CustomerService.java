package com.ai.cs.base.service;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:14
 * @description TODO
 */
import com.ai.cs.base.entity.Customer;
import com.ai.cs.base.mapper.CustomerMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {
}
