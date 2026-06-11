package com.ai.cs.base.controller;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:14
 * @description TODO
 */
import com.ai.cs.base.entity.Customer;
import com.ai.cs.base.service.CustomerService;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/customer")
public class CustomerController {
    @Resource
    private CustomerService customerService;

    @GetMapping("/list")
    public Result<List<Customer>> list() {
        return Result.success(customerService.list());
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody Customer customer) {
        customerService.save(customer);
        return Result.success("新增成功");
    }
}
