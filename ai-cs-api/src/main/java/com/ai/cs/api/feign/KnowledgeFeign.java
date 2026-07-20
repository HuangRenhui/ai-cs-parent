package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.KnowledgeFeignFallback;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 知识库 Feign 接口
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@FeignClient(value = "ai-cs-knowledge", fallback = KnowledgeFeignFallback.class)
public interface KnowledgeFeign {

    @PostMapping("/knowledge/search")
    Result<Object> search(@RequestBody Map<String, Object> params);

    @GetMapping("/knowledge/faq/{id}")
    Result<Object> getFaqById(@PathVariable("id") Long id);
}
