package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工单服务 Feign 降级
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class WorkOrderFeignFallback implements WorkOrderFeign {

    @Override
    public Result<String> createOrder(WorkOrderDTO dto) {
        log.error("工单服务调用失败，触发熔断降级");
        return Result.fail(503, "工单服务暂时不可用，请稍后重试");
    }
}
