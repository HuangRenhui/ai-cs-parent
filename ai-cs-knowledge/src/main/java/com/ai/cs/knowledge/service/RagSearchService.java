package com.ai.cs.knowledge.service;

import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.RagCitationDTO;
import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.util.EmbeddingClient;
import com.ai.cs.knowledge.util.LlmClient;
import com.ai.cs.knowledge.util.MilvusHit;
import com.ai.cs.knowledge.util.MilvusUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    @Resource
    private KnowledgeFaqService faqService;

    @Resource
    private KnowledgeMissService missService;

    /**
     * 租户隔离语义检索：命中/未命中/不可用 + 引用回传
     * 流程：问题向量化 → Milvus租户集合检索 → 阈值/状态/租户三重过滤 → LLM组织答案（失败降级为FAQ原文）
     * @param question 用户问题
     * @param tenantCode 租户编码（空时归一化为default）
     * @param sessionId 会话ID（用于未命中记录追溯）
     * @return 检索结果（hit带引用 / miss / unavailable三态）
     */
    public RagSearchResultDTO semanticSearch(String question, String tenantCode, String sessionId) {
        String tenant = KnowledgeFaqService.normalizeTenant(tenantCode);
        if (!StringUtils.hasText(question)) {
            return RagSearchResultDTO.miss(PromptConst.NO_KNOWLEDGE_REPLY);
        }
        // 前置检查：向量库未就绪时直接返回不可用，避免后续调用空转
        if (!milvusUtil.isReady()) {
            return RagSearchResultDTO.unavailable("向量库不可用: " + milvusUtil.statusMessage());
        }
        // 第一步：问题向量化（外部Embedding服务调用，失败视为服务不可用）
        List<Float> vector;
        try {
            vector = embeddingClient.getVector(question.trim());
        } catch (Exception e) {
            log.warn("Embedding 失败: {}", e.getMessage());
            return RagSearchResultDTO.unavailable("Embedding 不可用: " + e.getMessage());
        }
        // 第二步：在租户隔离集合中做向量相似度检索
        List<MilvusHit> hits;
        try {
            hits = milvusUtil.search(tenant, vector, milvusUtil.topK());
        } catch (Exception e) {
            log.warn("租户 Milvus 检索失败: {}", e.getMessage());
            return RagSearchResultDTO.unavailable("向量检索失败: " + e.getMessage());
        }
        // 第三步：三重过滤——相似度阈值、FAQ启用状态、租户一致性（防串租）
        float threshold = milvusUtil.scoreThreshold();
        List<KnowledgeFaq> faqs = new ArrayList<>();
        List<RagCitationDTO> citations = new ArrayList<>();
        Float bestBelow = null;
        for (MilvusHit hit : hits) {
            if (hit.getScore() < threshold) {
                // 记录阈值以下的最高分，用于未命中分析（判断阈值是否设置过高）
                if (bestBelow == null || hit.getScore() > bestBelow) {
                    bestBelow = hit.getScore();
                }
                continue;
            }
            KnowledgeFaq faq = faqService.getById(hit.getFaqId());
            if (faq == null || faq.getStatus() == null || faq.getStatus() != 1) {
                continue;
            }
            // 二次校验租户，防止向量库中残留的其他租户数据串租
            if (!tenant.equals(KnowledgeFaqService.normalizeTenant(faq.getTenantCode()))) {
                continue;
            }
            faqs.add(faq);
            RagCitationDTO citation = new RagCitationDTO();
            citation.setFaqId(faq.getId());
            citation.setQuestion(faq.getQuestion());
            citation.setCategory(faq.getCategory());
            // 分数保留4位小数，便于前端展示
            citation.setScore(Math.round(hit.getScore() * 10000f) / 10000f);
            citations.add(citation);
        }
        // 未命中：记录知识盲区（携带最高相似度供运营参考），返回兜底话术
        if (faqs.isEmpty()) {
            missService.record(tenant, question, sessionId, bestBelow == null && !hits.isEmpty()
                    ? Math.round(hits.get(0).getScore() * 10000f) / 10000f
                    : (bestBelow == null ? null : Math.round(bestBelow * 10000f) / 10000f));
            return RagSearchResultDTO.miss(PromptConst.NO_KNOWLEDGE_REPLY);
        }
        // 第四步：LLM基于FAQ上下文组织自然语言答案
        String reply;
        try {
            reply = llmClient.call(PromptConst.fill(PromptConst.RAG_PROMPT, buildFaqContext(faqs), question));
            if (!StringUtils.hasText(reply) || reply.contains(PromptConst.NO_KNOWLEDGE_REPLY)) {
                // LLM返回空或兜底话术时，降级为相似度最高的FAQ原文答案
                reply = faqs.get(0).getAnswer();
            }
        } catch (Exception e) {
            // LLM调用失败降级：直接返回首条FAQ原文，保证用户能拿到答案
            log.warn("RAG 生成失败，回退为首条 FAQ 原文: {}", e.getMessage());
            reply = faqs.get(0).getAnswer();
        }
        return RagSearchResultDTO.hit(reply, citations);
    }

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
        // 2. 插入旧集合（兼容存量检索）
        String milvusId = milvusUtil.insert(vector, faq.getQuestion());
        // 3. 同步写入租户隔离集合
        if (faq.getId() != null) {
            try {
                milvusUtil.upsertTenant(faq.getId(), faq.getTenantCode(), vector, buildFaqContent(faq));
            } catch (Exception e) {
                log.warn("租户向量写入失败 faqId={}: {}", faq.getId(), e.getMessage());
            }
        }
        return milvusId;
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

    /** 将命中的FAQ列表拼接为编号上下文文本，供RAG Prompt引用 */
    private String buildFaqContext(List<KnowledgeFaq> faqs) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < faqs.size(); i++) {
            context.append(i + 1).append(". ").append(buildFaqContent(faqs.get(i))).append("\n");
        }
        return context.toString();
    }

    /** 拼接单条FAQ的展示内容（分类+问+答），用于向量库存储与Prompt上下文 */
    private String buildFaqContent(KnowledgeFaq faq) {
        String category = StringUtils.hasText(faq.getCategory()) ? faq.getCategory() : "未分类";
        return "【分类】" + category + "\n【问】" + faq.getQuestion() + "\n【答】" + faq.getAnswer();
    }
}
