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
import com.ai.cs.knowledge.util.MilvusUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.io.IOException;
import java.util.List;

/**
 * FAQ知识库控制器
 * 提供FAQ的增删改查和语义检索功能
 */
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
    public Result<KnowledgeHealthVO> health() {
        KnowledgeHealthVO vo = new KnowledgeHealthVO();
        vo.setReady(milvusUtil.isReady());
        vo.setMilvus(milvusUtil.statusMessage());
        vo.setCollection(milvusUtil.tenantCollectionName());
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
        // 显式分页请求
        if (page != null) {
            return Result.success(faqService.pageList(tenantCode, keyword, status, page, size == null ? 20 : size));
        }
        // 带筛选条件但不分页：用大页拉取记录列表
        if (StringUtils.hasText(keyword) || status != null) {
            return Result.success(faqService.pageList(tenantCode, keyword, status, 1, 500).getRecords());
        }
        // 无参数：返回全部启用状态的FAQ
        return Result.success(faqService.getEnableFaqList(tenantCode));
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
        return Result.success(missService.pageList(tenantCode, page, size));
    }

    /**
     * 新增FAQ
     * 先落库再向量化写入Milvus，向量化失败不影响落库结果（返回提示）
     */
    @PostMapping("/save")
    @Operation(summary = "新增FAQ", description = "创建新的FAQ并自动向量化存入Milvus")
    public Result<String> save(@RequestBody KnowledgeFaq faq) {
        // 租户编码归一化，避免大小写/空格导致的数据隔离错乱
        faq.setTenantCode(KnowledgeFaqService.normalizeTenant(faq.getTenantCode()));
        faqService.save(faq);
        
        // 自动向量化并插入Milvus
        try {
            String milvusId = ragSearchService.vectorizeAndInsert(faq);
            // 更新FAQ的milvusId
            faq.setMilvusId(milvusId);
            faqService.updateById(faq);
            return Result.success("新增成功，已向量化插入Milvus");
        } catch (IOException e) {
            return Result.fail("新增成功，但向量化失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 更新FAQ
     * 先带出旧的milvusId以便更新向量时定位旧记录，再落库并增量更新向量
     */
    @PutMapping("/update")
    @Operation(summary = "更新FAQ", description = "修改FAQ信息并增量更新Milvus向量")
    public Result<String> update(@RequestBody KnowledgeFaq faq) {
        // 查询旧数据获取milvusId
        KnowledgeFaq oldFaq = faqService.getById(faq.getId());
        if (oldFaq == null) {
            return Result.fail("FAQ不存在，ID: " + faq.getId());
        }
        
        // 设置旧的milvusId
        faq.setMilvusId(oldFaq.getMilvusId());
        
        faqService.updateById(faq);
        
        // 增量更新Milvus向量
        try {
            String newMilvusId = ragSearchService.incrementUpdate(faq);
            // 更新FAQ的milvusId
            faq.setMilvusId(newMilvusId);
            faqService.updateById(faq);
            return Result.success("修改成功，已增量更新Milvus");
        } catch (IOException e) {
            return Result.fail("修改成功，但向量化失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 删除FAQ
     * 同步删除Milvus中对应的向量，避免残留脏数据
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除FAQ", description = "删除FAQ及其在Milvus中的向量数据")
    public Result<String> delete(@PathVariable Long id) {
        // 查询FAQ获取milvusId
        KnowledgeFaq faq = faqService.getById(id);
        if (faq != null && faq.getMilvusId() != null && !faq.getMilvusId().isEmpty()) {
            // 删除Milvus中的向量
            milvusUtil.deleteById(faq.getMilvusId());
        }
        
        faqService.removeById(id);
        return Result.success("删除成功");
    }

    /**
     * 语义检索问答（对接Milvus）
     * 带租户/会话参数时走租户隔离检索，否则走全局语义问答
     */
    @GetMapping("/search")
    @Operation(summary = "语义检索问答", description = "基于Milvus向量数据库的语义检索和RAG问答")
    public Result<String> search(@Parameter(description = "用户问题") @RequestParam String question,
                                 @RequestParam(required = false) String tenantCode,
                                 @RequestParam(required = false) String sessionId) {
        try {
            // 有租户或会话标识时按租户隔离检索，保证多租户数据安全
            if (StringUtils.hasText(tenantCode) || StringUtils.hasText(sessionId)) {
                RagSearchResultDTO data = ragSearchService.semanticSearch(question, tenantCode, sessionId);
                if (RagSearchResultDTO.UNAVAILABLE.equals(data.getStatus())) {
                    return Result.fail(data.getError() == null ? "知识库检索不可用" : data.getError());
                }
                return Result.success(data.getReply());
            }
            String answer = ragSearchService.semanticChat(question);
            return Result.success(answer);
        } catch (IOException e) {
            return Result.fail("知识库检索失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 租户隔离 RAG 检索
     * 与/search不同，返回包含命中状态、答复与引用的完整结构
     */
    @GetMapping("/rag/search")
    @Operation(summary = "租户隔离 RAG 检索", description = "返回命中状态、答复与引用")
    public Result<RagSearchResultDTO> ragSearch(@RequestParam String question,
                                                @RequestParam(required = false) String tenantCode,
                                                @RequestParam(required = false) String sessionId) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("检索问题不能为空");
        }
        RagSearchResultDTO data = ragSearchService.semanticSearch(question.trim(), tenantCode, sessionId);
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
            faq.setTenantCode(KnowledgeFaqService.normalizeTenant(faq.getTenantCode()));
            if (faq.getStatus() == null) {
                faq.setStatus(1);
            }
            faqService.save(faq);
            saved++;
            try {
                ragSearchService.vectorizeAndInsert(faq);
                indexed++;
            } catch (Exception ignored) {
                // 已落库
            }
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
            
            // 调用服务层进行向量化插入
            String milvusId = ragSearchService.vectorizeAndInsert(faq);
            
            return Result.success("向量化插入成功，Milvus ID: " + milvusId);
        } catch (IOException e) {
            return Result.fail("向量化失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 批量FAQ向量化并插入Milvus（全量）
     * @return 插入结果
     */
    @PostMapping("/vectorize/batch")
    @Operation(summary = "批量FAQ向量化", description = "将所有启用的FAQ批量向量化并存入Milvus")
    public Result<String> batchVectorizeAndInsert(@RequestParam(required = false) String tenantCode) {
        try {
            // 查询所有启用的FAQ
            List<KnowledgeFaq> faqList = faqService.getEnableFaqList(tenantCode);
            if (faqList == null || faqList.isEmpty()) {
                return Result.fail("没有可向量化的FAQ数据");
            }
            
            // 调用服务层进行批量向量化插入
            int count = ragSearchService.batchVectorizeAndInsert(faqList);
            
            if (count == 0) {
                return Result.fail("所有FAQ向量化均失败");
            }
            
            return Result.success("批量向量化插入成功，共插入 " + count + " 条数据");
        } catch (Exception e) {
            return Result.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 批量FAQ增量更新Milvus向量
     * @return 更新结果
     */
    @PostMapping("/vectorize/increment")
    @Operation(summary = "批量增量更新向量", description = "增量更新所有FAQ的Milvus向量数据")
    public Result<String> batchIncrementUpdate() {
        try {
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
        } catch (Exception e) {
            return Result.fail("系统异常: " + e.getMessage());
        }
    }
}
