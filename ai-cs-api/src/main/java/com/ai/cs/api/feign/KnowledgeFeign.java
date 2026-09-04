package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.KnowledgeFeignFallback;
import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 知识库 Feign 接口，路径与 FaqController 一致。
 */
@FeignClient(value = "ai-cs-knowledge", url = "${feign.knowledge.url:http://localhost:8083}", fallback = KnowledgeFeignFallback.class)
public interface KnowledgeFeign {

    @GetMapping("/knowledge/search")
    Result<String> search(@RequestParam("question") String question,
                          @RequestParam(value = "tenantCode", required = false) String tenantCode,
                          @RequestParam(value = "sessionId", required = false) String sessionId);

    @GetMapping("/knowledge/rag/search")
    Result<RagSearchResultDTO> ragSearch(@RequestParam("question") String question,
                                         @RequestParam(value = "tenantCode", required = false) String tenantCode,
                                         @RequestParam(value = "sessionId", required = false) String sessionId);

    @GetMapping("/knowledge/faq/{id}")
    Result<Object> getFaqById(@PathVariable("id") Long id);
}
