package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.service.MultimodalSearchService;
import com.ai.cs.knowledge.service.MultimodalSearchService.MultimodalSearchResult;
import com.ai.cs.knowledge.service.MultimodalSearchService.ModalHit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 多模态检索控制器
 * 支持图文混合检索、跨模态语义搜索
 */
@Slf4j
@RestController
@RequestMapping("/api/multimodal")
@Tag(name = "多模态检索", description = "图文混合检索与跨模态语义搜索接口")
public class MultimodalSearchController {

    private final MultimodalSearchService multimodalSearchService;

    public MultimodalSearchController(MultimodalSearchService multimodalSearchService) {
        this.multimodalSearchService = multimodalSearchService;
    }

    /**
     * 多模态混合检索
     * 同时在图片库、音频库、知识库中搜索
     */
    @GetMapping("/search")
    @Operation(summary = "多模态混合检索", description = "同时在图片、音频、文本知识库中搜索，融合排序返回结果")
    public Result<MultimodalSearchResult> multimodalSearch(
            @Parameter(description = "搜索查询文本") @RequestParam String query,
            @Parameter(description = "搜索模态: image,audio,text（逗号分隔）") 
                @RequestParam(defaultValue = "image,audio,text") String modalities,
            @Parameter(description = "每种模态最大返回结果数") @RequestParam(defaultValue = "5") int maxResults) {
        try {
            List<String> modalityList = Arrays.asList(modalities.split(","));
            MultimodalSearchResult result = multimodalSearchService.multimodalSearch(
                    query, modalityList, maxResults);
            return Result.success(result);
        } catch (Exception e) {
            log.error("多模态检索失败", e);
            return Result.fail("多模态检索失败: " + e.getMessage());
        }
    }

    /**
     * 跨模态检索
     * 例如：用图片描述搜索音频，或用音频描述搜索图片
     */
    @GetMapping("/cross-search")
    @Operation(summary = "跨模态检索", description = "跨模态语义搜索，如用图片描述搜索音频或反之")
    public Result<Map<String, Object>> crossModalSearch(
            @Parameter(description = "搜索查询文本") @RequestParam String query,
            @Parameter(description = "源模态(image/audio/text)") @RequestParam String sourceModality,
            @Parameter(description = "目标模态(image/audio/text)") @RequestParam String targetModality,
            @Parameter(description = "最大返回结果数") @RequestParam(defaultValue = "5") int maxResults) {
        try {
            List<ModalHit> hits = multimodalSearchService.crossModalSearch(
                    query, sourceModality, targetModality, maxResults);

            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("sourceModality", sourceModality);
            result.put("targetModality", targetModality);
            result.put("totalHits", hits.size());
            result.put("hits", hits);

            return Result.success(result);
        } catch (Exception e) {
            log.error("跨模态检索失败", e);
            return Result.fail("跨模态检索失败: " + e.getMessage());
        }
    }

    /**
     * 图文混合问答
     * 结合图片检索结果和知识库检索结果进行综合回答
     */
    @GetMapping("/qa")
    @Operation(summary = "图文混合问答", description = "基于图片和文本知识库的综合问答")
    public Result<Map<String, Object>> multimodalQa(
            @Parameter(description = "用户问题") @RequestParam String question,
            @Parameter(description = "最大参考结果数") @RequestParam(defaultValue = "10") int maxResults) {
        try {
            // 同时在图片库和知识库中检索相关内容
            List<String> modalities = Arrays.asList("image", "text");
            MultimodalSearchResult searchResult = multimodalSearchService.multimodalSearch(
                    question, modalities, maxResults);

            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("totalReferences", searchResult.getTotalHits());
            result.put("modalityCount", searchResult.getModalityCount());
            result.put("references", searchResult.getHits());
            result.put("note", "建议将检索结果传递给LLM进行综合回答");

            return Result.success(result);
        } catch (Exception e) {
            log.error("图文混合问答失败", e);
            return Result.fail("问答失败: " + e.getMessage());
        }
    }

    /**
     * 获取多模态检索统计
     */
    @GetMapping("/stats")
    @Operation(summary = "多模态检索统计", description = "获取多模态向量库的统计信息")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("supportedModalities", Arrays.asList("image", "audio", "text"));
        stats.put("features", Arrays.asList(
                "多模态混合检索",
                "跨模态语义搜索",
                "图文混合问答",
                "向量融合排序"
        ));
        return Result.success(stats);
    }
}
