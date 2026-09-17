package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.MultimodalKnowledgeProperties;
import com.ai.cs.knowledge.config.RagProperties;
import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.ai.cs.knowledge.mapper.MultimodalKnowledgeMapper;
import com.ai.cs.knowledge.util.LlmClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多模态知识库服务（占位）
 *
 * <p>TODO 后续实现：
 * <ul>
 *   <li>入库：把图片/音频/视频分析结果拼成向量文本，经 {@code EmbeddingModel} 向量化后写入
 *       对应向量库（{@code LangChainConfig.imageEmbeddingStore()} / {@code audioEmbeddingStore()}）；</li>
 *   <li>问答：按模态检索知识拼 Prompt 后经 {@code LlmClient} 生成图片/音频/视频/混合模态回答；</li>
 *   <li>视频内容分析：抽帧 + 视频理解模型汇总摘要。</li>
 * </ul>
 * </p>
 *
 * <p>当前占位：三个 {@code indexXxxKnowledge} **保留单表落库**（记录真实写入
 * {@code cs_multimodal_knowledge}），但向量化未实现，故 {@code status=0} 标记「未索引」、不参与检索；
 * 四个问答入口返回未实现文案；{@link #analyzeVideo} 返回空 Map。</p>
 *
 * <p>真实现部分（单表 CRUD 与简单统计）：按模态/标签检索、按资源或知识ID查询、列表、
 * {@link #getStatistics}、{@link #deleteKnowledge}、{@link #updateKnowledge}，
 * 以及父类 {@code ServiceImpl} 的 `getById` / `save`。</p>
 *
 * <p>本类不接入向量模型（未实现），仅依赖 Mapper、属性配置与 {@code LlmClient}。</p>
 */
@Slf4j
@Service
public class MultimodalKnowledgeService extends ServiceImpl<MultimodalKnowledgeMapper, MultimodalKnowledge> {

    /** 问答类占位统一文案 */
    private static final String QA_NOT_IMPLEMENTED = "多模态知识问答为占位实现，后端未接入大模型调用";

    private final MultimodalKnowledgeMapper knowledgeMapper;
    private final MultimodalKnowledgeProperties multimodalProperties;
    private final RagProperties ragProperties;
    private final LlmClient llmClient;

    public MultimodalKnowledgeService(MultimodalKnowledgeMapper knowledgeMapper,
                                      MultimodalKnowledgeProperties multimodalProperties,
                                      RagProperties ragProperties,
                                      LlmClient llmClient) {
        this.knowledgeMapper = knowledgeMapper;
        this.multimodalProperties = multimodalProperties;
        this.ragProperties = ragProperties;
        this.llmClient = llmClient;
    }

    // ========== 知识入库 ==========

    /**
     * 将图片分析结果入库（占位：仅落库，不向量化）
     *
     * <p>记录会真实写入 {@code cs_multimodal_knowledge}，但 {@code status=0}（未索引），
     * 因此不会被检索命中；恢复向量化后应置 1 并回填 {@code vectorCollection}。</p>
     *
     * @return 落库后的知识条目（status=0）
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
        knowledge.setStatus(0);

        log.warn("[占位] 图片知识向量化未实现，仅落库 status=0 resourceId={}", resourceId);
        knowledgeMapper.insert(knowledge);
        return knowledge;
    }

    /**
     * 将音频分析结果入库（占位：仅落库，不向量化）
     *
     * @return 落库后的知识条目（status=0）
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
        knowledge.setStatus(0);

        log.warn("[占位] 音频知识向量化未实现，仅落库 status=0 resourceId={}", resourceId);
        knowledgeMapper.insert(knowledge);
        return knowledge;
    }

    /**
     * 将视频分析结果入库（占位：仅落库，不向量化）
     *
     * @return 落库后的知识条目（status=0）
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
        knowledge.setStatus(0);

        log.warn("[占位] 视频知识向量化未实现，仅落库 status=0 resourceId={}", resourceId);
        knowledgeMapper.insert(knowledge);
        return knowledge;
    }

    // ========== 知识检索（单表 CRUD，真实现） ==========

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

    // ========== 多模态问答（占位） ==========

    /**
     * 图片知识库问答（占位：返回未实现文案）
     */
    public String imageQa(String question, String imageResourceId) {
        log.warn("[占位] 图片知识库问答未实现 imageResourceId={}", imageResourceId);
        return QA_NOT_IMPLEMENTED;
    }

    /**
     * 音频知识库问答（占位：返回未实现文案）
     */
    public String audioQa(String question, String audioResourceId) {
        log.warn("[占位] 音频知识库问答未实现 audioResourceId={}", audioResourceId);
        return QA_NOT_IMPLEMENTED;
    }

    /**
     * 视频知识库问答（占位：返回未实现文案）
     */
    public String videoQa(String question, String videoResourceId) {
        log.warn("[占位] 视频知识库问答未实现 videoResourceId={}", videoResourceId);
        return QA_NOT_IMPLEMENTED;
    }

    /**
     * 混合模态问答（占位：返回未实现文案）
     */
    public String mixedModalityQa(String question) {
        log.warn("[占位] 混合模态问答未实现");
        return QA_NOT_IMPLEMENTED;
    }

    // ========== 视频内容分析（占位） ==========

    /**
     * 分析视频内容生成摘要（占位：返回空 Map）
     *
     * <p>TODO 后续实现：FFmpeg 抽关键帧 → 多模态大模型逐帧理解 → 汇总摘要。</p>
     */
    public Map<String, Object> analyzeVideo(String videoPath, int frameInterval, int maxFrames) {
        log.warn("[占位] 视频内容分析未实现 videoPath={} frameInterval={} maxFrames={}",
                videoPath, frameInterval, maxFrames);
        return Map.of();
    }

    // ========== 统计功能（单表聚合，真实现） ==========

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
}
