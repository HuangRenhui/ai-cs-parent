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
 * RAG检索增强生成服务
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

    // 构建带防幻觉约束的Prompt
    private String buildRagPrompt(String question, List<String> docs) {
        StringBuilder context = new StringBuilder();
        context.append("【参考真实业务文档】\n");
        docs.forEach(d -> context.append(d).append("\n"));
        // Prompt约束，禁止编造数据
        String rule = """
                严格遵守规则：
                1. 仅使用上面参考文档内容回答，禁止编造文档编号、接口、业务数据；
                2. 无相关信息直接回复「暂无相关资料」，禁止猜测。
                """;
        return context + rule + "\n用户问题：" + question;
    }

    // 完整语义检索+大模型问答
    public String semanticChat(String question) throws IOException {
        // 1. 问题向量化
        List<Float> vec = embeddingClient.getVector(question);
        // 2. Milvus语义检索真实文档
        List<String> relatedDocs = milvusUtil.search(vec, 3);
        // 3. 拼接约束Prompt
        String fullPrompt = buildRagPrompt(question, relatedDocs);
        // 4. 调用大模型生成答案
        return llmClient.call(fullPrompt);
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
