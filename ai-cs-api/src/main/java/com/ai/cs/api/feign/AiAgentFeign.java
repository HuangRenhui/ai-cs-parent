package com.ai.cs.api.feign;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:48
 * @description AI 智能体 Feign 接口
 * AI 对话
 */
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("ai-cs-agent")
public interface AiAgentFeign {

    @PostMapping("/ai/chat/send")
    Result<String> chat(@RequestBody ChatDTO dto);
}