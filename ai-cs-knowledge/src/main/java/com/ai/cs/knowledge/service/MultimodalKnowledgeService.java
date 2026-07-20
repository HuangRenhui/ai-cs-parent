package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.LangChainConfig;
import com.ai.cs.knowledge.config.MultimodalKnowledgeProperties;
import com.ai.cs.knowledge.config.RagProperties;
import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.ai.cs.knowledge.mapper.MultimodalKnowledgeMapper;
import com.ai.cs.knowledge.util.LlmClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多模态知识库服务
 * 提供图片知识库、音频知识库、视频知识库的管理和检索功能
 */
@Slf4j
@Service
public class MultimodalKnowledgeService extends ServiceImpl<MultimodalKnowledgeMapper, MultimodalKnowledge> {

    private final MultimodalKnowledgeMapper knowledgeMapper;
    private final MultimodalKnowledgeProperties multimodalProperties;
    private final EmbeddingModel embeddingModel;
    private final LangChainConfig langChainConfig;
    private final RagProperties ragProperties;
    private final LlmClient llmClient;

    public MultimodalKnowledgeService(MultimodalKnowledgeMapper knowledgeMapper,
                                       MultimodalKnowledgeProperties multimodalProperties,
                                       EmbeddingModel embeddingModel,
                                       LangChainConfig langChainConfig,
                                       RagProperties ragProperties,
                                       LlmClient llmClient) {
        this.knowledgeMapper = knowledgeMapper;
        this.multimodalProperties = multimodalProperties;
        this.embeddingModel = embeddingModel;
        this.langChainConfig = langChainConfig;
        this.ragProperties = ragProperties;
        this.llmClient = llmClient;
    }

    // ========== 知识入库 ==========

    /**
     * 将图片分析结果入库
     * @param resourceId 图片资源ID
     * @param title 标题
     * @param description 描述
     * @param analysis AI分析结果
     * @param keywords 关键词
     * @param tags 标签
     */
    @Transactional(rollbackFor = Exception.class)
    public MultimodalKnowledge indexImageKnowledge(String resourceId, String resourcePath,
                                                    String title, String description,
                                                    String analysis, String keywords, String tags) {
        MultimodalKnowledge knowledge = new MultimodalKnowledge();
        knowledge.setKnowledgeId(UUID.randomUUID().toString());
        knowledge.setModality("IMAGE");
        knowledge.setResourceId(resourceId);
        knowledge.setResourcePath(resourcePath);
        knowledge.setTitle(title);
        knowledge.setDescription(description);
        knowledge.setAnalysis(analysis);
        knowledge.setKeywords(keywords);
        knowledge.setTags(tags);
        knowledge.setStatus(1);

        // 向量化并存储
        try {
            String textForEmbedding = buildEmbeddingText(title, description, analysis, keywords);
            TextSegment segment = TextSegment.from(textForEmbedding);
            Response<Embedding> embeddingResp = embeddingModel.embed(segment);
            
            EmbeddingStore<TextSegment> store = langChainConfig.imageEmbeddingStore();
            store.add(embeddingResp.content(), segment);
            
            knowledge.setVectorCollection("image_embeddings");
            knowledge.setConfidence(0.8);
        } catch (Exception e) {
            log.error("图片知识向量化失败: resourceId={}", resourceId, e);
            knowledge.setStatus(0);
        }

        knowledgeMapper.insert(knowledge);
        log.info("图片知识入库完成: {}", title);
        return knowledge;
    }

    /**
     * 将音频分析结果入库
     */
    @Transactional(rollbackFor = Exception.class)
    public MultimodalKnowledge indexAudioKnowledge(String resourceId, String resourcePath,
                                                    String title, String description,
                                                    String analysis, String keywords, String tags) {
        MultimodalKnowledge knowledge = new MultimodalKnowledge();
        knowledge.setKnowledgeId(UUID.randomUUID().toString());
        knowledge.setModality("AUDIO");
        knowledge.setResourceId(resourceId);
        knowledge.setResourcePath(resourcePath);
        knowledge.setTitle(title);
        knowledge.setDescription(description);
        knowledge.setAnalysis(analysis);
        knowledge.setKeywords(keywords);
        knowledge.setTags(tags);
        knowledge.setStatus(1);

        try {
            String textForEmbedding = buildEmbeddingText(title, description, analysis, keywords);
            TextSegment segment = TextSegment.from(textForEmbedding);
            Response<Embedding> embeddingResp = embeddingModel.embed(segment);
            
            EmbeddingStore<TextSegment> store = langChainConfig.audioEmbeddingStore();
            store.add(embeddingResp.content(), segment);
            
            knowledge.setVectorCollection("audio_embeddings");
            knowledge.setConfidence(0.8);
        } catch (Exception e) {
            log.error("音频知识向量化失败: resourceId={}", resourceId, e);
            knowledge.setStatus(0);
        }

        knowledgeMapper.insert(knowledge);
        log.info("音频知识入库完成: {}", title);
        return knowledge;
    }

    /**
     * 将视频分析结果入库
     */
    @Transactional(rollbackFor = Exception.class)
    public MultimodalKnowledge indexVideoKnowledge(String resourceId, String resourcePath,
                                                    String title, String description,
                                                    String analysis, String keywords, String tags,
                                                    String entities) {
        MultimodalKnowledge knowledge = new MultimodalKnowledge();
        knowledge.setKnowledgeId(UUID.randomUUID().toString());
        knowledge.setModality("VIDEO");
        knowledge.setResourceId(resourceId);
        knowledge.setResourcePath(resourcePath);
        knowledge.setTitle(title);
        knowledge.setDescription(description);
        knowledge.setAnalysis(analysis);
        knowledge.setKeywords(keywords);
        knowledge.setTags(tags);
        knowledge.setEntities(entities);
        knowledge.setStatus(1);

        try {
            String textForEmbedding = buildEmbeddingText(title, description, analysis, keywords);
            TextSegment segment = TextSegment.from(textForEmbedding);
            Response<Embedding> embeddingResp = embeddingModel.embed(segment);
            
            // 视频知识也存入图片向量库（当前未单独创建视频向量库）
            EmbeddingStore<TextSegment> store = langChainConfig.imageEmbeddingStore();
            store.add(embeddingResp.content(), segment);
            
            knowledge.setVectorCollection("image_embeddings");
            knowledge.setConfidence(0.75);
        } catch (Exception e) {
            log.error("视频知识向量化失败: resourceId={}", resourceId, e);
            knowledge.setStatus(0);
        }

        knowledgeMapper.insert(knowledge);
        log.info("视频知识入库完成: {}", title);
        return knowledge;
    }

    // ========== 知识检索 ==========

    /**
     * 按模态检索知识
     */
    public List<MultimodalKnowledge> searchByModality(String modality, String query, int limit) {
        return knowledgeMapper.selectList(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getModality, modality)
                        .eq(MultimodalKnowledge::getStatus, 1)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
                        .and(w -> w.like(MultimodalKnowledge::getTitle, query)
                                .or().like(MultimodalKnowledge::getDescription, query)
                                .or().like(MultimodalKnowledge::getKeywords, query)
                                .or().like(MultimodalKnowledge::getTags, query))
                        .last("LIMIT " + limit)
        );
    }

    /**
     * 按标签检索知识
     */
    public List<MultimodalKnowledge> searchByTags(String tags, int limit) {
        return knowledgeMapper.selectList(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getStatus, 1)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
                        .like(MultimodalKnowledge::getTags, tags)
                        .last("LIMIT " + limit)
        );
    }

    /**
     * 获取指定资源的知识条目
     */
    public MultimodalKnowledge getByResourceId(String resourceId) {
        return knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getResourceId, resourceId)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
        );
    }

    /**
     * 获取所有知识条目（分页）
     */
    public List<MultimodalKnowledge> getAllKnowledge(String modality, int offset, int limit) {
        LambdaQueryWrapper<MultimodalKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MultimodalKnowledge::getStatus, 1)
                .eq(MultimodalKnowledge::getDelFlag, 0)
                .orderByDesc(MultimodalKnowledge::getCreateTime);
        if (modality != null && !modality.isEmpty()) {
            wrapper.eq(MultimodalKnowledge::getModality, modality);
        }
        wrapper.last("LIMIT " + offset + "," + limit);
        return knowledgeMapper.selectList(wrapper);
    }

    /**
     * 按模态类型列出所有知识条目
     */
    public List<MultimodalKnowledge> listByModality(String modality) {
        return knowledgeMapper.selectList(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getModality, modality)
                        .eq(MultimodalKnowledge::getStatus, 1)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
                        .orderByDesc(MultimodalKnowledge::getCreateTime)
        );
    }

    /**
     * 根据 knowledgeId 获取知识条目
     */
    public MultimodalKnowledge getByKnowledgeId(String knowledgeId) {
        return knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getKnowledgeId, knowledgeId)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
        );
    }

    // ========== 多模态问答 ==========

    /**
     * 图片知识库问答 - 基于图片分析结果回答用户问题
     */
    public String imageQa(String question, String imageResourceId) {
        try {
            List<MultimodalKnowledge> knowledgeList;
            
            if (imageResourceId != null && !imageResourceId.isEmpty()) {
                // 指定图片问答
                MultimodalKnowledge knowledge = getByResourceId(imageResourceId);
                knowledgeList = knowledge != null ? List.of(knowledge) : Collections.emptyList();
            } else {
                // 语义检索相关图片知识
                knowledgeList = searchByModality("IMAGE", question, 5);
            }

            if (knowledgeList.isEmpty()) {
                return "未找到相关图片知识，请先上传并分析图片。";
            }

            return buildQaPromptAndCall(question, knowledgeList, "图片");
        } catch (Exception e) {
            log.error("图片知识库问答失败", e);
            return "图片问答出错: " + e.getMessage();
        }
    }

    /**
     * 音频知识库问答
     */
    public String audioQa(String question, String audioResourceId) {
        try {
            List<MultimodalKnowledge> knowledgeList;

            if (audioResourceId != null && !audioResourceId.isEmpty()) {
                MultimodalKnowledge knowledge = getByResourceId(audioResourceId);
                knowledgeList = knowledge != null ? List.of(knowledge) : Collections.emptyList();
            } else {
                knowledgeList = searchByModality("AUDIO", question, 5);
            }

            if (knowledgeList.isEmpty()) {
                return "未找到相关音频知识，请先上传并分析音频。";
            }

            return buildQaPromptAndCall(question, knowledgeList, "音频");
        } catch (Exception e) {
            log.error("音频知识库问答失败", e);
            return "音频问答出错: " + e.getMessage();
        }
    }

    /**
     * 视频知识库检索
     */
    public String videoQa(String question, String videoResourceId) {
        try {
            List<MultimodalKnowledge> knowledgeList;

            if (videoResourceId != null && !videoResourceId.isEmpty()) {
                MultimodalKnowledge knowledge = getByResourceId(videoResourceId);
                knowledgeList = knowledge != null ? List.of(knowledge) : Collections.emptyList();
            } else {
                knowledgeList = searchByModality("VIDEO", question, 5);
            }

            if (knowledgeList.isEmpty()) {
                return "未找到相关视频知识，请先上传并分析视频。";
            }

            return buildQaPromptAndCall(question, knowledgeList, "视频");
        } catch (Exception e) {
            log.error("视频知识库问答失败", e);
            return "视频问答出错: " + e.getMessage();
        }
    }

    /**
     * 混合模态问答（图片+音频+文本综合检索）
     */
    public String mixedModalityQa(String question) {
        try {
            // 从所有模态中检索
            List<MultimodalKnowledge> allKnowledge = knowledgeMapper.selectList(
                    new LambdaQueryWrapper<MultimodalKnowledge>()
                            .eq(MultimodalKnowledge::getStatus, 1)
                            .eq(MultimodalKnowledge::getDelFlag, 0)
                            .and(w -> w.like(MultimodalKnowledge::getTitle, question)
                                    .or().like(MultimodalKnowledge::getDescription, question)
                                    .or().like(MultimodalKnowledge::getKeywords, question))
                            .last("LIMIT 10")
            );

            if (allKnowledge.isEmpty()) {
                return "未找到与问题相关的多模态知识。";
            }

            // 按模态分组
            Map<String, List<MultimodalKnowledge>> grouped = allKnowledge.stream()
                    .collect(Collectors.groupingBy(MultimodalKnowledge::getModality));

            StringBuilder context = new StringBuilder("多模态知识库综合检索结果:\n\n");
            for (Map.Entry<String, List<MultimodalKnowledge>> entry : grouped.entrySet()) {
                context.append(String.format("【%s知识】\n", getModalityChineseName(entry.getKey())));
                for (MultimodalKnowledge k : entry.getValue()) {
                    context.append(String.format("- %s: %s\n", k.getTitle(), k.getDescription()));
                    if (k.getAnalysis() != null && !k.getAnalysis().isEmpty()) {
                        context.append(String.format("  分析: %s\n", k.getAnalysis().substring(0, Math.min(200, k.getAnalysis().length()))));
                    }
                }
                context.append("\n");
            }

            String prompt = String.format(
                    "基于以下多模态知识库信息回答问题。请综合利用图片、音频、视频等多模态信息。\n\n%s\n问题: %s\n\n回答:",
                    context.toString(), question
            );

            return llmClient.call(prompt);
        } catch (Exception e) {
            log.error("混合模态问答失败", e);
            return "混合模态问答出错: " + e.getMessage();
        }
    }

    // ========== 视频内容分析 ==========

    /**
     * 分析视频内容生成摘要
     * 实际部署时需要集成视频分析模型（如 Video-LLaMA 等）
     */
    public Map<String, Object> analyzeVideo(String videoPath, int frameInterval, int maxFrames) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 框架：提取视频关键帧 → 用多模态大模型分析每帧 → 汇总生成摘要
            result.put("status", "success");
            result.put("message", "视频分析框架已就绪，需配置视频分析模型使用");
            result.put("videoPath", videoPath);
            result.put("frameInterval", frameInterval);
            result.put("maxFrames", maxFrames);
            result.put("frames", new ArrayList<>());

            // 实际集成时：
            // 1. 使用FFmpeg提取关键帧
            // 2. 调用多模态大模型分析每帧
            // 3. 汇总分析结果

        } catch (Exception e) {
            log.error("视频分析失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ========== 统计功能 ==========

    /**
     * 获取多模态知识库统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<MultimodalKnowledge> all = knowledgeMapper.selectList(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getDelFlag, 0)
        );

        // 按模态统计
        Map<String, Long> modalityStats = all.stream()
                .collect(Collectors.groupingBy(MultimodalKnowledge::getModality, Collectors.counting()));
        stats.put("byModality", modalityStats);

        // 总条目数
        stats.put("totalCount", all.size());

        // 已入库条目数
        long indexedCount = all.stream().filter(k -> k.getStatus() == 1).count();
        stats.put("indexedCount", indexedCount);

        // 热门标签
        Map<String, Long> tagFrequency = new HashMap<>();
        for (MultimodalKnowledge k : all) {
            if (k.getTags() != null) {
                for (String tag : k.getTags().split(",")) {
                    tag = tag.trim();
                    if (!tag.isEmpty()) {
                        tagFrequency.merge(tag, 1L, Long::sum);
                    }
                }
            }
        }
        List<Map.Entry<String, Long>> topTags = tagFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toList());
        stats.put("topTags", topTags);

        return stats;
    }

    /**
     * 删除知识条目
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteKnowledge(String knowledgeId) {
        return knowledgeMapper.delete(new LambdaQueryWrapper<MultimodalKnowledge>()
                .eq(MultimodalKnowledge::getKnowledgeId, knowledgeId)) > 0;
    }

    /**
     * 更新知识条目
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateKnowledge(MultimodalKnowledge knowledge) {
        return knowledgeMapper.updateById(knowledge) > 0;
    }

    // ========== 辅助方法 ==========

    private String buildEmbeddingText(String title, String description, String analysis, String keywords) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (analysis != null) sb.append(analysis).append(" ");
        if (keywords != null) sb.append(keywords);
        return sb.toString().trim();
    }

    private String buildQaPromptAndCall(String question, List<MultimodalKnowledge> knowledgeList, String modality) throws IOException {
        StringBuilder context = new StringBuilder(String.format("%s知识库相关内容:\n", modality));
        for (int i = 0; i < knowledgeList.size(); i++) {
            MultimodalKnowledge k = knowledgeList.get(i);
            context.append(String.format("%d. %s\n   描述: %s\n", i + 1, k.getTitle(), k.getDescription()));
            if (k.getAnalysis() != null && !k.getAnalysis().isEmpty()) {
                context.append(String.format("   分析: %s\n", k.getAnalysis()));
            }
            if (k.getKeywords() != null) {
                context.append(String.format("   关键词: %s\n", k.getKeywords()));
            }
        }

        String prompt = String.format(
                "基于以下%s知识库信息回答问题。\n\n%s\n\n问题: %s\n\n回答:",
                modality, context.toString(), question
        );

        return llmClient.call(prompt);
    }

    private String getModalityChineseName(String modality) {
        return switch (modality != null ? modality : "") {
            case "IMAGE" -> "图片";
            case "AUDIO" -> "音频";
            case "VIDEO" -> "视频";
            case "MIXED" -> "混合";
            default -> "未知";
        };
    }
}
