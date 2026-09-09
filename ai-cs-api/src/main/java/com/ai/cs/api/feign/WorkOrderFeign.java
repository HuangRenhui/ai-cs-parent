package com.ai.cs.api.feign;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:04
 * @description 工单
 */

import com.ai.cs.api.feign.fallback.WorkOrderFeignFallback;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.dto.WorkOrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "ai-cs-workorder", url = "${feign.workorder.url:http://localhost:8085}", fallback = WorkOrderFeignFallback.class)
public interface WorkOrderFeign {

    /** 创建工单，返回工单号或创建结果文案 */
    @PostMapping("/workorder/create")
    Result<String> createOrder(@RequestBody WorkOrderDTO dto);
}