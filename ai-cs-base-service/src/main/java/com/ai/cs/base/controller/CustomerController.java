package com.ai.cs.base.controller;

import com.ai.cs.base.entity.Customer;
import com.ai.cs.base.service.CustomerService;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 客户控制器
 *
 * @author huangrenhui
 * @date 2026/6/11 18:14
 */
@RestController
@RequestMapping("/customer")
public class CustomerController {

    @Resource
    private CustomerService customerService;

    /**
     * 客户列表（支持关键字模糊搜索）
     */
    @GetMapping("/list")
    public Result<List<Customer>> list(@RequestParam(required = false) String keyword) {
        return Result.success(customerService.listByKeyword(keyword));
    }

    /**
     * 客户详情
     */
    @GetMapping("/{id}")
    public Result<Customer> detail(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        return Result.success(customer);
    }

    /**
     * 新增客户（强制清空 id，防止伪造更新请求）
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Customer customer) {
        customer.setId(null);
        customerService.saveCustomer(customer);
        return Result.success("新增成功");
    }

    /**
     * 更新客户
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Customer customer) {
        if (customer.getId() == null) {
            throw new BusinessException("客户ID不能为空");
        }
        customerService.saveCustomer(customer);
        return Result.success("修改成功");
    }

    /**
     * 删除客户（逻辑删除）
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        if (customerService.getById(id) == null) {
            throw new BusinessException("客户不存在");
        }
        customerService.removeById(id);
        return Result.success("删除成功");
    }
}
