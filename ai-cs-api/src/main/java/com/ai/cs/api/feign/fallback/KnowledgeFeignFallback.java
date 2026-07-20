package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识库 Feign 降级
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class KnowledgeFeignFallback implements KnowledgeFeign {

    @Override
    public Result<Object> search(Map<String, Object> params) {
        log.error("知识库服务调用失败，触发熔断降级");
        return Result.fail(503, "知识库服务暂时不可用，请稍后重试");
    }

    @Override
    public Result<Object> getFaqById(Long id) {
        log.error("知识库FAQ查询失败，触发熔断降级");
        return Result.fail(503, "知识库服务暂时不可用，请稍后重试");
    }
}
