package com.ai.cs.api.feign;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:04
 * @description 工单
 */

import com.ai.cs.common.result.Result;
import com.ai.cs.common.dto.WorkOrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("ai-cs-workorder")
public interface WorkOrderFeign {
    @PostMapping("/workorder/create")
    Result<String> createOrder(@RequestBody WorkOrderDTO dto);
}