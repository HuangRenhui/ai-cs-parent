package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.OpenToolFeignFallback;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 开放平台工具 Feign 接口：物流查询、退款等开放能力的统一调用入口。
 */
@FeignClient(value = "ai-cs-open", url = "${feign.open.url:http://localhost:8086}", fallback = OpenToolFeignFallback.class)
public interface OpenToolFeign {

    /** 调用开放工具（带意图绑定、确认标记与幂等键） */
    @PostMapping("/open/tool/invoke")
    Result<ToolInvokeResultDTO> invoke(@RequestBody ToolInvokeDTO dto);

    /**
     * 查询注册工具，供模型按 Schema 自动选工具。
     * 用 Map 承接，避免 agent 依赖 open 的实体类；open 端 /open/tool/list 返回结构不变。
     */
    @GetMapping("/open/tool/list")
    Result<List<Map<String, Object>>> listTools();
}
