package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.AiAgentFeign;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.ChatReplyDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI智能体 Feign 降级
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class AiAgentFeignFallback implements AiAgentFeign {

    /** 降级：AI 服务不可用时返回 503 提示，调用方需按失败结果走兜底话术 */
    @Override
    public Result<ChatReplyDTO> chat(ChatDTO dto) {
        log.error("AI智能体服务调用失败，触发熔断降级");
        return Result.fail(503, "AI智能体服务暂时不可用，请稍后重试");
    }
}
