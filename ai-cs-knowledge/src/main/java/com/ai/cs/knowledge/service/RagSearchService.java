package com.ai.cs.knowledge.service;

import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.util.EmbeddingClient;
import com.ai.cs.knowledge.util.LlmClient;
import com.ai.cs.knowledge.util.MilvusUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * RAG 检索服务（占位）
 *
 * <p>TODO 后续实现：
 * <ul>
 *   <li>{@link #semanticSearch}：问题向量化 → Milvus 相似检索（可按租户隔离集合）→ 命中 FAQ 列表；
 *       经 {@code RetrievalOptimizerService} 做质量/相似度/BM25 过滤、召回限制与上下文压缩，
 *       再用 {@code PromptTemplateService} 组 Prompt 调模型生成回答；结果按
 *       {@code RagSearchResultDTO} 三态返回（命中带引用 / 未命中 / 服务不可用），
 *       未命中时经 {@code KnowledgeMissService} 记录未命中问题，可选 {@code industryPrompt} 做行业人设与拒答叠加；</li>
 *   <li>{@link #semanticChat}：不带引用的简版语义问答（集成过滤/召回限制/上下文压缩/Prompt 增强）；</li>
 *   <li>{@link #vectorizeAndInsert} / {@link #incrementUpdate} / {@link #batchIncrementUpdate} /
 *       {@link #batchVectorizeAndInsert}：FAQ 的向量化入库与增量更新（内容拼接为「分类+问+答」后写入向量库）。</li>
 * </ul>
 * </p>
 *
 * <p>当前不检索、不调用模型：两个检索方法返回 UNAVAILABLE（错误说明为占位实现），
 * 需要向量化的方法抛 {@link IOException}，批量方法返回 0。</p>
 */
@Slf4j
@Service
public class RagSearchService {

    @Resource
    private MilvusUtil milvusUtil;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private LlmClient llmClient;

    @Resource
    private RetrievalOptimizerService retrievalOptimizer;

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource
    private KnowledgeFaqService faqService;

    @Resource
    private KnowledgeMissService missService;

    /**
     * 语义检索（占位：返回服务不可用）
     *
     * @param question   用户问题
     * @param tenantCode 租户编码
     * @param sessionId  会话 ID
     * @return UNAVAILABLE 结果
     */
    public RagSearchResultDTO semanticSearch(String question, String tenantCode, String sessionId) {
        return semanticSearch(question, tenantCode, sessionId, null);
    }

    /**
     * 语义检索（占位：返回服务不可用）
     *
     * @param industryPrompt 行业人设/拒答，可空
     * @return UNAVAILABLE 结果
     */
    public RagSearchResultDTO semanticSearch(String question, String tenantCode, String sessionId, String industryPrompt) {
        log.warn("[占位] RAG 语义检索未实现 tenant={} session={}", tenantCode, sessionId);
        return RagSearchResultDTO.unavailable("RAG 语义检索为占位实现，后端未接入向量检索与模型生成");
    }

    /**
     * 语义问答（占位：抛 IOException）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String semanticChat(String question) throws IOException {
        log.warn("[占位] RAG 语义问答未实现");
        throw new IOException("RAG 问答为占位实现");
    }

    /**
     * FAQ 向量化入库（占位：抛 IOException）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String vectorizeAndInsert(KnowledgeFaq faq) throws IOException {
        log.warn("[占位] FAQ 向量化入库未实现");
        throw new IOException("FAQ 向量化入库为占位实现");
    }

    /**
     * FAQ 增量向量更新（占位：抛 IOException）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String incrementUpdate(KnowledgeFaq faq) throws IOException {
        log.warn("[占位] FAQ 增量向量更新未实现");
        throw new IOException("FAQ 增量向量更新为占位实现");
    }

    /**
     * FAQ 批量增量更新（占位：返回 0）
     *
     * @return 0
     */
    public int batchIncrementUpdate(List<KnowledgeFaq> faqList) {
        log.warn("[占位] FAQ 批量增量更新未实现，返回 0");
        return 0;
    }

    /**
     * FAQ 批量向量化入库（占位：返回 0）
     *
     * @return 0
     */
    public int batchVectorizeAndInsert(List<KnowledgeFaq> faqList) {
        log.warn("[占位] FAQ 批量向量化入库未实现，返回 0");
        return 0;
    }
}
