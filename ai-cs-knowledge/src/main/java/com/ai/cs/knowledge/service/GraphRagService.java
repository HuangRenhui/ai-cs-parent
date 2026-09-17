package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.util.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 图谱增强 RAG 服务（占位）
 *
 * <p>TODO 后续实现：把知识图谱作为 RAG 的结构化补充——
 * <ul>
 *   <li>{@link #graphRagQa}：从问题提取实体 → 沿图谱抽取相关子图上下文 → 与向量检索结果拼成增强 Prompt → 调模型生成综合回答并附带引用；</li>
 *   <li>{@link #entityEnhancedRetrieval}：先用实体识别缩小范围，再做向量检索，提升召回相关性；</li>
 *   <li>{@link #graphGuidedRetrieval}：以图谱路径为引导做多跳探索（限制最大路径长度），沿路径收集节点与关系；</li>
 *   <li>{@link #graphEnhancedRanking}：在向量结果上叠加图谱关联度做重排；</li>
 *   <li>{@link #getGraphRagStats}：图谱增强检索的统计信息。</li>
 * </ul>
 * </p>
 *
 * <p>当前不检索、不调用模型：各方法返回空 Map / 空列表（统计亦为空）。</p>
 */
@Slf4j
@Service
public class GraphRagService {

    private final KnowledgeGraphService graphService;
    private final HybridRetrievalService hybridRetrievalService;
    private final LlmClient llmClient;
    private final PromptTemplateService promptTemplateService;

    public GraphRagService(KnowledgeGraphService graphService,
                           HybridRetrievalService hybridRetrievalService,
                           LlmClient llmClient,
                           PromptTemplateService promptTemplateService) {
        this.graphService = graphService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.llmClient = llmClient;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 图谱增强问答（占位：返回空 Map）
     *
     * @param question 用户问题
     * @param topK     向量检索条数
     * @return 空 Map
     */
    public Map<String, Object> graphRagQa(String question, int topK) {
        log.info("[占位] 图谱增强问答未实现 topK={}", topK);
        return Map.of();
    }

    /**
     * 实体增强检索（占位：返回空 Map）
     */
    public Map<String, Object> entityEnhancedRetrieval(String question) {
        log.info("[占位] 实体增强检索未实现");
        return Map.of();
    }

    /**
     * 图谱引导检索（占位：返回空 Map）
     */
    public Map<String, Object> graphGuidedRetrieval(String question, int maxPathLength) {
        log.info("[占位] 图谱引导检索未实现 maxPathLength={}", maxPathLength);
        return Map.of();
    }

    /**
     * 图谱增强重排（占位：返回空列表）
     *
     * @param vectorResults 向量检索结果
     * @return 空列表
     */
    public List<Map<String, Object>> graphEnhancedRanking(String question, List<Map<String, Object>> vectorResults) {
        log.info("[占位] 图谱增强重排未实现，返回空列表");
        return List.of();
    }

    /**
     * 图谱增强检索统计（占位：返回空 Map）
     */
    public Map<String, Object> getGraphRagStats() {
        log.info("[占位] 图谱增强统计未实现，返回空 Map");
        return Map.of();
    }
}
