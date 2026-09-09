package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会话服务 Feign 降级：基础服务不可用时返回 503，调用方记日志但不阻断聊天主流程。
 */
@Slf4j
@Component
public class SessionFeignFallback implements SessionFeign {

    /** 降级：会话创建/确保失败，返回 503 */
    @Override
    public Result<String> ensure(SessionDTO dto) {
        log.error("会话确保失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用");
    }

    /** 降级：消息落库失败，返回 503（消息丢失仅影响历史记录，不影响本次回复） */
    @Override
    public Result<String> saveMessage(SessionDTO dto) {
        log.error("会话消息保存失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用");
    }

    /** 降级：转人工状态变更失败，返回 503，由上层决定是否重试 */
    @Override
    public Result<String> transfer(String sessionId) {
        log.error("会话转人工失败，触发熔断降级 sessionId={}", sessionId);
        return Result.fail(503, "基础服务暂时不可用");
    }
}
