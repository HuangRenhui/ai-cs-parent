package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.service.KnowledgeFaqService;
import com.ai.cs.knowledge.service.RagSearchService;
import com.ai.cs.knowledge.util.MilvusUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private MilvusUtil milvusUtil;

    @GetMapping("/list")
    @Operation(summary = "查询FAQ列表", description = "获取所有启用的FAQ知识列表")
    public Result<List<KnowledgeFaq>> list() {
        return Result.success(faqService.getEnableFaqList());
    }

    @PostMapping("/save")
    @Operation(summary = "新增FAQ", description = "创建新的FAQ并自动向量化存入Milvus")
    public Result<String> save(@RequestBody KnowledgeFaq faq) {
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

    // 语义检索接口（对接Milvus）
    @GetMapping("/search")
    @Operation(summary = "语义检索问答", description = "基于Milvus向量数据库的语义检索和RAG问答")
    public Result<String> search(@Parameter(description = "用户问题") @RequestParam String question) {
        try {
            String answer = ragSearchService.semanticChat(question);
            return Result.success(answer);
        } catch (IOException e) {
            return Result.fail("知识库检索失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail("系统异常: " + e.getMessage());
        }
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
    public Result<String> batchVectorizeAndInsert() {
        try {
            // 查询所有启用的FAQ
            List<KnowledgeFaq> faqList = faqService.getEnableFaqList();
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
