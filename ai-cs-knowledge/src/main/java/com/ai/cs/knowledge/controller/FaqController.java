package com.ai.cs.knowledge.controller;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:59
 * @description TODO
 */


import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.service.KnowledgeFaqService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class FaqController {

    @Resource
    private KnowledgeFaqService faqService;

    @GetMapping("/list")
    public Result<List<KnowledgeFaq>> list() {
        return Result.success(faqService.getEnableFaqList());
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody KnowledgeFaq faq) {
        faqService.save(faq);
        return Result.success("新增成功");
    }

    @PutMapping("/update")
    public Result<String> update(@RequestBody KnowledgeFaq faq) {
        faqService.updateById(faq);
        return Result.success("修改成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        faqService.removeById(id);
        return Result.success("删除成功");
    }

    // 语义检索接口（对接Milvus）
    @GetMapping("/search")
    public Result<String> search(@RequestParam String question) {
        // 此处可扩展：文本向量化 -> Milvus相似度检索 -> 返回FAQ答案
        return Result.success("知识库检索结果：" + question);
    }
}
