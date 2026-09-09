package com.ai.cs.knowledge.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多模态检索服务
 * 支持图文混合检索、跨模态语义搜索
 * 将不同模态（图片、音频、文本）的结果融合排序
 */
@Slf4j
@Service
public class MultimodalSearchService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> imageEmbeddingStore;
    private final EmbeddingStore<TextSegment> audioEmbeddingStore;
    private final EmbeddingStore<TextSegment> knowledgeEmbeddingStore;

    public MultimodalSearchService(
            EmbeddingModel embeddingModel,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageEmbeddingStore,
            @Qualifier("audioEmbeddingStore") EmbeddingStore<TextSegment> audioEmbeddingStore,
            @Qualifier("embeddingStore") EmbeddingStore<TextSegment> knowledgeEmbeddingStore) {
        this.embeddingModel = embeddingModel;
        this.imageEmbeddingStore = imageEmbeddingStore;
        this.audioEmbeddingStore = audioEmbeddingStore;
        this.knowledgeEmbeddingStore = knowledgeEmbeddingStore;
    }

    /**
     * 多模态混合检索
     * 同时在图片库、音频库、知识库中搜索，融合结果
     *
     * @param query 搜索查询文本
     * @param modalities 要搜索的模态列表: image, audio, text
     * @param maxResults 每种模态最大返回结果数
     * @return 融合排序后的搜索结果
     */
    public MultimodalSearchResult multimodalSearch(String query, List<String> modalities, int maxResults) {
        long startTime = System.currentTimeMillis();
        MultimodalSearchResult result = new MultimodalSearchResult();
        result.setQuery(query);
        result.setModalities(modalities);

        // 将查询文本向量化
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<ModalHit> allHits = new ArrayList<>();

        // 图片模态搜索
        if (modalities.contains("image")) {
            try {
                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(0.4)
                        .build();
                EmbeddingSearchResult<TextSegment> imageResult = imageEmbeddingStore.search(request);
                for (var match : imageResult.matches()) {
                    ModalHit hit = new ModalHit();
                    hit.setModality("image");
                    hit.setScore(match.score());
                    hit.setContent(match.embedded().text());
                    hit.setMetadata(mapFromLangChain4j(match.embedded().metadata()));
                    allHits.add(hit);
                }
                log.debug("图片模态搜索完成: {}条结果", imageResult.matches().size());
            } catch (Exception e) {
                log.warn("图片模态搜索失败: {}", e.getMessage());
            }
        }

        // 音频模态搜索
        if (modalities.contains("audio")) {
            try {
                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(0.4)
                        .build();
                EmbeddingSearchResult<TextSegment> audioResult = audioEmbeddingStore.search(request);
                for (var match : audioResult.matches()) {
                    ModalHit hit = new ModalHit();
                    hit.setModality("audio");
                    hit.setScore(match.score());
                    hit.setContent(match.embedded().text());
                    hit.setMetadata(mapFromLangChain4j(match.embedded().metadata()));
                    allHits.add(hit);
                }
                log.debug("音频模态搜索完成: {}条结果", audioResult.matches().size());
            } catch (Exception e) {
                log.warn("音频模态搜索失败: {}", e.getMessage());
            }
        }

        // 文本/知识库模态搜索
        if (modalities.contains("text")) {
            try {
                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(0.4)
                        .build();
                EmbeddingSearchResult<TextSegment> textResult = knowledgeEmbeddingStore.search(request);
                for (var match : textResult.matches()) {
                    ModalHit hit = new ModalHit();
                    hit.setModality("text");
                    hit.setScore(match.score());
                    hit.setContent(match.embedded().text());
                    hit.setMetadata(mapFromLangChain4j(match.embedded().metadata()));
                    allHits.add(hit);
                }
                log.debug("文本模态搜索完成: {}条结果", textResult.matches().size());
            } catch (Exception e) {
                log.warn("文本模态搜索失败: {}", e.getMessage());
            }
        }

        // 融合排序：按分数降序排列
        allHits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 按模态分组统计
        Map<String, Long> modalityCount = allHits.stream()
                .collect(Collectors.groupingBy(ModalHit::getModality, Collectors.counting()));

        result.setTotalHits(allHits.size());
        result.setHits(allHits);
        result.setModalityCount(modalityCount);
        result.setDuration(System.currentTimeMillis() - startTime);

        log.info("多模态检索完成: query='{}', 总命中数={}, 模态分布={}, 耗时={}ms",
                query, allHits.size(), modalityCount, result.getDuration());

        return result;
    }

    /**
     * 跨模态检索：用图片描述搜音频，或用音频描述搜图片
     */
    public List<ModalHit> crossModalSearch(String query, String sourceModality, String targetModality, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<ModalHit> hits = new ArrayList<>();

        EmbeddingStore<TextSegment> targetStore;
        switch (targetModality.toLowerCase()) {
            case "image":
                targetStore = imageEmbeddingStore;
                break;
            case "audio":
                targetStore = audioEmbeddingStore;
                break;
            case "text":
                targetStore = knowledgeEmbeddingStore;
                break;
            default:
                throw new IllegalArgumentException("不支持的目标模态: " + targetModality);
        }

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(0.4)
                .build();

        EmbeddingSearchResult<TextSegment> result = targetStore.search(request);
        for (var match : result.matches()) {
            ModalHit hit = new ModalHit();
            hit.setModality(targetModality);
            hit.setScore(match.score());
            hit.setContent(match.embedded().text());
            hit.setMetadata(mapFromLangChain4j(match.embedded().metadata()));
            hits.add(hit);
        }

        log.info("跨模态检索完成: {}->{}, 结果数={}", sourceModality, targetModality, hits.size());
        return hits;
    }

    /**
     * 将LangChain4j的Metadata转换为Map
     */
    private Map<String, String> mapFromLangChain4j(dev.langchain4j.data.document.Metadata metadata) {
        Map<String, String> map = new HashMap<>();
        if (metadata != null) {
            metadata.asMap().forEach((k, v) -> map.put(k, v != null ? v.toString() : ""));
        }
        return map;
    }

    /**
     * 多模态搜索结果
     */
    public static class MultimodalSearchResult {
        /** 原始查询文本 */
        private String query;
        /** 本次检索覆盖的模态列表（image/audio/text） */
        private List<String> modalities;
        /** 总命中数 */
        private int totalHits;
        /** 融合排序后的命中列表 */
        private List<ModalHit> hits;
        /** 各模态命中数统计 */
        private Map<String, Long> modalityCount;
        /** 检索耗时（毫秒） */
        private long duration;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public List<String> getModalities() { return modalities; }
        public void setModalities(List<String> modalities) { this.modalities = modalities; }
        public int getTotalHits() { return totalHits; }
        public void setTotalHits(int totalHits) { this.totalHits = totalHits; }
        public List<ModalHit> getHits() { return hits; }
        public void setHits(List<ModalHit> hits) { this.hits = hits; }
        public Map<String, Long> getModalityCount() { return modalityCount; }
        public void setModalityCount(Map<String, Long> modalityCount) { this.modalityCount = modalityCount; }
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
    }

    /**
     * 模态命中结果
     */
    public static class ModalHit {
        /** 命中所属模态（image/audio/text） */
        private String modality;
        /** 相似度分数 */
        private double score;
        /** 命中的文本内容（图片描述/音频描述/文档片段） */
        private String content;
        /** 命中条目的元数据（fileId、存储路径等） */
        private Map<String, String> metadata;

        public String getModality() { return modality; }
        public void setModality(String modality) { this.modality = modality; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }
}
