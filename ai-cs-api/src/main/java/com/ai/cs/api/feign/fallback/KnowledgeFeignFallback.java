package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识库 Feign 降级
 */
@Slf4j
@Component
public class KnowledgeFeignFallback implements KnowledgeFeign {

    /** 降级：检索失败返回 503，调用方据失败结果标记知识库不可用 */
    @Override
    public Result<String> search(String question, String tenantCode, String sessionId) {
        log.error("知识库检索失败，触发熔断降级");
        return Result.fail(503, "知识库服务暂时不可用，请稍后重试");
    }

    /** 降级：RAG 检索失败返回 503，上层会转换为 UNAVAILABLE 状态并给出兜底回复 */
    @Override
    public Result<RagSearchResultDTO> ragSearch(String question, String tenantCode, String sessionId) {
        log.error("知识库RAG检索失败，触发熔断降级");
        return Result.fail(503, "知识库服务暂时不可用，请稍后重试");
    }

    /** 降级：FAQ 查询失败返回 503 */
    @Override
    public Result<Object> getFaqById(Long id) {
        log.error("知识库FAQ查询失败，触发熔断降级");
        return Result.fail(503, "知识库服务暂时不可用，请稍后重试");
    }
}
