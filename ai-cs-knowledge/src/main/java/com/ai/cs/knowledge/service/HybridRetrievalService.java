package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.MultimodalKnowledgeProperties;
import com.ai.cs.knowledge.config.RagProperties;
import com.ai.cs.knowledge.mapper.MultimodalKnowledgeMapper;
import com.ai.cs.knowledge.util.EmbeddingClient;
import com.ai.cs.knowledge.util.LlmClient;
import com.ai.cs.knowledge.util.MilvusUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合多模态检索服务（占位）
 *
 * <p>TODO 后续实现：
 * <ul>
 *   <li>{@link #hybridSearch}：按请求的模态分别检索（TEXT：FAQ + Chroma 文档 + 关键词；
 *       IMAGE/AUDIO/VIDEO：各自向量库与元数据表），再做质量过滤、相似度阈值过滤、召回数限制与上下文压缩；</li>
 *   <li>融合排序：RRF（Reciprocal Rank Fusion，k 加权）、加权融合、线性融合三种策略；</li>
 *   <li>{@link #multimodalChat}：把命中的多模态片段组织成上下文交给 LLM 生成综合回答；</li>
 *   <li>{@link #crossModalSearch}：跨模态检索（如用图片描述搜音频），需用模态上下文丰富查询。</li>
 * </ul>
 * </p>
 *
 * <p>当前不检索、不调用模型：{@link #hybridSearch} 与 {@link #crossModalSearch} 返回空列表，
 * {@link #multimodalChat} 返回未实现提示。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final EmbeddingClient embeddingClient;
    private final MilvusUtil milvusUtil;
    private final RagSearchService ragSearchService;
    private final MultimodalKnowledgeMapper multimodalKnowledgeMapper;
    private final KnowledgeFaqService faqService;
    private final MultimodalKnowledgeProperties multimodalProperties;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;
    private final RetrievalOptimizerService retrievalOptimizer;
    private final PromptTemplateService promptTemplateService;

    /**
     * 混合检索结果条目
     */
    public static class HybridSearchResult {
        /** 结果条目ID（向量ID或知识ID） */
        public String id;
        /** 命中的文本内容（文档片段/图片描述/音频描述） */
        public String content;
        /** 所属模态 */
        public String modality;   // TEXT, IMAGE, AUDIO, VIDEO
        /** 数据来源类型 */
        public String sourceType; // faq, document, image_knowledge, audio_knowledge
        /** 来源业务ID（FAQ主键/资源ID等） */
        public String sourceId;
        /** 来源标题（FAQ问题/知识标题） */
        public String sourceTitle;
        /** 相关性分数（融合排序后会被改写为融合分） */
        public double score;
        /** 附加元数据（存储路径、标签等） */
        public Map<String, Object> metadata;

        public HybridSearchResult(String id, String content, String modality, double score) {
            this.id = id;
            this.content = content;
            this.modality = modality;
            this.score = score;
            this.metadata = new HashMap<>();
        }
    }

    // ========== 混合检索核心方法（占位） ==========

    /**
     * 混合多模态检索（占位：返回空列表）
     *
     * @param query      查询文本
     * @param modalities 需要检索的模态列表: TEXT, IMAGE, AUDIO, VIDEO
     * @param topK       每种模态返回的结果数
     * @return 空列表
     */
    public List<HybridSearchResult> hybridSearch(String query, List<String> modalities, int topK) {
        log.info("[占位] 混合多模态检索未实现，返回空列表 modalities={} topK={}", modalities, topK);
        return List.of();
    }

    /**
     * 多模态融合问答（占位：返回未实现提示）
     *
     * @param question       用户问题
     * @param includeImages  是否纳入图片知识
     * @param includeAudios  是否纳入音频知识
     * @return 未实现提示
     */
    public String multimodalChat(String question, boolean includeImages, boolean includeAudios) {
        log.info("[占位] 多模态融合问答未实现 includeImages={} includeAudios={}", includeImages, includeAudios);
        return "多模态融合问答为占位实现，后端未接入混合检索与模型生成";
    }

    /**
     * 跨模态检索（占位：返回空列表）
     *
     * @param query          查询文本
     * @param sourceModality 源模态
     * @param targetModality 目标模态
     * @param topK           返回条数
     * @return 空列表
     */
    public List<HybridSearchResult> crossModalSearch(String query, String sourceModality, String targetModality, int topK) {
        log.info("[占位] 跨模态检索未实现 source={} target={} topK={}", sourceModality, targetModality, topK);
        return List.of();
    }
}
