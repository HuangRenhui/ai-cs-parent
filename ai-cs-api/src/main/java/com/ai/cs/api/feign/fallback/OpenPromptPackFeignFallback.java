package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.OpenPromptPackFeign;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 行业提示词包 Feign 降级：开放层不可用时返回空串，调用方回退内置包或 PromptConst。
 */
@Slf4j
@Component
public class OpenPromptPackFeignFallback implements OpenPromptPackFeign {

    /** 降级：提示词包加载失败，返回空串避免阻断对话 */
    @Override
    public Result<String> load(String packCode) {
        log.error("行业提示词包加载失败，触发熔断降级 packCode={}", packCode);
        return Result.fail(503, "开放平台暂时不可用");
    }
}
