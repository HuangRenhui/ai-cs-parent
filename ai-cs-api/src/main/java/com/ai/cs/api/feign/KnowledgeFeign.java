package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.KnowledgeFeignFallback;
import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 知识库 Feign 接口，路径与 FaqController 一致。
 */
@FeignClient(value = "ai-cs-knowledge", url = "${feign.knowledge.url:http://localhost:8083}", fallback = KnowledgeFeignFallback.class)
public interface KnowledgeFeign {

    /** 知识库全文检索（兼容旧接口，返回纯文本答案）；tenantCode 必传，服务端不再隐式落到 default 租户 */
    @GetMapping("/knowledge/search")
    Result<String> search(@RequestParam("question") String question,
                          @RequestParam("tenantCode") String tenantCode,
                          @RequestParam(value = "sessionId", required = false) String sessionId);

    /** RAG 检索：返回命中状态、生成回复与引用来源；tenantCode 必传，industryPrompt 为人设/拒答叠加，可空 */
    @GetMapping("/knowledge/rag/search")
    Result<RagSearchResultDTO> ragSearch(@RequestParam("question") String question,
                                         @RequestParam("tenantCode") String tenantCode,
                                         @RequestParam(value = "sessionId", required = false) String sessionId,
                                         @RequestParam(value = "industryPrompt", required = false) String industryPrompt);

    /** 按 ID 查询 FAQ 详情 */
    @GetMapping("/knowledge/faq/{id}")
    Result<Object> getFaqById(@PathVariable("id") Long id);

    /** 按 ID 补录向量（对账用） */
    @PostMapping("/knowledge/vectorize/{id}")
    Result<String> vectorizeById(@PathVariable("id") Long id);
}
