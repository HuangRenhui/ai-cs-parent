package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.ai.cs.knowledge.service.KnowledgeGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱控制器
 * 提供知识图谱构建、查询、可视化、实体关系抽取等功能
 */
@RestController
@RequestMapping("/api/knowledge-graph")
@RequiredArgsConstructor
@Tag(name = "知识图谱管理", description = "知识图谱构建、查询、可视化、实体关系抽取")
public class KnowledgeGraphController {

    private final KnowledgeGraphService graphService;

    // ========== 图谱构建 ==========

    /**
     * 从文本构建知识图谱
     */
    @PostMapping("/build")
    @Operation(summary = "从文本构建知识图谱", description = "使用LLM从文本中自动抽取实体和关系构建图谱")
    public Result<String> buildFromText(
            @Parameter(description = "文本内容") @RequestParam String text,
            @Parameter(description = "来源文档ID") @RequestParam(required = false) String documentId,
            @Parameter(description = "来源文档名称") @RequestParam(required = false) String documentName) {
        try {
            String result = graphService.buildFromText(text, documentId, documentName);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail("图谱构建失败: " + e.getMessage());
        }
    }

    /**
     * 实体关系抽取
     */
    @PostMapping("/extract")
    @Operation(summary = "实体关系抽取", description = "从文本中抽取实体和关系（不入库）")
    public Result<Map<String, Object>> extractEntities(
            @Parameter(description = "文本内容") @RequestParam String text) {
        try {
            Map<String, Object> result = graphService.extractEntities(text);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail("实体关系抽取失败: " + e.getMessage());
        }
    }

    // ========== 节点管理 ==========

    /**
     * 创建节点
     */
    @PostMapping("/node")
    @Operation(summary = "创建图谱节点")
    public Result<KnowledgeGraphNode> createNode(@RequestBody KnowledgeGraphNode node) {
        try {
            KnowledgeGraphNode saved = graphService.saveNode(node);
            return Result.success(saved);
        } catch (Exception e) {
            return Result.fail("创建节点失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有节点
     */
    @GetMapping("/nodes")
    @Operation(summary = "获取所有节点")
    public Result<List<KnowledgeGraphNode>> getAllNodes() {
        try {
            return Result.success(graphService.getAllNodes());
        } catch (Exception e) {
            return Result.fail("获取节点失败: " + e.getMessage());
        }
    }

    /**
     * 按标签获取节点
     */
    @GetMapping("/nodes/label/{label}")
    @Operation(summary = "按标签获取节点")
    public Result<List<KnowledgeGraphNode>> getNodesByLabel(
            @Parameter(description = "节点标签") @PathVariable String label) {
        try {
            return Result.success(graphService.getNodesByLabel(label));
        } catch (Exception e) {
            return Result.fail("获取节点失败: " + e.getMessage());
        }
    }

    /**
     * 删除节点
     */
    @DeleteMapping("/node/{nodeId}")
    @Operation(summary = "删除节点（含关联关系）")
    public Result<Boolean> deleteNode(
            @Parameter(description = "节点ID") @PathVariable String nodeId) {
        try {
            boolean deleted = graphService.deleteNode(nodeId);
            return Result.success(deleted);
        } catch (Exception e) {
            return Result.fail("删除节点失败: " + e.getMessage());
        }
    }

    // ========== 关系管理 ==========

    /**
     * 创建关系
     */
    @PostMapping("/relation")
    @Operation(summary = "创建图谱关系")
    public Result<KnowledgeGraphRelation> createRelation(@RequestBody KnowledgeGraphRelation relation) {
        try {
            KnowledgeGraphRelation saved = graphService.saveRelation(relation);
            return Result.success(saved);
        } catch (Exception e) {
            return Result.fail("创建关系失败: " + e.getMessage());
        }
    }

    /**
     * 获取节点的所有关系
     */
    @GetMapping("/relations/{nodeId}")
    @Operation(summary = "获取节点所有关系")
    public Result<List<KnowledgeGraphRelation>> getNodeRelations(
            @Parameter(description = "节点ID") @PathVariable String nodeId) {
        try {
            return Result.success(graphService.getNodeRelations(nodeId));
        } catch (Exception e) {
            return Result.fail("获取关系失败: " + e.getMessage());
        }
    }

    /**
     * 删除关系
     */
    @DeleteMapping("/relation/{relationId}")
    @Operation(summary = "删除关系")
    public Result<Boolean> deleteRelation(
            @Parameter(description = "关系ID") @PathVariable String relationId) {
        try {
            boolean deleted = graphService.deleteRelation(relationId);
            return Result.success(deleted);
        } catch (Exception e) {
            return Result.fail("删除关系失败: " + e.getMessage());
        }
    }

    // ========== 图谱查询与问答 ==========

    /**
     * 基于图谱的问答
     */
    @PostMapping("/qa")
    @Operation(summary = "基于图谱的智能问答", description = "在图谱中查找相关实体和关系，结合LLM生成答案")
    public Result<String> graphQa(
            @Parameter(description = "用户问题") @RequestParam String question) {
        try {
            String answer = graphService.graphQa(question);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.fail("图谱问答失败: " + e.getMessage());
        }
    }

    /**
     * 搜索相关节点
     */
    @GetMapping("/search")
    @Operation(summary = "搜索相关节点", description = "根据关键词搜索图谱中的相关实体")
    public Result<List<KnowledgeGraphNode>> searchNodes(
            @Parameter(description = "搜索关键词") @RequestParam String keyword) {
        try {
            return Result.success(graphService.searchRelatedNodes(keyword));
        } catch (Exception e) {
            return Result.fail("搜索节点失败: " + e.getMessage());
        }
    }

    // ========== 可视化 ==========

    /**
     * 获取图谱可视化数据（ECharts格式）
     */
    @GetMapping("/visualization")
    @Operation(summary = "获取图谱可视化数据", description = "返回ECharts格式的图谱可视化数据（节点+关系+分类）")
    public Result<Map<String, Object>> getVisualizationData() {
        try {
            Map<String, Object> data = graphService.getGraphVisualizationData();
            return Result.success(data);
        } catch (Exception e) {
            return Result.fail("获取可视化数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取子图谱（N度邻居）
     */
    @GetMapping("/subgraph")
    @Operation(summary = "获取子图谱", description = "获取指定节点及其N度邻居的子图谱")
    public Result<Map<String, Object>> getSubGraph(
            @Parameter(description = "节点ID") @RequestParam String nodeId,
            @Parameter(description = "度数") @RequestParam(defaultValue = "2") int degree) {
        try {
            Map<String, Object> subGraph = graphService.getSubGraph(nodeId, degree);
            return Result.success(subGraph);
        } catch (Exception e) {
            return Result.fail("获取子图谱失败: " + e.getMessage());
        }
    }

    // ========== 统计 ==========

    /**
     * 获取图谱统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取图谱统计信息", description = "返回节点数、关系数、按类型分布等统计信息")
    public Result<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = graphService.getGraphStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            return Result.fail("获取统计信息失败: " + e.getMessage());
        }
    }
}
