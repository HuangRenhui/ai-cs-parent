package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.ai.cs.knowledge.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库增强功能控制器
 * 提供 3D模型、Neo4j集成、图谱推理、时序图谱、多语言融合、Graph Embedding、GraphRAG、图谱演化 等增强功能API
 */
@RestController
@RequestMapping("/api/knowledge-enhanced")
@RequiredArgsConstructor
@Tag(name = "知识库增强功能", description = "知识库进一步增强功能API：3D模型、图谱推理、GraphRAG等")
public class KnowledgeEnhancedController {

    private final Model3DKnowledgeService model3DService;
    private final Neo4jIntegrationService neo4jService;
    private final GraphReasoningEngine reasoningEngine;
    private final TemporalKnowledgeGraphService temporalService;
    private final MultilingualGraphFusionService multilingualService;
    private final GraphEmbeddingService embeddingService;
    private final GraphRagService graphRagService;
    private final GraphEvolutionService evolutionService;

    // ========== 3D 模型知识库 ==========

    @PostMapping("/3d-model/import")
    @Operation(summary = "导入3D模型", description = "将3D模型文件（OBJ/STL/GLTF/GLB/FBX等）导入知识库")
    public Result<MultimodalKnowledge> import3DModel(
            @Parameter(description = "模型文件路径") @RequestParam String modelPath,
            @Parameter(description = "模型名称") @RequestParam String modelName,
            @Parameter(description = "模型描述") @RequestParam(required = false) String description,
            @Parameter(description = "标签列表(逗号分隔)") @RequestParam(required = false) String tags) {
        try {
            List<String> tagList = tags != null ? List.of(tags.split(",")) : List.of();
            MultimodalKnowledge knowledge = model3DService.import3DModel(modelPath, modelName, description, tagList);
            return Result.success(knowledge);
        } catch (Exception e) {
            return Result.fail("导入3D模型失败: " + e.getMessage());
        }
    }

    @PostMapping("/3d-model/batch-import")
    @Operation(summary = "批量导入3D模型")
    public Result<Map<String, Object>> batchImport3DModels(@RequestBody List<Map<String, Object>> modelInfos) {
        try {
            return Result.success(model3DService.batchImport3DModels(modelInfos));
        } catch (Exception e) {
            return Result.fail("批量导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/3d-model/search")
    @Operation(summary = "搜索3D模型")
    public Result<List<MultimodalKnowledge>> search3DModels(
            @Parameter(description = "搜索关键词") @RequestParam String keyword) {
        return Result.success(model3DService.search3DModels(keyword));
    }

    @GetMapping("/3d-model/all")
    @Operation(summary = "获取所有3D模型")
    public Result<List<MultimodalKnowledge>> getAll3DModels() {
        return Result.success(model3DService.getAll3DModels());
    }

    @GetMapping("/3d-model/tag/{tag}")
    @Operation(summary = "按标签筛选3D模型")
    public Result<List<MultimodalKnowledge>> get3DModelsByTag(@PathVariable String tag) {
        return Result.success(model3DService.get3DModelsByTag(tag));
    }

    @GetMapping("/3d-model/similar/{knowledgeId}")
    @Operation(summary = "获取相似3D模型")
    public Result<List<MultimodalKnowledge>> getSimilar3DModels(
            @PathVariable String knowledgeId,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(model3DService.getSimilar3DModels(knowledgeId, limit));
    }

    @GetMapping("/3d-model/report/{knowledgeId}")
    @Operation(summary = "获取3D模型分析报告")
    public Result<Map<String, Object>> getModelReport(@PathVariable String knowledgeId) {
        try {
            return Result.success(model3DService.getModelAnalysisReport(knowledgeId));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/3d-model/compare")
    @Operation(summary = "比较两个3D模型")
    public Result<Map<String, Object>> compare3DModels(
            @RequestParam String knowledgeId1,
            @RequestParam String knowledgeId2) {
        try {
            return Result.success(model3DService.compare3DModels(knowledgeId1, knowledgeId2));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/3d-model/qa")
    @Operation(summary = "3D模型知识问答")
    public Result<String> modelQa(
            @RequestParam String question,
            @RequestParam(required = false) String knowledgeId) {
        return Result.success(model3DService.modelQa(question, knowledgeId));
    }

    @GetMapping("/3d-model/conversion-advice/{knowledgeId}")
    @Operation(summary = "获取3D模型格式转换建议")
    public Result<Map<String, Object>> getConversionAdvice(
            @PathVariable String knowledgeId,
            @RequestParam String targetFormat) {
        return Result.success(model3DService.getFormatConversionAdvice(knowledgeId, targetFormat));
    }

    // ========== Neo4j 图数据库集成 ==========

    @GetMapping("/neo4j/status")
    @Operation(summary = "检查Neo4j连接状态")
    public Result<Boolean> checkNeo4jStatus() {
        return Result.success(neo4jService.isConnected());
    }

    @PostMapping("/neo4j/cypher")
    @Operation(summary = "执行Cypher查询")
    public Result<List<Map<String, Object>>> executeCypher(
            @Parameter(description = "Cypher查询语句") @RequestParam String cypher) {
        try {
            return Result.success(neo4jService.executeCypher(cypher));
        } catch (Exception e) {
            return Result.fail("Cypher查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/neo4j/sync")
    @Operation(summary = "全量同步到Neo4j")
    public Result<Map<String, Object>> fullSyncToNeo4j() {
        return Result.success(neo4jService.fullSyncToNeo4j());
    }

    @GetMapping("/neo4j/shortest-path")
    @Operation(summary = "查找最短路径")
    public Result<Map<String, Object>> findShortestPath(
            @RequestParam String startNodeId,
            @RequestParam String endNodeId,
            @RequestParam(defaultValue = "5") int maxDepth) {
        return Result.success(neo4jService.findShortestPath(startNodeId, endNodeId, maxDepth));
    }

    @GetMapping("/neo4j/k-hop/{nodeId}")
    @Operation(summary = "获取K度邻居")
    public Result<Map<String, Object>> getKHopNeighbors(
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "2") int depth) {
        return Result.success(neo4jService.getKHopNeighbors(nodeId, depth));
    }

    @GetMapping("/neo4j/communities")
    @Operation(summary = "社区检测")
    public Result<Map<String, Object>> detectCommunities() {
        return Result.success(neo4jService.detectCommunities());
    }

    @GetMapping("/neo4j/pagerank")
    @Operation(summary = "PageRank中心性计算")
    public Result<List<Map<String, Object>>> computePageRank(
            @RequestParam(defaultValue = "20") int topK) {
        return Result.success(neo4jService.computePageRank(topK));
    }

    @GetMapping("/neo4j/bridge-nodes")
    @Operation(summary = "查找桥接节点")
    public Result<List<Map<String, Object>>> findBridgeNodes() {
        return Result.success(neo4jService.findBridgeNodes());
    }

    @GetMapping("/neo4j/circular-deps")
    @Operation(summary = "查找环形依赖")
    public Result<List<Map<String, Object>>> findCircularDependencies() {
        return Result.success(neo4jService.findCircularDependencies());
    }

    @GetMapping("/neo4j/stats")
    @Operation(summary = "获取Neo4j图统计")
    public Result<Map<String, Object>> getNeo4jStats() {
        return Result.success(neo4jService.getNeo4jGraphStats());
    }

    // ========== 图谱推理引擎 ==========

    @GetMapping("/reasoning/rules")
    @Operation(summary = "获取所有推理规则")
    public Result<List<GraphReasoningEngine.Rule>> getReasoningRules() {
        return Result.success(reasoningEngine.getBuiltinRules());
    }

    @PostMapping("/reasoning/execute-all")
    @Operation(summary = "执行所有推理规则")
    public Result<Map<String, Object>> executeAllRules() {
        return Result.success(reasoningEngine.executeAllRules());
    }

    @PostMapping("/reasoning/execute/{ruleName}")
    @Operation(summary = "执行指定推理规则")
    public Result<GraphReasoningEngine.ReasoningResult> executeRule(
            @PathVariable String ruleName) {
        try {
            List<GraphReasoningEngine.Rule> rules = reasoningEngine.getBuiltinRules();
            for (GraphReasoningEngine.Rule rule : rules) {
                if (rule.getName().equals(ruleName)) {
                    return Result.success(reasoningEngine.executeRule(rule));
                }
            }
            return Result.fail("未找到规则: " + ruleName);
        } catch (Exception e) {
            return Result.fail("执行规则失败: " + e.getMessage());
        }
    }

    @GetMapping("/reasoning/paths")
    @Operation(summary = "查找两实体间所有路径")
    public Result<List<Map<String, Object>>> findAllPaths(
            @RequestParam String sourceName,
            @RequestParam String targetName,
            @RequestParam(defaultValue = "4") int maxDepth) {
        return Result.success(reasoningEngine.findAllPaths(sourceName, targetName, maxDepth));
    }

    @GetMapping("/reasoning/hierarchy")
    @Operation(summary = "查找概念层级路径")
    public Result<Map<String, Object>> findHierarchyPath(
            @RequestParam String nodeName,
            @RequestParam(defaultValue = "up") String direction) {
        return Result.success(reasoningEngine.findHierarchyPath(nodeName, direction));
    }

    @GetMapping("/reasoning/disambiguation")
    @Operation(summary = "实体消歧")
    public Result<List<Map<String, Object>>> entityDisambiguation() {
        return Result.success(reasoningEngine.entityDisambiguation());
    }

    @PostMapping("/reasoning/merge-entities")
    @Operation(summary = "合并实体")
    public Result<Map<String, Object>> mergeEntities(
            @RequestParam String keepNodeId,
            @RequestParam String removeNodeId) {
        try {
            return Result.success(reasoningEngine.mergeEntities(keepNodeId, removeNodeId));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/reasoning/chain")
    @Operation(summary = "生成推理链")
    public Result<Map<String, Object>> generateReasoningChain(
            @RequestParam String sourceName,
            @RequestParam String targetName) {
        return Result.success(reasoningEngine.generateReasoningChain(sourceName, targetName));
    }

    // ========== 时序知识图谱 ==========

    @PostMapping("/temporal/relation")
    @Operation(summary = "创建时序关系")
    public Result<?> createTemporalRelation(
            @RequestParam String sourceNodeId,
            @RequestParam String targetNodeId,
            @RequestParam String relationType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String validFrom,
            @RequestParam(required = false) String validUntil) {
        try {
            var validFromTime = validFrom != null ? java.time.LocalDateTime.parse(validFrom) : null;
            var validUntilTime = validUntil != null ? java.time.LocalDateTime.parse(validUntil) : null;
            temporalService.createTemporalRelation(sourceNodeId, targetNodeId, relationType, description, validFromTime, validUntilTime);
            return Result.success("时序关系创建成功");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/temporal/snapshot")
    @Operation(summary = "获取指定时间点图谱快照")
    public Result<Map<String, Object>> getSnapshot(
            @RequestParam String snapshotTime) {
        try {
            var time = java.time.LocalDateTime.parse(snapshotTime);
            return Result.success(temporalService.getSnapshot(time));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/temporal/changes")
    @Operation(summary = "获取两时间点间图谱变化")
    public Result<Map<String, Object>> getGraphChanges(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            var fromTime = java.time.LocalDateTime.parse(from);
            var toTime = java.time.LocalDateTime.parse(to);
            return Result.success(temporalService.getGraphChanges(fromTime, toTime));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/temporal/timeline/{nodeId}")
    @Operation(summary = "获取实体关系变化时间线")
    public Result<List<Map<String, Object>>> getEntityTimeline(@PathVariable String nodeId) {
        return Result.success(temporalService.getEntityTimeline(nodeId));
    }

    @GetMapping("/temporal/global-timeline")
    @Operation(summary = "获取全局时间线")
    public Result<List<Map<String, Object>>> getGlobalTimeline(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(temporalService.getGlobalTimeline(limit));
    }

    @GetMapping("/temporal/evolution/{nodeId}")
    @Operation(summary = "分析关系演化模式")
    public Result<Map<String, Object>> analyzeRelationEvolution(@PathVariable String nodeId) {
        return Result.success(temporalService.analyzeRelationEvolution(nodeId));
    }

    @GetMapping("/temporal/predict-trend")
    @Operation(summary = "预测关系演化趋势")
    public Result<Map<String, Object>> predictRelationTrend(@RequestParam String relationType) {
        return Result.success(temporalService.predictRelationTrend(relationType));
    }

    @GetMapping("/temporal/validate")
    @Operation(summary = "验证时序一致性")
    public Result<Map<String, Object>> validateTemporalConsistency() {
        return Result.success(temporalService.validateTemporalConsistency());
    }

    // ========== 多语言知识图谱融合 ==========

    @GetMapping("/multilingual/equivalents")
    @Operation(summary = "查找多语言等价实体")
    public Result<Map<String, Object>> findMultilingualEquivalents(
            @RequestParam String entityName,
            @RequestParam(defaultValue = "zh") String sourceLang) {
        return Result.success(multilingualService.findMultilingualEquivalents(entityName, sourceLang));
    }

    @PostMapping("/multilingual/link")
    @Operation(summary = "创建跨语言实体链接")
    public Result<String> createCrossLingualLink(
            @RequestParam String entityName1,
            @RequestParam(defaultValue = "zh") String lang1,
            @RequestParam String entityName2,
            @RequestParam(defaultValue = "en") String lang2) {
        try {
            multilingualService.createCrossLingualLink(entityName1, lang1, entityName2, lang2);
            return Result.success("跨语言链接创建成功");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/multilingual/fuse")
    @Operation(summary = "融合两个语言的知识图谱")
    public Result<Map<String, Object>> fuseLanguageGraphs(
            @RequestParam(defaultValue = "zh") String sourceLang,
            @RequestParam(defaultValue = "en") String targetLang) {
        return Result.success(multilingualService.fuseLanguageGraphs(sourceLang, targetLang));
    }

    @GetMapping("/multilingual/cross-lingual-relations")
    @Operation(summary = "查找跨语言关联实体")
    public Result<List<Map<String, Object>>> findCrossLingualRelations(
            @RequestParam String entityName,
            @RequestParam(defaultValue = "zh") String sourceLang,
            @RequestParam(defaultValue = "en") String targetLang,
            @RequestParam(defaultValue = "3") int maxDepth) {
        return Result.success(multilingualService.findCrossLingualRelations(entityName, sourceLang, targetLang, maxDepth));
    }

    @GetMapping("/multilingual/language-distribution")
    @Operation(summary = "检测语言分布")
    public Result<Map<String, Object>> detectLanguageDistribution() {
        return Result.success(multilingualService.detectLanguageDistribution());
    }

    @PostMapping("/multilingual/tag-language")
    @Operation(summary = "为实体添加语言标签")
    public Result<String> tagNodeLanguage(
            @RequestParam String nodeId,
            @RequestParam String language) {
        try {
            multilingualService.tagNodeLanguage(nodeId, language);
            return Result.success("语言标签添加成功");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/multilingual/aliases")
    @Operation(summary = "添加多语言别名")
    public Result<String> addMultilingualAliases(
            @RequestParam String nodeId,
            @RequestBody Map<String, String> aliases) {
        try {
            multilingualService.addMultilingualAliases(nodeId, aliases);
            return Result.success("多语言别名添加成功");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    // ========== Graph Embedding ==========

    @PostMapping("/embedding/generate")
    @Operation(summary = "生成所有节点嵌入向量")
    public Result<Map<String, Object>> generateAllEmbeddings() {
        return Result.success(embeddingService.generateAllEmbeddings());
    }

    @GetMapping("/embedding/predict-link")
    @Operation(summary = "链接预测")
    public Result<Map<String, Object>> predictLink(
            @RequestParam String nodeId1,
            @RequestParam String nodeId2) {
        return Result.success(embeddingService.predictLink(nodeId1, nodeId2));
    }

    @GetMapping("/embedding/missing-links")
    @Operation(summary = "预测缺失链接")
    public Result<List<Map<String, Object>>> predictMissingLinks(
            @RequestParam(defaultValue = "10") int topK) {
        return Result.success(embeddingService.predictMissingLinks(topK));
    }

    @GetMapping("/embedding/similar-nodes/{nodeId}")
    @Operation(summary = "查找相似节点")
    public Result<List<Map<String, Object>>> findSimilarNodes(
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "10") int topK) {
        return Result.success(embeddingService.findSimilarNodes(nodeId, topK));
    }

    @PostMapping("/embedding/cluster")
    @Operation(summary = "节点聚类")
    public Result<Map<String, Object>> clusterNodes(
            @RequestParam(defaultValue = "5") int numClusters) {
        return Result.success(embeddingService.clusterNodes(numClusters));
    }

    @GetMapping("/embedding/node/{nodeId}")
    @Operation(summary = "获取节点嵌入向量")
    public Result<Map<String, Object>> getNodeEmbedding(@PathVariable String nodeId) {
        return Result.success(embeddingService.getNodeEmbeddingData(nodeId));
    }

    @GetMapping("/embedding/export")
    @Operation(summary = "导出所有节点嵌入")
    public Result<Map<String, List<Double>>> exportAllEmbeddings() {
        return Result.success(embeddingService.exportAllEmbeddings());
    }

    // ========== GraphRAG ==========

    @PostMapping("/graphrag/qa")
    @Operation(summary = "GraphRAG问答", description = "融合知识图谱和向量检索的增强问答")
    public Result<Map<String, Object>> graphRagQa(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK) {
        return Result.success(graphRagService.graphRagQa(question, topK));
    }

    @PostMapping("/graphrag/entity-enhanced-retrieval")
    @Operation(summary = "实体增强检索")
    public Result<Map<String, Object>> entityEnhancedRetrieval(@RequestParam String question) {
        return Result.success(graphRagService.entityEnhancedRetrieval(question));
    }

    @PostMapping("/graphrag/guided-retrieval")
    @Operation(summary = "图谱引导的检索路径")
    public Result<Map<String, Object>> graphGuidedRetrieval(
            @RequestParam String question,
            @RequestParam(defaultValue = "3") int maxPathLength) {
        return Result.success(graphRagService.graphGuidedRetrieval(question, maxPathLength));
    }

    @PostMapping("/graphrag/enhanced-ranking")
    @Operation(summary = "图谱增强排序融合")
    public Result<List<Map<String, Object>>> graphEnhancedRanking(
            @RequestParam String question,
            @RequestBody List<Map<String, Object>> vectorResults) {
        return Result.success(graphRagService.graphEnhancedRanking(question, vectorResults));
    }

    @GetMapping("/graphrag/stats")
    @Operation(summary = "获取GraphRAG统计信息")
    public Result<Map<String, Object>> getGraphRagStats() {
        return Result.success(graphRagService.getGraphRagStats());
    }

    // ========== 图谱演化 ==========

    @PostMapping("/evolution/incremental-update")
    @Operation(summary = "增量更新知识图谱")
    public Result<Map<String, Object>> incrementalUpdate(
            @RequestParam String text,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String documentName) {
        return Result.success(evolutionService.incrementalUpdate(text, documentId, documentName));
    }

    @PostMapping("/evolution/recalculate-core")
    @Operation(summary = "重新计算核心节点")
    public Result<Map<String, Object>> recalculateCoreNodes() {
        return Result.success(evolutionService.recalculateCoreNodes());
    }

    @PostMapping("/evolution/cleanup-relations")
    @Operation(summary = "清理低置信度关系")
    public Result<Map<String, Object>> cleanupRelations(
            @RequestParam(defaultValue = "0.3") double threshold) {
        return Result.success(evolutionService.cleanupLowConfidenceRelations(threshold));
    }

    @PostMapping("/evolution/cleanup-isolated")
    @Operation(summary = "清理孤立节点")
    public Result<Map<String, Object>> cleanupIsolatedNodes() {
        return Result.success(evolutionService.cleanupIsolatedNodes());
    }

    @GetMapping("/evolution/health")
    @Operation(summary = "检查图谱健康度")
    public Result<Map<String, Object>> checkGraphHealth() {
        return Result.success(evolutionService.checkGraphHealth());
    }

    @GetMapping("/evolution/log")
    @Operation(summary = "获取演化日志")
    public Result<List<Map<String, Object>>> getEvolutionLog(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(evolutionService.getEvolutionLog(limit));
    }

    @GetMapping("/evolution/trend")
    @Operation(summary = "获取图谱演化趋势")
    public Result<Map<String, Object>> getEvolutionTrend() {
        return Result.success(evolutionService.getEvolutionTrend());
    }

    @PostMapping("/evolution/trigger-full")
    @Operation(summary = "手动触发全量演化")
    public Result<Map<String, Object>> triggerFullEvolution() {
        return Result.success(evolutionService.triggerFullEvolution());
    }
}
