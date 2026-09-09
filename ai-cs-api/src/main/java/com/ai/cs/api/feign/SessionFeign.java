package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.SessionFeignFallback;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 会话服务 Feign 接口（由基础服务提供实现）：会话创建、消息落库、转人工。
 */
@FeignClient(value = "ai-cs-base-service", url = "${feign.base-service.url:http://localhost:8084}", fallback = SessionFeignFallback.class)
public interface SessionFeign {

    /** 确保会话存在（不存在则创建） */
    @PostMapping("/session/ensure")
    Result<String> ensure(@RequestBody SessionDTO dto);

    /** 保存一条会话消息 */
    @PostMapping("/session/message")
    Result<String> saveMessage(@RequestBody SessionDTO dto);

    /** 把指定会话转为人工接待 */
    @PutMapping("/session/{sessionId}/transfer")
    Result<String> transfer(@PathVariable("sessionId") String sessionId);
}
