package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.util.EmbeddingClient;
import com.ai.cs.knowledge.util.LlmClient;
import com.ai.cs.knowledge.util.MilvusUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG检索增强生成服务（优化版）
 * 集成文档质量过滤、相似度阈值过滤、上下文压缩、Prompt模板增强等优化
 *
 * @author huangrenhui
 * @date 2026/6/18 00:17
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

    /**
     * 完整语义检索+大模型问答（优化版）
     * 集成质量过滤、召回限制、上下文压缩、Prompt模板增强
     */
    public String semanticChat(String question) throws IOException {
        // 1. 问题向量化
        List<Float> vec = embeddingClient.getVector(question);
        // 2. Milvus语义检索真实文档（召回更多候选，后续过滤）
        List<String> rawDocs = milvusUtil.search(vec, 10);

        // 3. 转换为 RetrievalItem 进行质量过滤
        List<RetrievalOptimizerService.RetrievalItem> items = new ArrayList<>();
        for (int i = 0; i < rawDocs.size(); i++) {
            String doc = rawDocs.get(i);
            if (doc == null || doc.isBlank()) continue;
            double score = 1.0 - (i * 0.05); // 向量检索排名越前分数越高
            items.add(new RetrievalOptimizerService.RetrievalItem("doc_" + i, doc, score));
        }

        // 4. 质量过滤 + 相似度过滤 + 数量限制 + 上下文压缩
        items = retrievalOptimizer.filterByQuality(items);
        items = retrievalOptimizer.filterBySimilarity(items);
        items = retrievalOptimizer.limitRecallCount(items);
        items = retrievalOptimizer.compressContext(items);

        // 5. 提取最终文档内容
        List<String> relatedDocs = items.stream()
                .map(item -> item.content)
                .toList();

        // 6. 使用 PromptTemplateService 构建增强 Prompt（角色+格式+CoT+Few-shot+边界+上下文）
        String systemPrompt = promptTemplateService.buildSystemPrompt();
        String userPrompt = promptTemplateService.buildUserPrompt(question, relatedDocs);

        // 7. 调用大模型生成答案（支持 system + user 双消息）
        return llmClient.callWithSystem(systemPrompt, userPrompt);
    }

    /**
     * 单个FAQ向量化并插入Milvus
     * @param faq FAQ对象
     * @return Milvus中的记录ID
     */
    public String vectorizeAndInsert(KnowledgeFaq faq) throws IOException {
        // 1. 将问题文本转换为向量
        List<Float> vector = embeddingClient.getVector(faq.getQuestion());
        // 2. 插入Milvus
        return milvusUtil.insert(vector, faq.getQuestion());
    }

    /**
     * 增量更新单个FAQ的向量数据
     * @param faq FAQ对象（需包含milvusId）
     * @return 新的Milvus记录ID
     */
    public String incrementUpdate(KnowledgeFaq faq) throws IOException {
        // 1. 如果已有Milvus ID，先删除旧向量
        if (faq.getMilvusId() != null && !faq.getMilvusId().isEmpty()) {
            boolean deleted = milvusUtil.deleteById(faq.getMilvusId());
            if (!deleted) {
                log.warn("删除旧向量失败, milvusId={}", faq.getMilvusId());
            }
        }
        
        // 2. 重新向量化并插入
        List<Float> vector = embeddingClient.getVector(faq.getQuestion());
        String newMilvusId = milvusUtil.insert(vector, faq.getQuestion());
        
        return newMilvusId;
    }

    /**
     * 批量增量更新FAQ的向量数据
     * @param faqList FAQ列表（需包含milvusId）
     * @return 成功更新的数量
     */
    public int batchIncrementUpdate(List<KnowledgeFaq> faqList) {
        if (faqList == null || faqList.isEmpty()) {
            return 0;
        }

        // 收集需要删除的Milvus ID
        List<String> deleteIds = new ArrayList<>();
        for (KnowledgeFaq faq : faqList) {
            if (faq.getMilvusId() != null && !faq.getMilvusId().isEmpty()) {
                deleteIds.add(faq.getMilvusId());
            }
        }

        // 批量删除旧向量
        if (!deleteIds.isEmpty()) {
            int deletedCount = milvusUtil.batchDelete(deleteIds);
            log.info("批量删除旧向量: {} 条", deletedCount);
        }

        // 批量向量化并插入新数据
        List<List<Float>> vectors = new ArrayList<>();
        List<String> contents = new ArrayList<>();

        for (KnowledgeFaq faq : faqList) {
            try {
                List<Float> vector = embeddingClient.getVector(faq.getQuestion());
                vectors.add(vector);
                contents.add(faq.getQuestion());
            } catch (Exception e) {
                // 记录错误但继续处理其他FAQ
                log.warn("FAQ ID {} 向量化失败: {}", faq.getId(), e.getMessage());
            }
        }

        if (vectors.isEmpty()) {
            return 0;
        }

        // 批量插入Milvus
        List<String> milvusIds = milvusUtil.batchInsert(vectors, contents);
        return milvusIds.size();
    }

    /**
     * 批量FAQ向量化并插入Milvus
     * @param faqList FAQ列表
     * @return 成功插入的数量
     */
    public int batchVectorizeAndInsert(List<KnowledgeFaq> faqList) {
        if (faqList == null || faqList.isEmpty()) {
            return 0;
        }

        // 批量向量化
        List<List<Float>> vectors = new ArrayList<>();
        List<String> contents = new ArrayList<>();

        for (KnowledgeFaq faq : faqList) {
            try {
                List<Float> vector = embeddingClient.getVector(faq.getQuestion());
                vectors.add(vector);
                contents.add(faq.getQuestion());
            } catch (Exception e) {
                // 记录错误但继续处理其他FAQ
                log.warn("FAQ ID {} 向量化失败: {}", faq.getId(), e.getMessage());
            }
        }

        if (vectors.isEmpty()) {
            return 0;
        }

        // 批量插入Milvus
        List<String> milvusIds = milvusUtil.batchInsert(vectors, contents);
        return milvusIds.size();
    }
}
