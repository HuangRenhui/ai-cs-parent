package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱自动更新与演化服务
 * 支持增量学习、过期关系清理、核心节点动态调整、图谱健康度监控
 * 
 * 增强功能：图谱自动更新与演化（增量学习）
 */
@Slf4j
@Service
public class GraphEvolutionService {

    private final KnowledgeGraphService graphService;
    private final GraphReasoningEngine reasoningEngine;
    private final TemporalKnowledgeGraphService temporalService;

    // 演化日志
    private final List<Map<String, Object>> evolutionLog = new ArrayList<>();
    private static final int MAX_LOG_SIZE = 1000;

    public GraphEvolutionService(KnowledgeGraphService graphService,
                                  GraphReasoningEngine reasoningEngine,
                                  TemporalKnowledgeGraphService temporalService) {
        this.graphService = graphService;
        this.reasoningEngine = reasoningEngine;
        this.temporalService = temporalService;
    }

    // ========== 增量学习 ==========

    /**
     * 从新文本增量更新知识图谱
     * @param text 新文本内容
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @return 更新统计
     */
    public Map<String, Object> incrementalUpdate(String text, String documentId, String documentName) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 1. 提取新实体和关系
            Map<String, Object> extracted = graphService.extractEntities(text);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> entities = (List<Map<String, String>>) extracted.get("entities");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> relations = (List<Map<String, String>>) extracted.get("relations");

            int newNodes = 0;
            int mergedNodes = 0;
            int newRelations = 0;

            // 2. 增量合并实体（去重）
            if (entities != null) {
                for (Map<String, String> entity : entities) {
                    KnowledgeGraphNode existing = graphService.findByName(entity.get("name"));
                    if (existing != null) {
                        // 更新现有实体
                        if (entity.get("description") != null && 
                                (existing.getDescription() == null || existing.getDescription().isEmpty())) {
                            existing.setDescription(entity.get("description"));
                        }
                        existing.setImportance(Math.min(100,
                                (existing.getImportance() != null ? existing.getImportance() : 50) + 5));
                        existing.setSourceDocumentId(documentId);
                        existing.setSourceDocumentName(documentName);
                        graphService.saveNode(existing);
                        mergedNodes++;
                    } else {
                        // 创建新实体
                        KnowledgeGraphNode node = new KnowledgeGraphNode();
                        node.setNodeId(UUID.randomUUID().toString());
                        node.setName(entity.get("name"));
                        node.setLabel(entity.getOrDefault("type", "概念"));
                        node.setDescription(entity.getOrDefault("description", ""));
                        node.setSourceDocumentId(documentId);
                        node.setSourceDocumentName(documentName);
                        node.setImportance(40);
                        node.setIsCore(0);
                        node.setStatus(1);
                        graphService.saveNode(node);
                        newNodes++;
                    }
                }
            }

            // 3. 增量合并关系
            if (relations != null) {
                for (Map<String, String> rel : relations) {
                    KnowledgeGraphNode sourceNode = graphService.findByName(rel.get("source"));
                    KnowledgeGraphNode targetNode = graphService.findByName(rel.get("target"));

                    if (sourceNode != null && targetNode != null) {
                        // 检查是否已存在相同关系
                        List<KnowledgeGraphRelation> existingRels = graphService.getNodeRelations(sourceNode.getNodeId());
                        boolean exists = existingRels.stream()
                                .anyMatch(r -> r.getTargetNodeId().equals(targetNode.getNodeId())
                                        && r.getRelationType().equals(rel.getOrDefault("type", "相关")));

                        if (!exists) {
                            KnowledgeGraphRelation relation = new KnowledgeGraphRelation();
                            relation.setRelationId(UUID.randomUUID().toString());
                            relation.setSourceNodeId(sourceNode.getNodeId());
                            relation.setTargetNodeId(targetNode.getNodeId());
                            relation.setSourceNodeName(sourceNode.getName());
                            relation.setTargetNodeName(targetNode.getName());
                            relation.setRelationType(rel.getOrDefault("type", "相关"));
                            relation.setDescription(rel.getOrDefault("description", ""));
                            relation.setWeight(50);
                            relation.setConfidence(0.6);
                            relation.setSourceDocumentId(documentId);
                            relation.setStatus(1);

                            // 添加时间戳
                            JSONObject props = new JSONObject();
                            props.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            props.put("incremental", true);
                            relation.setProperties(props.toJSONString());

                            graphService.saveRelation(relation);
                            newRelations++;
                        }
                    }
                }
            }

            stats.put("success", true);
            stats.put("newNodes", newNodes);
            stats.put("mergedNodes", mergedNodes);
            stats.put("newRelations", newRelations);
            stats.put("duration", java.time.Duration.between(startTime, LocalDateTime.now()).toMillis() + "ms");

            // 记录演化日志
            logEvolution("INCREMENTAL_UPDATE", stats);

        } catch (Exception e) {
            log.error("增量更新失败", e);
            stats.put("success", false);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    // ========== 定时演化任务 ==========

    /**
     * 每日自动演化任务
     * 1. 执行推理规则
     * 2. 重新计算核心节点
     * 3. 清理低置信度关系
     * 4. 健康度检查
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void dailyEvolution() {
        log.info("开始每日图谱自动演化任务");
        Map<String, Object> report = new HashMap<>();
        report.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try {
            // 1. 执行推理
            Map<String, Object> reasoningResult = reasoningEngine.executeAllRules();
            report.put("reasoning", reasoningResult);

            // 2. 重新计算核心节点
            Map<String, Object> coreUpdateResult = recalculateCoreNodes();
            report.put("coreNodeUpdate", coreUpdateResult);

            // 3. 清理低置信度关系
            Map<String, Object> cleanupResult = cleanupLowConfidenceRelations(0.3);
            report.put("cleanup", cleanupResult);

            // 4. 健康度检查
            Map<String, Object> healthResult = checkGraphHealth();
            report.put("health", healthResult);

            log.info("每日图谱演化完成: {}", report);
            logEvolution("DAILY_EVOLUTION", report);
        } catch (Exception e) {
            log.error("每日演化任务失败", e);
            report.put("error", e.getMessage());
        }
    }

    /**
     * 每周深度演化任务
     */
    @Scheduled(cron = "0 0 2 * * SUN") // 每周日凌晨2点
    public void weeklyDeepEvolution() {
        log.info("开始每周深度演化任务");

        try {
            // 1. 全量推理
            Map<String, Object> reasoningResult = reasoningEngine.executeAllRules();
            log.info("全量推理完成: {}", reasoningResult.get("totalInferredRelations"));

            // 2. 实体消歧
            List<Map<String, Object>> disambiguationSuggestions = reasoningEngine.entityDisambiguation();
            log.info("发现 {} 条消歧建议", disambiguationSuggestions.size());

            // 3. 自动合并高置信度重复实体
            int autoMerged = 0;
            for (Map<String, Object> suggestion : disambiguationSuggestions) {
                double similarity = (double) suggestion.get("similarity");
                if (similarity > 0.9) {
                    try {
                        reasoningEngine.mergeEntities(
                                (String) suggestion.get("node1Id"),
                                (String) suggestion.get("node2Id")
                        );
                        autoMerged++;
                    } catch (Exception e) {
                        log.warn("自动合并失败: {}", e.getMessage());
                    }
                }
            }

            // 4. 检测环形依赖
            List<Map<String, Object>> circularDeps = findCircularDependencies();
            
            Map<String, Object> report = new HashMap<>();
            report.put("autoMergedEntities", autoMerged);
            report.put("disambiguationSuggestions", disambiguationSuggestions.size());
            report.put("circularDependencies", circularDeps.size());
            
            log.info("每周深度演化完成: {}", report);
            logEvolution("WEEKLY_DEEP_EVOLUTION", report);
        } catch (Exception e) {
            log.error("每周深度演化任务失败", e);
        }
    }

    // ========== 核心节点动态调整 ==========

    /**
     * 重新计算核心节点
     * 基于：重要性分数 + 连接度 + 被引用次数
     */
    public Map<String, Object> recalculateCoreNodes() {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        int updatedCount = 0;

        // 计算每个节点的综合分数
        Map<String, Double> scores = new HashMap<>();
        int maxConnections = 0;

        for (KnowledgeGraphNode node : allNodes) {
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
            int connections = relations.size();
            maxConnections = Math.max(maxConnections, connections);

            double score = (node.getImportance() != null ? node.getImportance() : 50) * 0.4
                    + (connections * 5.0) * 0.4
                    + (node.getIsCore() != null && node.getIsCore() == 1 ? 20 : 0) * 0.2;
            scores.put(node.getNodeId(), score);
        }

        // 排序并标记前20%为核心节点
        List<Map.Entry<String, Double>> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        int coreThreshold = Math.max(1, (int) (allNodes.size() * 0.2));
        Set<String> newCoreIds = new HashSet<>();
        for (int i = 0; i < coreThreshold && i < sorted.size(); i++) {
            newCoreIds.add(sorted.get(i).getKey());
        }

        // 更新节点状态
        for (KnowledgeGraphNode node : allNodes) {
            int newCore = newCoreIds.contains(node.getNodeId()) ? 1 : 0;
            int newImportance = Math.min(100, (int) Math.round(scores.get(node.getNodeId())));

            if ((node.getIsCore() == null || node.getIsCore() != newCore) 
                    || (node.getImportance() == null || Math.abs(node.getImportance() - newImportance) > 10)) {
                node.setIsCore(newCore);
                node.setImportance(newImportance);
                graphService.saveNode(node);
                updatedCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalNodes", allNodes.size());
        result.put("coreNodes", newCoreIds.size());
        result.put("updatedNodes", updatedCount);
        result.put("maxConnections", maxConnections);
        return result;
    }

    // ========== 关系清理 ==========

    /**
     * 清理低置信度关系
     * @param threshold 置信度阈值
     */
    public Map<String, Object> cleanupLowConfidenceRelations(double threshold) {
        int removedCount = 0;
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        Set<String> processedRelIds = new HashSet<>();

        for (KnowledgeGraphNode node : allNodes) {
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
            for (KnowledgeGraphRelation rel : relations) {
                if (processedRelIds.contains(rel.getRelationId())) continue;
                processedRelIds.add(rel.getRelationId());

                if (rel.getConfidence() != null && rel.getConfidence() < threshold) {
                    // 低置信度且权重也低
                    if (rel.getWeight() != null && rel.getWeight() < 30) {
                        graphService.deleteRelation(rel.getRelationId());
                        removedCount++;
                        log.info("清理低质量关系: {} -[{}]-> {} (置信度: {}, 权重: {})",
                                rel.getSourceNodeName(), rel.getRelationType(),
                                rel.getTargetNodeName(), rel.getConfidence(), rel.getWeight());
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("removedRelations", removedCount);
        result.put("threshold", threshold);
        return result;
    }

    /**
     * 清理孤立节点
     */
    public Map<String, Object> cleanupIsolatedNodes() {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        int removedCount = 0;

        for (KnowledgeGraphNode node : allNodes) {
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
            if (relations.isEmpty()) {
                // 孤立节点，且重要性低
                if (node.getImportance() != null && node.getImportance() < 30) {
                    graphService.deleteNode(node.getNodeId());
                    removedCount++;
                    log.info("清理孤立节点: {} (重要性: {})", node.getName(), node.getImportance());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("removedNodes", removedCount);
        result.put("totalNodes", allNodes.size());
        return result;
    }

    // ========== 健康度监控 ==========

    /**
     * 检查图谱健康度
     * @return 健康度报告
     */
    public Map<String, Object> checkGraphHealth() {
        Map<String, Object> health = new HashMap<>();

        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        Map<String, Object> graphStats = graphService.getGraphStatistics();

        long totalNodes = (long) graphStats.getOrDefault("totalNodes", 0);
        long totalRelations = (long) graphStats.getOrDefault("totalRelations", 0);

        // 1. 密度检查
        double density = totalNodes > 0 ? (double) totalRelations / totalNodes : 0;
        health.put("graphDensity", String.format("%.2f", density));
        health.put("densityStatus", density > 2.0 ? "良好" : density > 1.0 ? "一般" : "偏低");

        // 2. 孤立节点比例
        long isolatedCount = allNodes.stream()
                .filter(n -> {
                    List<KnowledgeGraphRelation> rels = graphService.getNodeRelations(n.getNodeId());
                    return rels.isEmpty();
                }).count();
        double isolatedRatio = totalNodes > 0 ? (double) isolatedCount / totalNodes : 0;
        health.put("isolatedNodeRatio", String.format("%.1f%%", isolatedRatio * 100));
        health.put("isolatedStatus", isolatedRatio < 0.1 ? "良好" : isolatedRatio < 0.3 ? "警告" : "需要清理");

        // 3. 低置信度关系比例
        long lowConfCount = 0;
        Set<String> processed = new HashSet<>();
        for (KnowledgeGraphNode node : allNodes) {
            for (KnowledgeGraphRelation rel : graphService.getNodeRelations(node.getNodeId())) {
                if (processed.add(rel.getRelationId())) {
                    if (rel.getConfidence() != null && rel.getConfidence() < 0.5) {
                        lowConfCount++;
                    }
                }
            }
        }
        double lowConfRatio = totalRelations > 0 ? (double) lowConfCount / totalRelations : 0;
        health.put("lowConfidenceRatio", String.format("%.1f%%", lowConfRatio * 100));

        // 4. 核心节点覆盖
        long coreCount = allNodes.stream().filter(n -> n.getIsCore() != null && n.getIsCore() == 1).count();
        health.put("coreNodeCount", coreCount);
        health.put("coreNodeRatio", totalNodes > 0 ? String.format("%.1f%%", coreCount * 100.0 / totalNodes) : "0%");

        // 5. 整体评分
        double healthScore = 100.0;
        if (density < 1.0) healthScore -= 20;
        if (isolatedRatio > 0.2) healthScore -= 15;
        if (lowConfRatio > 0.3) healthScore -= 15;
        if (coreCount < 3 && totalNodes > 10) healthScore -= 10;
        health.put("overallScore", Math.max(0, healthScore));
        health.put("status", healthScore > 80 ? "健康" : healthScore > 60 ? "一般" : "需要优化");

        return health;
    }

    // ========== 演化日志与回滚 ==========

    /**
     * 获取演化日志
     * @param limit 返回条数
     * @return 日志列表
     */
    public List<Map<String, Object>> getEvolutionLog(int limit) {
        synchronized (evolutionLog) {
            if (evolutionLog.size() <= limit) {
                return new ArrayList<>(evolutionLog);
            }
            return new ArrayList<>(evolutionLog.subList(evolutionLog.size() - limit, evolutionLog.size()));
        }
    }

    /**
     * 获取图谱演化趋势
     * @return 趋势数据
     */
    public Map<String, Object> getEvolutionTrend() {
        Map<String, Object> trend = new HashMap<>();

        // 从日志中提取趋势
        synchronized (evolutionLog) {
            List<Map<String, Object>> dailyEvolutions = evolutionLog.stream()
                    .filter(e -> "DAILY_EVOLUTION".equals(e.get("type")))
                    .collect(Collectors.toList());

            List<Map<String, Object>> incrementalUpdates = evolutionLog.stream()
                    .filter(e -> "INCREMENTAL_UPDATE".equals(e.get("type")))
                    .collect(Collectors.toList());

            trend.put("totalEvolutions", evolutionLog.size());
            trend.put("dailyEvolutions", dailyEvolutions.size());
            trend.put("incrementalUpdates", incrementalUpdates.size());

            // 最近10次增量更新的统计
            List<Map<String, Object>> recentUpdates = incrementalUpdates.stream()
                    .skip(Math.max(0, incrementalUpdates.size() - 10))
                    .collect(Collectors.toList());
            
            long totalNewNodes = recentUpdates.stream()
                    .mapToLong(u -> ((Number) u.getOrDefault("newNodes", 0)).longValue())
                    .sum();
            long totalNewRelations = recentUpdates.stream()
                    .mapToLong(u -> ((Number) u.getOrDefault("newRelations", 0)).longValue())
                    .sum();

            trend.put("recentNewNodes", totalNewNodes);
            trend.put("recentNewRelations", totalNewRelations);
        }

        // 当前图谱状态
        Map<String, Object> currentStats = graphService.getGraphStatistics();
        trend.put("currentNodes", currentStats.get("totalNodes"));
        trend.put("currentRelations", currentStats.get("totalRelations"));

        return trend;
    }

    // ========== 手动触发 ==========

    /**
     * 手动触发全量演化
     */
    public Map<String, Object> triggerFullEvolution() {
        Map<String, Object> report = new HashMap<>();
        report.put("triggerTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        report.put("reasoning", reasoningEngine.executeAllRules());
        report.put("coreNodeUpdate", recalculateCoreNodes());
        report.put("cleanup", cleanupLowConfidenceRelations(0.3));
        report.put("health", checkGraphHealth());

        logEvolution("MANUAL_FULL_EVOLUTION", report);
        return report;
    }

    // ========== 辅助方法 ==========

    /**
     * 检测环形依赖
     */
    private List<Map<String, Object>> findCircularDependencies() {
        List<Map<String, Object>> cycles = new ArrayList<>();
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();

        // DFS检测环
        for (KnowledgeGraphNode startNode : allNodes) {
            Set<String> visited = new HashSet<>();
            List<String> path = new ArrayList<>();
            detectCycle(startNode.getNodeId(), startNode.getNodeId(), visited, path, cycles, 0, 8);
            if (cycles.size() >= 20) break; // 限制检测数量
        }

        return cycles;
    }

    private void detectCycle(String startId, String currentId, Set<String> visited,
                              List<String> path, List<Map<String, Object>> cycles,
                              int depth, int maxDepth) {
        if (depth > maxDepth || visited.contains(currentId)) {
            if (currentId.equals(startId) && depth > 1 && depth <= maxDepth) {
                Map<String, Object> cycle = new HashMap<>();
                cycle.put("length", depth);
                cycle.put("path", new ArrayList<>(path));
                cycles.add(cycle);
            }
            return;
        }

        visited.add(currentId);
        KnowledgeGraphNode node = graphService.findById(currentId);
        if (node != null) {
            path.add(node.getName());
        }

        List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(currentId);
        for (KnowledgeGraphRelation rel : relations) {
            String nextId = rel.getSourceNodeId().equals(currentId)
                    ? rel.getTargetNodeId() : rel.getSourceNodeId();
            if (nextId.equals(startId) || !visited.contains(nextId)) {
                detectCycle(startId, nextId, visited, path, cycles, depth + 1, maxDepth);
            }
        }

        if (!path.isEmpty()) path.remove(path.size() - 1);
        visited.remove(currentId);
    }

    /**
     * 记录演化日志
     */
    private void logEvolution(String type, Map<String, Object> data) {
        synchronized (evolutionLog) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            entry.putAll(data);

            evolutionLog.add(entry);
            if (evolutionLog.size() > MAX_LOG_SIZE) {
                evolutionLog.remove(0);
            }
        }
    }
}
