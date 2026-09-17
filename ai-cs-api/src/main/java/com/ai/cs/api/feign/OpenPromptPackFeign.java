package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.OpenPromptPackFeignFallback;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 行业提示词包 Feign：对话主链路按 packCode 拉取人设/拒答，避免 Agent 写死电商话术。
 */
@FeignClient(value = "ai-cs-open", url = "${feign.open.url:http://localhost:8086}", fallback = OpenPromptPackFeignFallback.class)
public interface OpenPromptPackFeign {

    /**
     * 加载行业包拼接后的系统提示词（人设 + 拒答 + 槽位）。
     *
     * @param packCode 行业包编码
     * @return 拼接后的提示词；无配置时 data 为空串
     */
    @GetMapping("/open/prompt/pack/load/{packCode}")
    Result<String> load(@PathVariable("packCode") String packCode);
}
