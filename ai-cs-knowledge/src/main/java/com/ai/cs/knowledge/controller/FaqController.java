package com.ai.cs.knowledge.controller;

import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.llm.AiModelProperties;
import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.dto.KnowledgeHealthVO;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.entity.KnowledgeMiss;
import com.ai.cs.knowledge.service.KnowledgeFaqService;
import com.ai.cs.knowledge.service.KnowledgeMissService;
import com.ai.cs.knowledge.service.RagSearchService;
import com.ai.cs.common.security.JwtContext;
import com.ai.cs.knowledge.util.MilvusUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FAQ知识库控制器
 * 提供FAQ的增删改查和语义检索功能
 */
@Slf4j
@RestController
@RequestMapping("/knowledge")
@Tag(name = "FAQ知识库管理", description = "FAQ知识的增删改查和向量化检索")
public class FaqController {

    @Resource
    private KnowledgeFaqService faqService;
    @Resource
    private RagSearchService ragSearchService;
    @Resource
    private KnowledgeMissService missService;
    @Resource
    private MilvusUtil milvusUtil;
    @Resource
    private AiModelProperties aiModelProperties;

    /**
     * 知识库健康检查
     * 汇总Milvus连通状态、集合名与当前Embedding模型，供前端探活展示
     */
    @GetMapping("/health")
    @Operation(summary = "知识库健康检查")
    public Result<KnowledgeHealthVO> health(@RequestParam(required = false) String tenantCode) {
        KnowledgeHealthVO vo = new KnowledgeHealthVO();
        vo.setReady(milvusUtil.isReady());
        vo.setMilvus(milvusUtil.statusMessage());
        // 探活接口：只回显集合名。令牌带租户时以令牌为准，否则按请求参数、最后兜底 default
        vo.setCollection(milvusUtil.tenantCollectionName(JwtContext.resolveTenantCode(tenantCode)));
        vo.setEmbeddingModel(aiModelProperties.getEmbedding().getModel());
        return Result.success(vo);
    }

    /**
     * 查询FAQ列表
     * 兼容三种用法：传page走分页；只传keyword/status时按条件查询（最多500条）；都不传返回全部启用FAQ
     */
    @GetMapping("/list")
    @Operation(summary = "查询FAQ列表", description = "无分页时返回数组；传入 page 时返回分页结构")
    public Result<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        // 租户以令牌为准（坐席/访客令牌绑定租户时忽略请求参数），平台级账号才看请求参数
        String tenant = JwtContext.resolveTenantCode(tenantCode);
        // 显式分页请求
        if (page != null) {
            return Result.success(faqService.pageList(tenant, keyword, status, page, size == null ? 20 : size));
        }
        // 带筛选条件但不分页：用大页拉取记录列表
        if (StringUtils.hasText(keyword) || status != null) {
            return Result.success(faqService.pageList(tenant, keyword, status, 1, 500).getRecords());
        }
        // 无参数：返回全部启用状态的FAQ
        return Result.success(faqService.getEnableFaqList(tenant));
    }

    /**
     * 知识未命中回收
     * 分页查询用户提问但未命中知识库的记录，用于运营补充知识
     */
    @GetMapping("/miss")
    @Operation(summary = "知识未命中回收")
    public Result<PageResult<KnowledgeMiss>> miss(
            @RequestParam(required = false) String tenantCode,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        // 同上：未命中记录也按「令牌租户优先」过滤
        return Result.success(missService.pageList(JwtContext.resolveTenantCode(tenantCode), page, size));
    }

    /**
     * 新增FAQ
     * 先落库再向量化写入Milvus，向量化失败不影响落库结果（返回提示）
     */
    @PostMapping("/save")
    @Operation(summary = "新增FAQ", description = "创建新的FAQ并自动向量化存入Milvus")
    public Result<String> save(@RequestBody KnowledgeFaq faq) {
        // 租户「令牌优先」后再归一化：绑定租户的令牌会忽略请求体里的 tenantCode，防越权写入；
        // 归一化避免大小写/空格导致的数据隔离错乱
        faq.setTenantCode(KnowledgeFaqService.normalizeTenant(JwtContext.resolveTenantCode(faq.getTenantCode())));
        faqService.save(faq);
        
        // 自动向量化并插入Milvus（按 faqId upsert，无需回写向量ID）
        try {
            ragSearchService.vectorizeAndInsert(faq);
            return Result.success("新增成功，已向量化插入Milvus");
        } catch (IOException e) {
            // FAQ 已落库：向量化失败只提示，不当作整体失败（其余异常交给全局异常处理）
            return Result.fail("新增成功，但向量化失败");
        }
    }

    /**
     * 更新FAQ
     * 先落库，再按 faqId 增量更新向量
     */
    @PutMapping("/update")
    @Operation(summary = "更新FAQ", description = "修改FAQ信息并增量更新Milvus向量")
    public Result<String> update(@RequestBody KnowledgeFaq faq) {
        KnowledgeFaq oldFaq = faqService.getById(faq.getId());
        if (oldFaq == null) {
            return Result.fail("FAQ不存在，ID: " + faq.getId());
        }
        // 防 IDOR：令牌绑定租户时只能改本租户的 FAQ（改 id 越权在业务层直接拦掉）
        if (!tenantMatched(oldFaq.getTenantCode())) {
            return Result.fail("无权修改其他租户的FAQ");
        }
        // 租户不允许通过请求体改动：沿用库里的原值，避免把记录「搬」到别的租户
        faq.setTenantCode(oldFaq.getTenantCode());

        faqService.updateById(faq);
        
        // 增量更新Milvus向量（按 faqId upsert，无需回写向量ID）
        try {
            ragSearchService.incrementUpdate(faq);
            return Result.success("修改成功，已增量更新Milvus");
        } catch (IOException e) {
            // FAQ 已落库：向量化失败只提示，不当作整体失败（其余异常交给全局异常处理）
            return Result.fail("修改成功，但向量化失败");
        }
    }

    /**
     * 删除FAQ
     * 同步删除Milvus中对应的向量，避免残留脏数据
     * 向量删除失败不阻断业务删除（fail-open），失败项登记待清理，由向量对账任务补偿
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除FAQ", description = "删除FAQ及其在Milvus中的向量数据")
    public Result<String> delete(@PathVariable Long id) {
        KnowledgeFaq faq = faqService.getById(id);
        if (faq != null) {
            // 防 IDOR：绑定租户的令牌不能删其他租户的 FAQ（也不会去动它的向量）
            if (!tenantMatched(faq.getTenantCode())) {
                return Result.fail("无权删除其他租户的FAQ");
            }
            try {
                milvusUtil.deleteByFaqId(faq.getId(), faq.getTenantCode());
            } catch (Exception e) {
                log.warn("删除FAQ向量失败，已登记待清理 faqId={} tenant={}: {}",
                        faq.getId(), faq.getTenantCode(), e.getMessage());
                milvusUtil.markPendingDelete(MilvusUtil.BIZ_TYPE_FAQ, faq.getTenantCode(), faq.getId());
            }
        }

        faqService.removeById(id);
        return Result.success("删除成功");
    }

    /**
     * 校验记录的归属租户是否与当前令牌租户一致（防 IDOR：改 id 读写别人租户的数据）。
     *
     * <p>令牌未绑定租户时一律放行——平台级运营账号本来就要跨租户管理；
     * 绑定租户的令牌（坐席、访客、C 端）只能操作自己租户的数据。</p>
     *
     * @param recordTenantCode 记录上的租户编码
     * @return true=允许操作
     */
    private boolean tenantMatched(String recordTenantCode) {
        String tokenTenant = JwtContext.getCurrentTenantCode();
        if (!StringUtils.hasText(tokenTenant)) {
            return true;
        }
        return tokenTenant.equals(KnowledgeFaqService.normalizeTenant(recordTenantCode));
    }

    /**
     * 语义检索问答（对接Milvus）
     * 必须传租户：多租户下不再隐式落到 default 租户检索
     */
    @GetMapping("/search")
    @Operation(summary = "语义检索问答", description = "基于Milvus向量数据库的语义检索和RAG问答")
    public Result<String> search(@Parameter(description = "用户问题") @RequestParam String question,
                                 @RequestParam(required = false) String tenantCode,
                                 @RequestParam(required = false) String sessionId) {
        // 令牌绑定租户时以令牌为准；平台级账号必须显式传租户，避免隐式落到 default 租户检索
        String tenant = JwtContext.resolveTenantCodeOrNull(tenantCode);
        if (!StringUtils.hasText(tenant)) {
            return Result.fail("请指定租户编码 tenantCode");
        }
        RagSearchResultDTO data = ragSearchService.semanticSearch(question, tenant, sessionId);
        if (RagSearchResultDTO.UNAVAILABLE.equals(data.getStatus())) {
            return Result.fail(data.getError() == null ? "知识库检索不可用" : data.getError());
        }
        return Result.success(data.getReply());
    }

    /**
     * 租户隔离 RAG 检索
     * 与/search不同，返回包含命中状态、答复与引用的完整结构
     */
    @GetMapping("/rag/search")
    @Operation(summary = "租户隔离 RAG 检索", description = "返回命中状态、答复与引用")
    public Result<RagSearchResultDTO> ragSearch(@RequestParam String question,
                                                @RequestParam(required = false) String tenantCode,
                                                @RequestParam(required = false) String sessionId,
                                                @RequestParam(required = false) String industryPrompt) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("检索问题不能为空");
        }
        // 同上：令牌租户优先；平台级账号必须显式传租户
        String tenant = JwtContext.resolveTenantCodeOrNull(tenantCode);
        if (!StringUtils.hasText(tenant)) {
            throw new BusinessException("请指定租户编码 tenantCode");
        }
        RagSearchResultDTO data = ragSearchService.semanticSearch(question.trim(), tenant, sessionId, industryPrompt);
        if (RagSearchResultDTO.UNAVAILABLE.equals(data.getStatus())) {
            return Result.fail(data.getError() == null ? "知识库检索不可用" : data.getError());
        }
        return Result.success(data);
    }

    /**
     * 按 ID 查询 FAQ
     */
    @GetMapping("/faq/{id}")
    @Operation(summary = "按 ID 查询 FAQ")
    public Result<KnowledgeFaq> getFaqById(@PathVariable Long id) {
        KnowledgeFaq faq = faqService.getById(id);
        if (faq == null) {
            throw new BusinessException("FAQ不存在");
        }
        return Result.success(faq);
    }

    /**
     * 批量导入FAQ
     * 逐条落库并向量化，单条向量化失败不影响整体导入
     */
    @PostMapping("/import")
    @Operation(summary = "批量导入FAQ")
    public Result<String> importFaqs(@RequestBody List<KnowledgeFaq> faqs) {
        if (faqs == null || faqs.isEmpty()) {
            throw new BusinessException("导入列表不能为空");
        }
        // 限制单批次数量，防止大批量导入拖垮Embedding服务
        if (faqs.size() > 200) {
            throw new BusinessException("单次最多导入 200 条");
        }
        int saved = 0;
        int indexed = 0;
        for (KnowledgeFaq faq : faqs) {
            // 强制清空ID确保是新增而非更新；租户归一化；状态默认启用
            faq.setId(null);
            // 租户「令牌优先」+ 归一化：批量导入的记录只能落到当前令牌租户，防越权写入
            faq.setTenantCode(KnowledgeFaqService.normalizeTenant(
                    JwtContext.resolveTenantCode(faq.getTenantCode())));
            if (faq.getStatus() == null) {
                faq.setStatus(1);
            }
            faqService.save(faq);
            saved++;
            try {
                ragSearchService.vectorizeAndInsert(faq);
                indexed++;
            } catch (Exception e) {
                // 已落库：单条向量化失败不阻断整批导入，但必须留痕便于后续补录
                log.warn("批量导入FAQ向量化失败 faqId={}: {}", faq.getId(), e.getMessage());
            }
        }
        if (indexed < saved) {
            return Result.fail("导入 " + saved + " 条，其中 " + (saved - indexed) + " 条未写入向量库，检索暂时不可用");
        }
        return Result.success("导入 " + saved + " 条，已写入向量库 " + indexed + " 条");
    }

    /**
     * 单个FAQ向量化并插入Milvus
     * @param id FAQ的ID
     * @return 插入结果
     */
    @PostMapping("/vectorize/{id}")
    @Operation(summary = "单个FAQ向量化", description = "将指定FAQ向量化并插入Milvus向量数据库")
    public Result<String> vectorizeAndInsert(@Parameter(description = "FAQ的ID") @PathVariable Long id) {
        try {
            // 查询FAQ
            KnowledgeFaq faq = faqService.getById(id);
            if (faq == null) {
                return Result.fail("FAQ不存在，ID: " + id);
            }
            
            // 调用服务层进行向量化插入（按 faqId upsert）
            ragSearchService.vectorizeAndInsert(faq);
            
            return Result.success("向量化插入成功，FAQ ID: " + faq.getId());
        } catch (IOException e) {
            return Result.fail("向量化失败");
        }
    }

    /**
     * 批量FAQ向量化并插入Milvus（全量）
     * @return 插入结果
     */
    @PostMapping("/vectorize/batch")
    @Operation(summary = "批量FAQ向量化", description = "将所有启用的FAQ批量向量化并存入Milvus")
    public Result<String> batchVectorizeAndInsert(@RequestParam(required = false) String tenantCode) {
        // 租户「令牌优先」：避免改参数就把别的租户的 FAQ 全量重建一遍向量
        String tenant = JwtContext.resolveTenantCode(tenantCode);
        // 查询所有启用的FAQ
        List<KnowledgeFaq> faqList = faqService.getEnableFaqList(tenant);
        if (faqList == null || faqList.isEmpty()) {
            return Result.fail("没有可向量化的FAQ数据");
        }

        // 调用服务层进行批量向量化插入
        int count = ragSearchService.batchVectorizeAndInsert(faqList);

        if (count == 0) {
            return Result.fail("所有FAQ向量化均失败");
        }

        return Result.success("批量向量化插入成功，共插入 " + count + " 条数据");
    }

    /**
     * 批量FAQ增量更新Milvus向量
     * @return 更新结果
     */
    @PostMapping("/vectorize/increment")
    @Operation(summary = "批量增量更新向量", description = "增量更新所有FAQ的Milvus向量数据")
    public Result<String> batchIncrementUpdate() {
        // 查询所有启用的FAQ
        List<KnowledgeFaq> faqList = faqService.getEnableFaqList();
        if (faqList == null || faqList.isEmpty()) {
            return Result.fail("没有可更新的FAQ数据");
        }

        // 调用服务层进行批量增量更新
        int count = ragSearchService.batchIncrementUpdate(faqList);

        if (count == 0) {
            return Result.fail("所有FAQ向量化均失败");
        }

        return Result.success("批量增量更新成功，共更新 " + count + " 条数据");
    }

    /**
     * 批量查询 FAQ 向量化状态
     *
     * <p>向量库故障时返回 {@code available=false}（状态未知）而非「未向量化」，前端据此显示「—」；
     * 返回值里的 vectorizedIds 为已在租户集合中的 FAQ 主键。</p>
     */
    @PostMapping("/vectorize/status")
    @Operation(summary = "批量查询FAQ向量化状态", description = "返回已写入向量库的FAQ主键；向量库不可用时 available=false，状态视为未知")
    public Result<Map<String, Object>> vectorizeStatus(@RequestParam(required = false) String tenantCode,
                                                       @RequestBody List<Long> ids) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            data.put("available", true);
            data.put("vectorizedIds", List.of());
            return Result.success(data);
        }
        // 租户「令牌优先」：状态查询只允许查当前租户的集合
        String tenant = JwtContext.resolveTenantCode(tenantCode);
        try {
            data.put("vectorizedIds", new ArrayList<>(milvusUtil.filterExistingFaqIds(tenant, ids)));
            data.put("available", true);
        } catch (Exception e) {
            log.warn("查询FAQ向量化状态失败 tenant={} count={}: {}", tenant, ids.size(), e.getMessage());
            data.put("vectorizedIds", List.of());
            data.put("available", false);
            data.put("message", "向量库暂不可用，状态未知");
        }
        return Result.success(data);
    }

    /**
     * FAQ 点赞/点踩反馈
     */
    @PostMapping("/feedback/{id}")
    @Operation(summary = "FAQ 点赞/点踩")
    public Result<String> feedback(@PathVariable Long id, @RequestParam String type) {
        return faqService.feedback(id, type) ? Result.success("反馈成功") : Result.fail("反馈失败，FAQ不存在或类型非法");
    }

    /**
     * 未命中转问学习：把未命中记录转成正式 FAQ 并向量化
     */
    @PostMapping("/miss/{id}/convert")
    @Operation(summary = "未命中转问学习", description = "将未命中记录转为正式FAQ并向量化入库")
    public Result<String> convertMiss(@PathVariable Long id,
                                      @RequestParam String answer,
                                      @RequestParam(required = false) String category) {
        KnowledgeMiss miss = missService.getById(id);
        if (miss == null) {
            return Result.fail("未命中记录不存在");
        }
        if (miss.getStatus() != null && miss.getStatus() == 1) {
            return Result.fail("该未命中记录已处理");
        }
        // 防 IDOR：绑定租户的令牌只能处理本租户的未命中记录
        if (!tenantMatched(miss.getTenantCode())) {
            return Result.fail("无权处理其他租户的未命中记录");
        }
        if (!StringUtils.hasText(answer)) {
            return Result.fail("答案不能为空");
        }
        // 创建 FAQ（直接发布）：租户沿用未命中记录并归一化
        KnowledgeFaq faq = new KnowledgeFaq();
        faq.setTenantCode(KnowledgeFaqService.normalizeTenant(miss.getTenantCode()));
        faq.setQuestion(miss.getQuestion());
        faq.setAnswer(answer);
        faq.setCategory(category);
        faq.setStatus(1);
        faq.setAuditStatus(2);
        faq.setSortNum(0);
        faqService.save(faq);
        // 向量化（失败不影响转问结果，可后续批量补录）
        try {
            ragSearchService.vectorizeAndInsert(faq);
        } catch (Exception e) {
            // 向量化失败可后续通过批量向量化补录，但必须留痕
            log.warn("转问为FAQ后向量化失败 faqId={}: {}", faq.getId(), e.getMessage());
        }
        missService.markConverted(id, faq.getId());
        return Result.success("已转问为FAQ，ID: " + faq.getId());
    }
}
