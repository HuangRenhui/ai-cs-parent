package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.OpenToolFeignFallback;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 开放平台工具 Feign 接口：物流查询、退款等开放能力的统一调用入口。
 */
@FeignClient(value = "ai-cs-open", url = "${feign.open.url:http://localhost:8086}", fallback = OpenToolFeignFallback.class)
public interface OpenToolFeign {

    /** 调用开放工具（带意图绑定、确认标记与幂等键） */
    @PostMapping("/open/tool/invoke")
    Result<ToolInvokeResultDTO> invoke(@RequestBody ToolInvokeDTO dto);
}
