package com.ai.cs.knowledge.controller;

import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.service.KnowledgeFaqService;
import com.ai.cs.knowledge.service.RagSearchService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对外帮助中心控制器
 * 面向 C 端顾客的自助服务，无需登录即可检索已发布的知识，并支持点赞点踩反哺排序
 */
@RestController
@RequestMapping("/help-center")
public class HelpCenterController {

    @Resource
    private RagSearchService ragSearchService;
    @Resource
    private KnowledgeFaqService faqService;

    /**
     * 帮助中心语义检索（返回答复与引用）
     */
    @GetMapping("/search")
    public Result<RagSearchResultDTO> search(@RequestParam String question,
                                             @RequestParam(required = false) String tenantCode) {
        if (question == null || question.trim().isEmpty()) {
            return Result.fail("检索问题不能为空");
        }
        RagSearchResultDTO data = ragSearchService.semanticSearch(question.trim(), tenantCode, null);
        if (RagSearchResultDTO.UNAVAILABLE.equals(data.getStatus())) {
            return Result.fail(data.getError() == null ? "知识库检索不可用" : data.getError());
        }
        return Result.success(data);
    }

    /**
     * 已发布 FAQ 列表（分类/关键词过滤，按点赞与浏览热度排序）
     */
    @GetMapping("/faqs")
    public Result<PageResult<KnowledgeFaq>> faqs(@RequestParam(required = false) String tenantCode,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(faqService.pagePublished(tenantCode, category, keyword, page, size));
    }

    /**
     * 已发布 FAQ 分类列表
     */
    @GetMapping("/categories")
    public Result<List<String>> categories(@RequestParam(required = false) String tenantCode) {
        return Result.success(faqService.listCategories(tenantCode));
    }

    /**
     * 帮助中心点赞/点踩
     */
    @PostMapping("/feedback/{id}")
    public Result<String> feedback(@PathVariable Long id, @RequestParam String type) {
        return faqService.feedback(id, type) ? Result.success("反馈成功") : Result.fail("反馈失败");
    }

    /**
     * 帮助中心浏览量 +1
     */
    @PostMapping("/view/{id}")
    public Result<String> view(@PathVariable Long id) {
        return faqService.view(id) ? Result.success("ok") : Result.fail("FAQ不存在");
    }
}
