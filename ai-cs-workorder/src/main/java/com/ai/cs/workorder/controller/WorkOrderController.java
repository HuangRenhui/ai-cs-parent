package com.ai.cs.workorder.controller;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:17
 * @description TODO
 */

import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.result.Result;
import com.ai.cs.workorder.service.WorkOrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;

@RestController
@RequestMapping("/workorder")
public class WorkOrderController {

    @Resource
    private WorkOrderService workOrderService;

    @PostMapping("/create")
    public Result<String> create(@RequestBody WorkOrderDTO dto) {
        String orderNo = workOrderService.createOrder(dto);
        return Result.success("工单创建成功，工单号：" + orderNo);
    }
}
