package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开放工具 Feign 降级：开放平台不可用时返回 503，避免异常直接抛给对话主流程。
 */
@Slf4j
@Component
public class OpenToolFeignFallback implements OpenToolFeign {

    /** 降级：工具调用失败返回 503，上层收到失败结果后改用"开放能力不可用"话术 */
    @Override
    public Result<ToolInvokeResultDTO> invoke(ToolInvokeDTO dto) {
        log.error("开放工具调用失败，触发熔断降级");
        return Result.fail(503, "开放平台暂时不可用，请稍后重试");
    }
}
