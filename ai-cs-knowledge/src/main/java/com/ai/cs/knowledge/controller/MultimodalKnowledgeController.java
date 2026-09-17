package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.ai.cs.knowledge.service.HybridRetrievalService;
import com.ai.cs.knowledge.service.MultimodalKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 多模态知识库控制器
 * 提供图片问答、音频问答、视频检索、混合模态检索等功能
 */
@RestController
@RequestMapping("/api/multimodal-knowledge")
@RequiredArgsConstructor
@Tag(name = "多模态知识库", description = "图片问答、音频问答、视频检索、混合模态检索")
public class MultimodalKnowledgeController {

    private final MultimodalKnowledgeService knowledgeService;
    private final HybridRetrievalService hybridRetrievalService;

    // ========== 知识入库 ==========

    /**
     * 图片知识入库
     */
    @PostMapping("/image/index")
    @Operation(summary = "图片知识入库", description = "将图片分析结果向量化入库")
    public Result<MultimodalKnowledge> indexImage(
            @Parameter(description = "图片资源ID") @RequestParam String resourceId,
            @Parameter(description = "资源路径") @RequestParam(required = false) String resourcePath,
            @Parameter(description = "标题") @RequestParam String title,
            @Parameter(description = "描述") @RequestParam String description,
            @Parameter(description = "AI分析结果") @RequestParam(required = false) String analysis,
            @Parameter(description = "关键词（逗号分隔）") @RequestParam(required = false) String keywords,
            @Parameter(description = "标签（逗号分隔）") @RequestParam(required = false) String tags) {
        try {
            MultimodalKnowledge knowledge = knowledgeService.indexImageKnowledge(
                    resourceId, resourcePath, title, description, analysis, keywords, tags);
            return Result.success(knowledge);
        } catch (Exception e) {
            return Result.fail("图片知识入库失败: " + e.getMessage());
        }
    }

    /**
     * 音频知识入库
     */
    @PostMapping("/audio/index")
    @Operation(summary = "音频知识入库", description = "将音频分析结果向量化入库")
    public Result<MultimodalKnowledge> indexAudio(
            @Parameter(description = "音频资源ID") @RequestParam String resourceId,
            @Parameter(description = "资源路径") @RequestParam(required = false) String resourcePath,
            @Parameter(description = "标题") @RequestParam String title,
            @Parameter(description = "描述") @RequestParam String description,
            @Parameter(description = "AI分析结果") @RequestParam(required = false) String analysis,
            @Parameter(description = "关键词（逗号分隔）") @RequestParam(required = false) String keywords,
            @Parameter(description = "标签（逗号分隔）") @RequestParam(required = false) String tags) {
        try {
            MultimodalKnowledge knowledge = knowledgeService.indexAudioKnowledge(
                    resourceId, resourcePath, title, description, analysis, keywords, tags);
            return Result.success(knowledge);
        } catch (Exception e) {
            return Result.fail("音频知识入库失败: " + e.getMessage());
        }
    }

    /**
     * 视频知识入库
     */
    @PostMapping("/video/index")
    @Operation(summary = "视频知识入库", description = "将视频分析结果向量化入库")
    public Result<MultimodalKnowledge> indexVideo(
            @Parameter(description = "视频资源ID") @RequestParam String resourceId,
            @Parameter(description = "资源路径") @RequestParam(required = false) String resourcePath,
            @Parameter(description = "标题") @RequestParam String title,
            @Parameter(description = "描述") @RequestParam String description,
            @Parameter(description = "AI分析结果") @RequestParam(required = false) String analysis,
            @Parameter(description = "关键词（逗号分隔）") @RequestParam(required = false) String keywords,
            @Parameter(description = "标签（逗号分隔）") @RequestParam(required = false) String tags,
            @Parameter(description = "提取的实体（JSON）") @RequestParam(required = false) String entities) {
        try {
            MultimodalKnowledge knowledge = knowledgeService.indexVideoKnowledge(
                    resourceId, resourcePath, title, description, analysis, keywords, tags, entities);
            return Result.success(knowledge);
        } catch (Exception e) {
            return Result.fail("视频知识入库失败: " + e.getMessage());
        }
    }

    // ========== 模态问答 ==========

    /**
     * 图片知识库问答
     */
    @PostMapping("/image/qa")
    @Operation(summary = "图片知识问答", description = "基于图片分析结果回答用户问题")
    public Result<String> imageQa(
            @Parameter(description = "用户问题") @RequestParam String question,
            @Parameter(description = "指定图片资源ID（可选）") @RequestParam(required = false) String imageResourceId) {
        try {
            String answer = knowledgeService.imageQa(question, imageResourceId);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.fail("图片问答失败: " + e.getMessage());
        }
    }

    /**
     * 音频知识库问答
     */
    @PostMapping("/audio/qa")
    @Operation(summary = "音频知识问答", description = "基于音频分析结果回答用户问题")
    public Result<String> audioQa(
            @Parameter(description = "用户问题") @RequestParam String question,
            @Parameter(description = "指定音频资源ID（可选）") @RequestParam(required = false) String audioResourceId) {
        try {
            String answer = knowledgeService.audioQa(question, audioResourceId);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.fail("音频问答失败: " + e.getMessage());
        }
    }

    /**
     * 视频知识库检索
     */
    @PostMapping("/video/qa")
    @Operation(summary = "视频知识问答", description = "基于视频分析结果回答用户问题")
    public Result<String> videoQa(
            @Parameter(description = "用户问题") @RequestParam String question,
            @Parameter(description = "指定视频资源ID（可选）") @RequestParam(required = false) String videoResourceId) {
        try {
            String answer = knowledgeService.videoQa(question, videoResourceId);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.fail("视频问答失败: " + e.getMessage());
        }
    }

    /**
     * 混合模态问答
     */
    @PostMapping("/mixed/qa")
    @Operation(summary = "混合模态问答", description = "综合图片、音频、视频多模态知识回答问题")
    public Result<String> mixedQa(
            @Parameter(description = "用户问题") @RequestParam String question) {
        try {
            String answer = knowledgeService.mixedModalityQa(question);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.fail("混合模态问答失败: " + e.getMessage());
        }
    }

    // ========== 混合检索 ==========

    /**
     * 多模态混合检索
     */
    @PostMapping("/hybrid-search")
    @Operation(summary = "多模态混合检索", description = "融合文本、图片、音频多模态的混合检索")
    public Result<List<HybridRetrievalService.HybridSearchResult>> hybridSearch(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "检索模态（逗号分隔: TEXT,IMAGE,AUDIO,VIDEO）") @RequestParam(defaultValue = "TEXT,IMAGE,AUDIO") String modalities,
            @Parameter(description = "返回结果数量") @RequestParam(defaultValue = "10") int topK) {
        try {
            List<String> modalityList = Arrays.asList(modalities.split(","));
            List<HybridRetrievalService.HybridSearchResult> results =
                    hybridRetrievalService.hybridSearch(query, modalityList, topK);
            return Result.success(results);
        } catch (Exception e) {
            return Result.fail("混合检索失败: " + e.getMessage());
        }
    }

    /**
     * 图文混合问答
     */
    @PostMapping("/multimodal-chat")
    @Operation(summary = "多模态综合问答", description = "综合文本和图片知识的问答")
    public Result<String> multimodalChat(
            @Parameter(description = "用户问题") @RequestParam String question,
            @Parameter(description = "是否包含图片知识") @RequestParam(defaultValue = "true") boolean includeImages,
            @Parameter(description = "是否包含音频知识") @RequestParam(defaultValue = "true") boolean includeAudios) {
        try {
            String answer = hybridRetrievalService.multimodalChat(question, includeImages, includeAudios);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.fail("多模态问答失败: " + e.getMessage());
        }
    }

    /**
     * 跨模态语义搜索
     */
    @PostMapping("/cross-modal-search")
    @Operation(summary = "跨模态语义搜索", description = "从一种模态搜索另一种模态的内容")
    public Result<List<HybridRetrievalService.HybridSearchResult>> crossModalSearch(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "源模态: TEXT,IMAGE,AUDIO,VIDEO") @RequestParam String sourceModality,
            @Parameter(description = "目标模态: TEXT,IMAGE,AUDIO,VIDEO") @RequestParam String targetModality,
            @Parameter(description = "返回结果数量") @RequestParam(defaultValue = "10") int topK) {
        try {
            List<HybridRetrievalService.HybridSearchResult> results =
                    hybridRetrievalService.crossModalSearch(query, sourceModality, targetModality, topK);
            return Result.success(results);
        } catch (Exception e) {
            return Result.fail("跨模态搜索失败: " + e.getMessage());
        }
    }

    // ========== 知识管理 ==========

    /**
     * 获取知识条目列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取知识条目列表")
    public Result<List<MultimodalKnowledge>> listKnowledge(
            @Parameter(description = "模态类型: IMAGE,AUDIO,VIDEO") @RequestParam(required = false) String modality,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int limit) {
        try {
            return Result.success(knowledgeService.getAllKnowledge(modality, offset, limit));
        } catch (Exception e) {
            return Result.fail("获取知识列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取知识条目详情
     */
    @GetMapping("/detail/{knowledgeId}")
    @Operation(summary = "获取知识条目详情")
    public Result<MultimodalKnowledge> getKnowledgeDetail(
            @Parameter(description = "知识条目ID") @PathVariable String knowledgeId) {
        try {
            MultimodalKnowledge knowledge = knowledgeService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MultimodalKnowledge>()
                            .eq(MultimodalKnowledge::getKnowledgeId, knowledgeId)
                            .eq(MultimodalKnowledge::getDelFlag, 0)
            );
            if (knowledge == null) {
                return Result.fail("知识条目不存在");
            }
            return Result.success(knowledge);
        } catch (Exception e) {
            return Result.fail("获取知识详情失败: " + e.getMessage());
        }
    }

    /**
     * 删除知识条目
     */
    @DeleteMapping("/delete/{knowledgeId}")
    @Operation(summary = "删除知识条目")
    public Result<Boolean> deleteKnowledge(
            @Parameter(description = "知识条目ID") @PathVariable String knowledgeId) {
        try {
            boolean deleted = knowledgeService.deleteKnowledge(knowledgeId);
            return Result.success(deleted);
        } catch (Exception e) {
            return Result.fail("删除知识条目失败: " + e.getMessage());
        }
    }

    // ========== 视频分析 ==========

    /**
     * 视频内容分析
     */
    @PostMapping("/video/analyze")
    @Operation(summary = "视频内容分析", description = "提取视频关键帧并进行内容分析")
    public Result<Map<String, Object>> analyzeVideo(
            @Parameter(description = "视频文件路径") @RequestParam String videoPath,
            @Parameter(description = "帧提取间隔（秒）") @RequestParam(defaultValue = "5") int frameInterval,
            @Parameter(description = "最大提取帧数") @RequestParam(defaultValue = "20") int maxFrames) {
        try {
            Map<String, Object> result = knowledgeService.analyzeVideo(videoPath, frameInterval, maxFrames);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail("视频分析失败: " + e.getMessage());
        }
    }

    // ========== 统计 ==========

    /**
     * 获取多模态知识库统计
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取多模态知识库统计")
    public Result<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = knowledgeService.getStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            return Result.fail("获取统计信息失败: " + e.getMessage());
        }
    }
}
