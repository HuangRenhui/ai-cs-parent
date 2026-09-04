package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.SessionFeignFallback;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "ai-cs-base-service", url = "${feign.base-service.url:http://localhost:8084}", fallback = SessionFeignFallback.class)
public interface SessionFeign {

    @PostMapping("/session/ensure")
    Result<String> ensure(@RequestBody SessionDTO dto);

    @PostMapping("/session/message")
    Result<String> saveMessage(@RequestBody SessionDTO dto);

    @PutMapping("/session/{sessionId}/transfer")
    Result<String> transfer(@PathVariable("sessionId") String sessionId);
}
