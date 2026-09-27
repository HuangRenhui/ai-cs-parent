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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索服务（FAQ 检索 + 生成）
 *
 * <p>检索链路：问题向量化（{@link EmbeddingClient} → {@code ModelRouter.embed}）→ 租户 FAQ 集合相似检索
 * （{@link MilvusUtil#search}，阈值与 TopK 在向量层过滤）→ <b>按 faqId 回查数据库</b>只保留
 * 「启用 + 已发布」的 FAQ（逻辑删除由 {@code @TableLogic} 自动过滤，因此删除失败遗留的脏向量不会答出）
 * → {@link PromptTemplateService} 组 Prompt → {@link LlmClient} 生成回答 → 三态返回
 * （HIT 带引用 / MISS 兜底话术并记录未命中 / UNAVAILABLE 服务不可用）。</p>
 *
 * <p>口径说明：本类不调用 {@code RetrievalOptimizerService}——该类的过滤/压缩方法仍为占位
 * （返回空列表），接入会把上下文清空；召回质量目前由 {@code MilvusUtil} 的
 * {@code scoreThreshold} 与 {@code topK} 承担，等优化器落地后再接入。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Service
public class RagSearchService {

    /** 未命中时返回的固定话术（与提示词约束「资料不足只回复该句」保持一致） */
    private static final String MISS_REPLY = PromptConst.NO_KNOWLEDGE_REPLY;

    /** 单次检索最多送进提示词/引用的上下文条数 */
    private static final int MAX_CONTEXT_DOCS = 5;

    @Resource
    private MilvusUtil milvusUtil;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private LlmClient llmClient;

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource
    private KnowledgeFaqService faqService;

    @Resource
    private KnowledgeMissService missService;

    /**
     * 语义检索（不带行业人设）
     */
    public RagSearchResultDTO semanticSearch(String question, String tenantCode, String sessionId) {
        return semanticSearch(question, tenantCode, sessionId, null);
    }

    /**
     * 语义检索问答：三态返回，供 Agent / 帮助中心调用
     *
     * @param question       用户问题
     * @param tenantCode     租户编码（空值归 default）
     * @param sessionId      会话ID（用于未命中记录，可空）
     * @param industryPrompt 行业人设/拒答叠加，可空
     */
    public RagSearchResultDTO semanticSearch(String question, String tenantCode, String sessionId,
                                             String industryPrompt) {
        if (!StringUtils.hasText(question)) {
            return RagSearchResultDTO.miss(MISS_REPLY);
        }
        String tenant = KnowledgeFaqService.normalizeTenant(tenantCode);
        try {
            // TODO(切片检索扩展点) 文档切片链路落地后（见 DocumentChunkService），这里是「FAQ + 文档切片」双路召回合并点：
            //   1) 并行取两路命中：List<MilvusHit> faqHits = retrieve(tenant, question)；
            //      List<MilvusChunkHit> chunkHits = documentChunkService.search(tenant, question, milvusUtil.topK())；
            //   2) 两路各自回表过滤（FAQ 看 status/auditStatus，切片看逻辑删除），保留各自 score；
            //   3) 按 score 归并后统一截断到 MAX_CONTEXT_DOCS，再拼进同一份 prompt；
            //   4) 引用（RagCitationDTO）需要能区分来源是 FAQ 还是文档切片（当前只有 faqId 字段）。
            //   合并前：文档知识对问答不可见（且切片写入链路尚未接通）。
            List<MilvusHit> hits = retrieve(tenant, question);
            List<KnowledgeFaq> faqs = loadPublishedFaqs(tenant, hits);
            if (faqs.isEmpty()) {
                // 未命中：记录问题供运营补知识，topScore 帮助判断「差一点命中」
                missService.record(tenant, question, sessionId, topScore(hits));
                log.info("知识库未命中 tenant={} 命中条数={}", tenant, hits.size());
                return RagSearchResultDTO.miss(MISS_REPLY);
            }
            List<String> contextDocs = faqs.stream().map(this::toContextDoc).toList();
            String reply = generate(question, contextDocs, industryPrompt);
            return RagSearchResultDTO.hit(reply, buildCitations(hits, faqs));
        } catch (Exception e) {
            // 模型/向量任何一环不可用都归 UNAVAILABLE，让上层走「服务不可用」话术而不是「没查到」
            log.warn("RAG 语义检索不可用 tenant={} question({}): {}", tenant, maskQuestion(question), e.getMessage());
            return RagSearchResultDTO.unavailable("知识库检索暂不可用: " + e.getMessage());
        }
    }

    // ==================== FAQ 向量化入库 ====================

    /**
     * FAQ 向量化入库：内容拼接为「分类 + 问 + 答」后按 faqId 幂等 upsert
     *
     * <p>不校验启停/审核状态——禁用或未发布的 FAQ 也照常入库，检索时按库内状态过滤，
     * 这样「先入库、后审核发布」不会漏掉向量。</p>
     */
    public void vectorizeAndInsert(KnowledgeFaq faq) throws IOException {
        if (faq == null || faq.getId() == null) {
            throw new IOException("FAQ 尚未落库，无法向量化");
        }
        String content = buildFaqContent(faq);
        try {
            List<Float> vector = embeddingClient.getVector(content);
            milvusUtil.upsertTenant(faq.getId(), faq.getTenantCode(), vector, content);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("FAQ 向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * FAQ 增量向量更新：按 faqId 覆盖写入，与首次入库是同一动作
     */
    public void incrementUpdate(KnowledgeFaq faq) throws IOException {
        vectorizeAndInsert(faq);
    }

    /**
     * 批量增量更新：逐条容错，返回成功条数
     */
    public int batchIncrementUpdate(List<KnowledgeFaq> faqList) {
        return batchVectorizeAndInsert(faqList);
    }

    /**
     * 批量向量化入库：逐条容错，返回成功条数（单条失败只记日志，不影响其余）
     */
    public int batchVectorizeAndInsert(List<KnowledgeFaq> faqList) {
        if (faqList == null || faqList.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (KnowledgeFaq faq : faqList) {
            try {
                vectorizeAndInsert(faq);
                success++;
            } catch (Exception e) {
                log.warn("FAQ 批量向量化单条失败 id={}: {}", faq == null ? null : faq.getId(), e.getMessage());
            }
        }
        return success;
    }

    // ==================== 内部辅助 ====================

    /**
     * 问题向量化 + 租户集合检索（阈值过滤已在 MilvusUtil 内完成）
     */
    private List<MilvusHit> retrieve(String tenant, String question) throws IOException {
        List<Float> queryVector = embeddingClient.getVector(question);
        return milvusUtil.search(tenant, queryVector, milvusUtil.topK());
    }

    /**
     * 按 faqId 回查数据库，只保留「启用 + 已发布 + 同租户」的 FAQ，并保持向量命中顺序
     *
     * <p>三个关键点：</p>
     * <ol>
     *   <li><b>必须回查库</b>：向量库里的 content 是写入时的快照，直接用会把「已下线/已删除」的知识答给用户；
     *       逻辑删除由 {@code @TableLogic} 自动过滤，所以删除失败遗留的脏向量在这里被自然挡掉；</li>
     *   <li><b>用 LinkedHashMap 保序</b>：数据库 {@code IN} 查询不保证顺序，但引用要按相似度顺序展示；</li>
     *   <li><b>在循环内截断</b>：向量 TopK 可能命中十几条，最多只取前 {@code MAX_CONTEXT_DOCS} 条进提示词，
     *       避免上下文过长稀释注意力、也避免超出模型上下文窗口。</li>
     * </ol>
     */
    private List<KnowledgeFaq> loadPublishedFaqs(String tenant, List<MilvusHit> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<Long> ids = hits.stream().map(MilvusHit::getFaqId).distinct().toList();
        // 一次批量查库（IN 查询）后按命中顺序重排，避免 N 次单条查询
        Map<Long, KnowledgeFaq> byId = faqService.listByIds(ids).stream()
                .filter(f -> Integer.valueOf(1).equals(f.getStatus()))
                .filter(f -> Integer.valueOf(2).equals(f.getAuditStatus()))
                .filter(f -> tenant.equals(KnowledgeFaqService.normalizeTenant(f.getTenantCode())))
                .collect(Collectors.toMap(KnowledgeFaq::getId, f -> f, (a, b) -> a, LinkedHashMap::new));
        List<KnowledgeFaq> ordered = new ArrayList<>();
        for (MilvusHit hit : hits) {
            KnowledgeFaq faq = byId.get(hit.getFaqId());
            if (faq != null) {
                ordered.add(faq);
            }
            if (ordered.size() >= MAX_CONTEXT_DOCS) {
                break;
            }
        }
        return ordered;
    }

    /**
     * FAQ → 提示词上下文文本
     */
    private String toContextDoc(KnowledgeFaq faq) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(faq.getCategory())) {
            sb.append("【分类】").append(faq.getCategory()).append('\n');
        }
        sb.append("【问题】").append(faq.getQuestion() == null ? "" : faq.getQuestion()).append('\n');
        sb.append("【答案】").append(faq.getAnswer() == null ? "" : faq.getAnswer());
        return sb.toString();
    }

    /**
     * 组 Prompt 调模型；industryPrompt 非空时并入 system 作为行业人设/拒答要求
     */
    private String generate(String question, List<String> contextDocs, String industryPrompt) throws IOException {
        if (!StringUtils.hasText(industryPrompt)) {
            return llmClient.callWithMessages(promptTemplateService.buildMessages(question, contextDocs));
        }
        String systemPrompt = promptTemplateService.buildSystemPrompt().trim();
        String mergedSystem = systemPrompt.isEmpty() ? industryPrompt.trim()
                : systemPrompt + "\n\n" + industryPrompt.trim();
        return llmClient.callWithSystem(mergedSystem, promptTemplateService.buildUserPrompt(question, contextDocs));
    }

    /**
     * 组装引用来源：只保留最终进入回答的 FAQ，保持命中顺序
     */
    private List<RagCitationDTO> buildCitations(List<MilvusHit> hits, List<KnowledgeFaq> faqs) {
        Map<Long, KnowledgeFaq> byId = faqs.stream()
                .collect(Collectors.toMap(KnowledgeFaq::getId, f -> f, (a, b) -> a));
        List<RagCitationDTO> citations = new ArrayList<>();
        for (MilvusHit hit : hits) {
            KnowledgeFaq faq = byId.get(hit.getFaqId());
            if (faq == null) {
                continue;
            }
            RagCitationDTO citation = new RagCitationDTO();
            citation.setFaqId(faq.getId());
            citation.setQuestion(faq.getQuestion());
            citation.setCategory(faq.getCategory());
            citation.setScore(hit.getScore());
            citations.add(citation);
            if (citations.size() >= MAX_CONTEXT_DOCS) {
                break;
            }
        }
        return citations;
    }

    /**
     * 命中列表的最高分（未命中时用于记录「差一点命中」的分数）。
     *
     * <p>直接取第 0 条即可：Milvus 按相似度降序返回，且 {@code MilvusUtil} 只保留达到阈值的命中，
     * 所以「有命中但不满足发布/启用条件」时，这个分数能告诉运营「差多少」
     * （分数高说明知识和问题是对上的，只是状态没发布）。</p>
     */
    private Float topScore(List<MilvusHit> hits) {
        return hits.isEmpty() ? null : hits.get(0).getScore();
    }

    /**
     * 问题脱敏：日志只记长度与短哈希，避免把用户原文（可能含手机号/订单号等 PII）写进日志
     */
    private String maskQuestion(String question) {
        if (question == null) {
            return "null";
        }
        return "len=" + question.length() + ",hash=" + Integer.toHexString(question.hashCode());
    }

    /**
     * FAQ 向量化文本：分类 + 问 + 答 拼接，保证切片语义完整
     */
    private String buildFaqContent(KnowledgeFaq faq) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(faq.getCategory())) {
            sb.append(faq.getCategory()).append(' ');
        }
        sb.append(faq.getQuestion() == null ? "" : faq.getQuestion()).append(' ')
                .append(faq.getAnswer() == null ? "" : faq.getAnswer());
        return sb.toString().trim();
    }
}
