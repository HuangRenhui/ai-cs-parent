package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.LangChainConfig;
import com.ai.cs.knowledge.config.MultimodalKnowledgeProperties;
import com.ai.cs.knowledge.config.RagProperties;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.ai.cs.knowledge.mapper.MultimodalKnowledgeMapper;
import com.ai.cs.knowledge.util.EmbeddingClient;
import com.ai.cs.knowledge.util.LlmClient;
import com.ai.cs.knowledge.util.MilvusUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 融合文本向量检索（Milvus/Chroma）、关键词检索（BM25）、多模态向量检索
 * 实现真正的图文混合、音文混合、多模态混合检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingClient embeddingClient;
    private final MilvusUtil milvusUtil;
    private final RagSearchService ragSearchService;
    private final MultimodalKnowledgeMapper multimodalKnowledgeMapper;
    private final KnowledgeFaqService faqService;
    private final LangChainConfig langChainConfig;
    private final MultimodalKnowledgeProperties multimodalProperties;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;

    /**
     * 混合检索结果条目
     */
    public static class HybridSearchResult {
        public String id;
        public String content;
        public String modality;   // TEXT, IMAGE, AUDIO, VIDEO
        public String sourceType; // faq, document, image_knowledge, audio_knowledge
        public String sourceId;
        public String sourceTitle;
        public double score;
        public Map<String, Object> metadata;

        public HybridSearchResult(String id, String content, String modality, double score) {
            this.id = id;
            this.content = content;
            this.modality = modality;
            this.score = score;
            this.metadata = new HashMap<>();
        }
    }

    // ========== 混合检索核心方法 ==========

    /**
     * 混合多模态检索
     * @param query 查询文本
     * @param modalities 需要检索的模态列表: TEXT, IMAGE, AUDIO, VIDEO
     * @param topK 每种模态返回的结果数
     * @return 融合排序后的结果列表
     */
    public List<HybridSearchResult> hybridSearch(String query, List<String> modalities, int topK) {
        List<HybridSearchResult> allResults = new ArrayList<>();

        if (modalities == null || modalities.isEmpty()) {
            modalities = Arrays.asList("TEXT", "IMAGE", "AUDIO");
        }

        // 并行检索各模态
        for (String modality : modalities) {
            try {
                List<HybridSearchResult> modalityResults = switch (modality.toUpperCase()) {
                    case "TEXT" -> searchTextModality(query, topK);
                    case "IMAGE" -> searchImageModality(query, topK);
                    case "AUDIO" -> searchAudioModality(query, topK);
                    case "VIDEO" -> searchVideoModality(query, topK);
                    default -> Collections.emptyList();
                };
                allResults.addAll(modalityResults);
            } catch (Exception e) {
                log.warn("模态 {} 检索异常: {}", modality, e.getMessage());
            }
        }

        // 多模态结果融合排序
        return fusionSort(allResults, query);
    }

    /**
     * 图文混合问答
     * @param question 用户问题
     * @param includeImages 是否包含图片知识
     * @param includeAudios 是否包含音频知识
     * @return 综合回答
     */
    public String multimodalChat(String question, boolean includeImages, boolean includeAudios) {
        try {
            List<String> modalities = new ArrayList<>();
            modalities.add("TEXT");
            if (includeImages) modalities.add("IMAGE");
            if (includeAudios) modalities.add("AUDIO");

            List<HybridSearchResult> results = hybridSearch(question, modalities, 5);

            if (results.isEmpty()) {
                return "未找到与您问题相关的知识。";
            }

            // 构建综合Prompt
            StringBuilder context = new StringBuilder("【多模态知识库综合检索】\n\n");
            Map<String, List<HybridSearchResult>> grouped = results.stream()
                    .collect(Collectors.groupingBy(r -> r.modality));

            for (Map.Entry<String, List<HybridSearchResult>> entry : grouped.entrySet()) {
                String modalityName = getModalityName(entry.getKey());
                context.append(String.format("## %s知识\n", modalityName));
                for (HybridSearchResult r : entry.getValue()) {
                    context.append(String.format("- [来源:%s] %s\n", r.sourceTitle != null ? r.sourceTitle : "未知", r.content));
                }
                context.append("\n");
            }

            String prompt = String.format(
                    "基于以下多模态知识库信息回答用户问题。请综合运用文本、图片、音频等多种信息源。\n\n%s\n用户问题: %s\n\n回答:",
                    context.toString(), question
            );

            return llmClient.call(prompt);
        } catch (Exception e) {
            log.error("多模态混合问答失败", e);
            return "多模态问答出错: " + e.getMessage();
        }
    }

    /**
     * 跨模态语义搜索
     * 例如：用图片描述搜音频，或用音频描述搜图片
     */
    public List<HybridSearchResult> crossModalSearch(String query, String sourceModality, String targetModality, int topK) {
        log.info("跨模态搜索: {} -> {}", sourceModality, targetModality);

        // 先对查询文本做语义理解
        String enrichedQuery = enrichQueryWithModalityContext(query, sourceModality);

        // 在目标模态中检索
        return switch (targetModality.toUpperCase()) {
            case "TEXT" -> searchTextModality(enrichedQuery, topK);
            case "IMAGE" -> searchImageModality(enrichedQuery, topK);
            case "AUDIO" -> searchAudioModality(enrichedQuery, topK);
            case "VIDEO" -> searchVideoModality(enrichedQuery, topK);
            default -> Collections.emptyList();
        };
    }

    // ========== 单模态检索 ==========

    /**
     * 文本模态检索（FAQs + Chroma文档 + 关键词匹配）
     */
    private List<HybridSearchResult> searchTextModality(String query, int topK) {
        List<HybridSearchResult> results = new ArrayList<>();
        Set<String> seenContents = new HashSet<>();

        try {
            // 1. 向量检索 - Chroma文档知识库
            TextSegment querySegment = TextSegment.from(query);
            Response<Embedding> queryEmbedding = embeddingModel.embed(querySegment);

            EmbeddingStore<TextSegment> store = langChainConfig.embeddingStore();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding.content())
                    .maxResults(topK)
                    .minScore(ragProperties.getRetrieve().getMinScore())
                    .build();
            EmbeddingSearchResult<TextSegment> chromaResult = store.search(searchRequest);

            for (var match : chromaResult.matches()) {
                String content = match.embedded().text();
                if (content != null && !content.isEmpty() && seenContents.add(content)) {
                    HybridSearchResult r = new HybridSearchResult(
                            match.embeddingId(), content, "TEXT", match.score()
                    );
                    r.sourceType = "document";
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("Chroma文本检索失败: {}", e.getMessage());
        }

        try {
            // 2. 向量检索 - Milvus FAQ知识库
            List<Float> vec = embeddingClient.getVector(query);
            List<String> faqContents = milvusUtil.search(vec, topK);
            for (String content : faqContents) {
                if (content != null && !content.isEmpty() && seenContents.add("faq:" + content)) {
                    HybridSearchResult r = new HybridSearchResult(
                            "faq_" + UUID.randomUUID(), content, "TEXT", 0.8
                    );
                    r.sourceType = "faq";
                    results.add(r);
                }
            }
        } catch (IOException e) {
            log.warn("Milvus FAQ检索失败: {}", e.getMessage());
        }

        // 3. 关键词匹配补充
        try {
            List<KnowledgeFaq> faqList = faqService.getEnableFaqList();
            for (KnowledgeFaq faq : faqList) {
                if (results.size() >= topK * 2) break;
                String faqText = faq.getQuestion() + " " + (faq.getAnswer() != null ? faq.getAnswer() : "");
                if (containsKeywords(faqText, query) && seenContents.add("kw:" + faq.getId())) {
                    HybridSearchResult r = new HybridSearchResult(
                            "kw_faq_" + faq.getId(), faqText, "TEXT", 0.6
                    );
                    r.sourceType = "faq";
                    r.sourceId = String.valueOf(faq.getId());
                    r.sourceTitle = faq.getQuestion();
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("关键词匹配失败: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 图片模态检索
     */
    private List<HybridSearchResult> searchImageModality(String query, int topK) {
        List<HybridSearchResult> results = new ArrayList<>();

        try {
            // 向量检索图片知识
            TextSegment querySegment = TextSegment.from(query);
            Response<Embedding> queryEmbedding = embeddingModel.embed(querySegment);

            EmbeddingStore<TextSegment> store = langChainConfig.imageEmbeddingStore();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding.content())
                    .maxResults(topK)
                    .minScore(0.3)
                    .build();
            EmbeddingSearchResult<TextSegment> searchResult = store.search(searchRequest);

            for (var match : searchResult.matches()) {
                String content = match.embedded().text();
                if (content != null && !content.isEmpty()) {
                    HybridSearchResult r = new HybridSearchResult(
                            match.embeddingId(), content, "IMAGE", match.score()
                    );
                    r.sourceType = "image_knowledge";
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("图片向量检索失败: {}", e.getMessage());
        }

        // 关键词匹配补充
        try {
            List<MultimodalKnowledge> imageKnowledge = multimodalKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<MultimodalKnowledge>()
                            .eq(MultimodalKnowledge::getModality, "IMAGE")
                            .eq(MultimodalKnowledge::getStatus, 1)
                            .eq(MultimodalKnowledge::getDelFlag, 0)
            );

            for (MultimodalKnowledge mk : imageKnowledge) {
                if (results.size() >= topK * 2) break;
                String text = mk.getTitle() + " " + mk.getDescription() + " " + mk.getKeywords();
                if (containsKeywords(text, query)) {
                    HybridSearchResult r = new HybridSearchResult(
                            mk.getKnowledgeId(), mk.getDescription(), "IMAGE", 0.6
                    );
                    r.sourceType = "image_knowledge";
                    r.sourceId = mk.getResourceId();
                    r.sourceTitle = mk.getTitle();
                    r.metadata.put("resourcePath", mk.getResourcePath());
                    r.metadata.put("tags", mk.getTags());
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("图片关键词匹配失败: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 音频模态检索
     */
    private List<HybridSearchResult> searchAudioModality(String query, int topK) {
        List<HybridSearchResult> results = new ArrayList<>();

        try {
            TextSegment querySegment = TextSegment.from(query);
            Response<Embedding> queryEmbedding = embeddingModel.embed(querySegment);

            EmbeddingStore<TextSegment> store = langChainConfig.audioEmbeddingStore();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding.content())
                    .maxResults(topK)
                    .minScore(0.3)
                    .build();
            EmbeddingSearchResult<TextSegment> searchResult = store.search(searchRequest);

            for (var match : searchResult.matches()) {
                String content = match.embedded().text();
                if (content != null && !content.isEmpty()) {
                    HybridSearchResult r = new HybridSearchResult(
                            match.embeddingId(), content, "AUDIO", match.score()
                    );
                    r.sourceType = "audio_knowledge";
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("音频向量检索失败: {}", e.getMessage());
        }

        // 关键词匹配补充
        try {
            List<MultimodalKnowledge> audioKnowledge = multimodalKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<MultimodalKnowledge>()
                            .eq(MultimodalKnowledge::getModality, "AUDIO")
                            .eq(MultimodalKnowledge::getStatus, 1)
                            .eq(MultimodalKnowledge::getDelFlag, 0)
            );

            for (MultimodalKnowledge mk : audioKnowledge) {
                if (results.size() >= topK * 2) break;
                String text = mk.getTitle() + " " + mk.getDescription() + " " + mk.getKeywords();
                if (containsKeywords(text, query)) {
                    HybridSearchResult r = new HybridSearchResult(
                            mk.getKnowledgeId(), mk.getDescription(), "AUDIO", 0.6
                    );
                    r.sourceType = "audio_knowledge";
                    r.sourceId = mk.getResourceId();
                    r.sourceTitle = mk.getTitle();
                    r.metadata.put("resourcePath", mk.getResourcePath());
                    r.metadata.put("tags", mk.getTags());
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("音频关键词匹配失败: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 视频模态检索
     */
    private List<HybridSearchResult> searchVideoModality(String query, int topK) {
        List<HybridSearchResult> results = new ArrayList<>();

        try {
            List<MultimodalKnowledge> videoKnowledge = multimodalKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<MultimodalKnowledge>()
                            .eq(MultimodalKnowledge::getModality, "VIDEO")
                            .eq(MultimodalKnowledge::getStatus, 1)
                            .eq(MultimodalKnowledge::getDelFlag, 0)
            );

            for (MultimodalKnowledge mk : videoKnowledge) {
                if (results.size() >= topK) break;
                String text = mk.getTitle() + " " + mk.getDescription() + " " + mk.getKeywords();
                if (containsKeywords(text, query)) {
                    HybridSearchResult r = new HybridSearchResult(
                            mk.getKnowledgeId(), mk.getDescription(), "VIDEO", 0.55
                    );
                    r.sourceType = "video_knowledge";
                    r.sourceId = mk.getResourceId();
                    r.sourceTitle = mk.getTitle();
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("视频检索失败: {}", e.getMessage());
        }

        return results;
    }

    // ========== 融合排序 ==========

    /**
     * 多模态结果融合排序
     * 使用RRF (Reciprocal Rank Fusion) 算法
     */
    private List<HybridSearchResult> fusionSort(List<HybridSearchResult> results, String query) {
        if (results.isEmpty()) return results;

        String fusionMethod = multimodalProperties.getHybridSearch().getFusionMethod();
        int rrfK = multimodalProperties.getHybridSearch().getRrfK();

        return switch (fusionMethod) {
            case "weighted" -> weightedFusion(results);
            case "linear" -> linearFusion(results);
            default -> rrfFusion(results, rrfK); // 默认使用RRF
        };
    }

    /**
     * RRF (Reciprocal Rank Fusion) 融合算法
     */
    private List<HybridSearchResult> rrfFusion(List<HybridSearchResult> results, int k) {
        // 按模态分组并分别排序
        Map<String, List<HybridSearchResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(r -> r.modality));

        // 为每个结果计算RRF分数
        Map<String, HybridSearchResult> bestByContent = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        for (List<HybridSearchResult> modalityResults : grouped.values()) {
            modalityResults.sort((a, b) -> Double.compare(b.score, a.score));
            for (int rank = 0; rank < modalityResults.size(); rank++) {
                HybridSearchResult r = modalityResults.get(rank);
                String key = r.id;
                double rrfScore = 1.0 / (k + rank + 1);
                rrfScores.merge(key, rrfScore, Double::sum);
                if (!bestByContent.containsKey(key) || r.score > bestByContent.get(key).score) {
                    bestByContent.put(key, r);
                }
            }
        }

        // 按RRF分数重新排序
        List<HybridSearchResult> fused = new ArrayList<>();
        for (HybridSearchResult r : bestByContent.values()) {
            r.score = rrfScores.getOrDefault(r.id, 0.0);
            fused.add(r);
        }

        fused.sort((a, b) -> Double.compare(b.score, a.score));
        return fused;
    }

    /**
     * 加权融合
     */
    private List<HybridSearchResult> weightedFusion(List<HybridSearchResult> results) {
        var props = multimodalProperties.getHybridSearch();
        for (HybridSearchResult r : results) {
            double weight = switch (r.modality) {
                case "TEXT" -> props.getTextWeight();
                case "IMAGE" -> props.getImageWeight();
                case "AUDIO" -> props.getAudioWeight();
                case "VIDEO" -> props.getVideoWeight();
                default -> 1.0;
            };
            r.score = r.score * weight;
        }
        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results;
    }

    /**
     * 线性融合
     */
    private List<HybridSearchResult> linearFusion(List<HybridSearchResult> results) {
        // 归一化分数后直接排序
        double maxScore = results.stream().mapToDouble(r -> r.score).max().orElse(1.0);
        double minScore = results.stream().mapToDouble(r -> r.score).min().orElse(0.0);
        double range = maxScore - minScore;

        for (HybridSearchResult r : results) {
            if (range > 0) {
                r.score = (r.score - minScore) / range;
            } else {
                r.score = 1.0;
            }
        }
        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results;
    }

    // ========== 辅助方法 ==========

    /**
     * 用模态上下文丰富查询
     */
    private String enrichQueryWithModalityContext(String query, String modality) {
        // 简单的查询扩展：根据源模态添加相关上下文
        String modalityName = getModalityName(modality);
        return String.format("从%s角度理解: %s", modalityName, query);
    }

    /**
     * 简单关键词匹配
     */
    private boolean containsKeywords(String text, String query) {
        if (text == null || query == null) return false;
        String[] keywords = query.split("[，,。.!！?？\\s]+");
        for (String keyword : keywords) {
            if (keyword.length() >= 2 && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String getModalityName(String modality) {
        return switch (modality != null ? modality.toUpperCase() : "") {
            case "TEXT" -> "文本";
            case "IMAGE" -> "图片";
            case "AUDIO" -> "音频";
            case "VIDEO" -> "视频";
            default -> "未知";
        };
    }
}
