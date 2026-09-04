package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SessionFeignFallback implements SessionFeign {

    @Override
    public Result<String> ensure(SessionDTO dto) {
        log.error("会话确保失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用");
    }

    @Override
    public Result<String> saveMessage(SessionDTO dto) {
        log.error("会话消息保存失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用");
    }

    @Override
    public Result<String> transfer(String sessionId) {
        log.error("会话转人工失败，触发熔断降级 sessionId={}", sessionId);
        return Result.fail(503, "基础服务暂时不可用");
    }
}
