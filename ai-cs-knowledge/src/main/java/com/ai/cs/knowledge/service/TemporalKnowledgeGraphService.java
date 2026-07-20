package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 时序知识图谱服务
 * 支持时间维度的关系变化追踪、历史快照、时间线可视化
 * 
 * 增强功能：时序知识图谱（支持时间维度的关系变化追踪）
 */
@Slf4j
@Service
public class TemporalKnowledgeGraphService {

    private final KnowledgeGraphService graphService;

    public TemporalKnowledgeGraphService(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    // ========== 时序关系属性管理 ==========

    /**
     * 创建带时间戳的关系
     */
    public KnowledgeGraphRelation createTemporalRelation(String sourceNodeId, String targetNodeId,
                                                          String relationType, String description,
                                                          LocalDateTime validFrom, LocalDateTime validUntil) {
        KnowledgeGraphRelation relation = new KnowledgeGraphRelation();
        relation.setRelationId(UUID.randomUUID().toString());
        relation.setSourceNodeId(sourceNodeId);
        relation.setTargetNodeId(targetNodeId);
        relation.setRelationType(relationType);
        relation.setDescription(description);
        relation.setWeight(50);
        relation.setConfidence(0.8);

        // 将时间信息存入properties
        JSONObject props = new JSONObject();
        props.put("temporal", true);
        if (validFrom != null) {
            props.put("validFrom", validFrom.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (validUntil != null) {
            props.put("validUntil", validUntil.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        props.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        relation.setProperties(props.toJSONString());

        return graphService.saveRelation(relation);
    }

    /**
     * 更新关系的有效时间范围
     */
    public void updateRelationValidity(String relationId, LocalDateTime validFrom, LocalDateTime validUntil) {
        List<KnowledgeGraphRelation> allRelations = graphService.getRelationsByType(null);
        // 通过relationId查找
        allRelations.stream()
                .filter(r -> r.getRelationId().equals(relationId))
                .findFirst()
                .ifPresent(relation -> {
                    JSONObject props = relation.getProperties() != null 
                            ? JSON.parseObject(relation.getProperties()) : new JSONObject();
                    if (validFrom != null) {
                        props.put("validFrom", validFrom.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    }
                    if (validUntil != null) {
                        props.put("validUntil", validUntil.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    }
                    relation.setProperties(props.toJSONString());
                    graphService.saveRelation(relation);
                });
    }

    // ========== 历史快照查询 ==========

    /**
     * 获取指定时间点的图谱快照
     * @param snapshotTime 快照时间点
     * @return 该时间点有效的节点和关系
     */
    public Map<String, Object> getSnapshot(LocalDateTime snapshotTime) {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        List<KnowledgeGraphRelation> allRelations = getAllRelations();

        // 筛选在该时间点有效的关系
        List<KnowledgeGraphRelation> validRelations = allRelations.stream()
                .filter(rel -> isRelationValidAt(rel, snapshotTime))
                .collect(Collectors.toList());

        // 收集有效关系涉及的节点
        Set<String> validNodeIds = new HashSet<>();
        for (KnowledgeGraphRelation rel : validRelations) {
            validNodeIds.add(rel.getSourceNodeId());
            validNodeIds.add(rel.getTargetNodeId());
        }

        List<KnowledgeGraphNode> validNodes = allNodes.stream()
                .filter(n -> validNodeIds.contains(n.getNodeId()))
                .collect(Collectors.toList());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("snapshotTime", snapshotTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        snapshot.put("nodeCount", validNodes.size());
        snapshot.put("relationCount", validRelations.size());
        snapshot.put("nodes", validNodes);
        snapshot.put("relations", validRelations);
        return snapshot;
    }

    /**
     * 获取两个时间点之间的图谱变化
     * @param from 起始时间
     * @param to 结束时间
     * @return 变化摘要
     */
    public Map<String, Object> getGraphChanges(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> snapshot1 = getSnapshot(from);
        Map<String, Object> snapshot2 = getSnapshot(to);

        @SuppressWarnings("unchecked")
        List<KnowledgeGraphRelation> rels1 = (List<KnowledgeGraphRelation>) snapshot1.get("relations");
        @SuppressWarnings("unchecked")
        List<KnowledgeGraphRelation> rels2 = (List<KnowledgeGraphRelation>) snapshot2.get("relations");

        Set<String> relIds1 = rels1.stream().map(KnowledgeGraphRelation::getRelationId).collect(Collectors.toSet());
        Set<String> relIds2 = rels2.stream().map(KnowledgeGraphRelation::getRelationId).collect(Collectors.toSet());

        // 新增的关系
        Set<String> added = new HashSet<>(relIds2);
        added.removeAll(relIds1);

        // 消失的关系
        Set<String> removed = new HashSet<>(relIds1);
        removed.removeAll(relIds2);

        Map<String, Object> changes = new HashMap<>();
        changes.put("from", from.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        changes.put("to", to.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        changes.put("addedRelations", added.size());
        changes.put("removedRelations", removed.size());
        changes.put("addedRelationIds", added);
        changes.put("removedRelationIds", removed);

        // 具体变化的关系详情
        List<Map<String, Object>> addedDetails = rels2.stream()
                .filter(r -> added.contains(r.getRelationId()))
                .map(r -> Map.<String, Object>of(
                        "source", r.getSourceNodeName(),
                        "target", r.getTargetNodeName(),
                        "type", r.getRelationType()
                ))
                .collect(Collectors.toList());
        changes.put("addedDetails", addedDetails);

        List<Map<String, Object>> removedDetails = rels1.stream()
                .filter(r -> removed.contains(r.getRelationId()))
                .map(r -> Map.<String, Object>of(
                        "source", r.getSourceNodeName(),
                        "target", r.getTargetNodeName(),
                        "type", r.getRelationType()
                ))
                .collect(Collectors.toList());
        changes.put("removedDetails", removedDetails);

        return changes;
    }

    // ========== 时间线可视化 ==========

    /**
     * 获取实体的关系变化时间线
     * @param nodeId 节点ID
     * @return 时间线数据
     */
    public List<Map<String, Object>> getEntityTimeline(String nodeId) {
        List<KnowledgeGraphRelation> nodeRelations = graphService.getNodeRelations(nodeId);
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (KnowledgeGraphRelation rel : nodeRelations) {
            Map<String, Object> event = new HashMap<>();
            event.put("relationId", rel.getRelationId());
            event.put("relationType", rel.getRelationType());
            event.put("targetName", rel.getSourceNodeId().equals(nodeId) 
                    ? rel.getTargetNodeName() : rel.getSourceNodeName());
            event.put("direction", rel.getSourceNodeId().equals(nodeId) ? "outgoing" : "incoming");

            // 解析时间信息
            if (rel.getProperties() != null) {
                try {
                    JSONObject props = JSON.parseObject(rel.getProperties());
                    event.put("validFrom", props.getString("validFrom"));
                    event.put("validUntil", props.getString("validUntil"));
                    event.put("createdAt", props.getString("createdAt"));
                } catch (Exception e) {
                    log.debug("解析时间属性失败: {}", e.getMessage());
                }
            }

            timeline.add(event);
        }

        // 按时间排序
        timeline.sort((a, b) -> {
            String ta = (String) a.getOrDefault("validFrom", "");
            String tb = (String) b.getOrDefault("validFrom", "");
            return ta.compareTo(tb);
        });

        return timeline;
    }

    /**
     * 获取全局时间线（按时间排序的所有关系创建事件）
     * @param limit 返回数量限制
     * @return 时间线事件列表
     */
    public List<Map<String, Object>> getGlobalTimeline(int limit) {
        List<KnowledgeGraphRelation> allRelations = getAllRelations();
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (KnowledgeGraphRelation rel : allRelations) {
            Map<String, Object> event = new HashMap<>();
            event.put("relationId", rel.getRelationId());
            event.put("source", rel.getSourceNodeName());
            event.put("target", rel.getTargetNodeName());
            event.put("type", rel.getRelationType());
            event.put("weight", rel.getWeight());
            event.put("confidence", rel.getConfidence());

            if (rel.getProperties() != null) {
                try {
                    JSONObject props = JSON.parseObject(rel.getProperties());
                    event.put("validFrom", props.getString("validFrom"));
                    event.put("validUntil", props.getString("validUntil"));
                    event.put("createdAt", props.getString("createdAt"));
                } catch (Exception ignored) {}
            }

            timeline.add(event);
        }

        timeline.sort((a, b) -> {
            String ta = (String) a.getOrDefault("createdAt", "");
            String tb = (String) b.getOrDefault("createdAt", "");
            return tb.compareTo(ta); // 最新在前
        });

        return timeline.size() > limit ? timeline.subList(0, limit) : timeline;
    }

    // ========== 关系演化分析 ==========

    /**
     * 分析关系的演化模式
     * @param nodeId 节点ID
     * @return 演化分析
     */
    public Map<String, Object> analyzeRelationEvolution(String nodeId) {
        List<Map<String, Object>> timeline = getEntityTimeline(nodeId);
        KnowledgeGraphNode node = graphService.findById(nodeId);

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("nodeId", nodeId);
        analysis.put("nodeName", node != null ? node.getName() : "未知");
        analysis.put("totalEvents", timeline.size());

        // 统计关系类型分布
        Map<String, Long> typeDistribution = timeline.stream()
                .collect(Collectors.groupingBy(e -> (String) e.get("relationType"), Collectors.counting()));
        analysis.put("typeDistribution", typeDistribution);

        // 统计方向分布
        Map<String, Long> directionDistribution = timeline.stream()
                .collect(Collectors.groupingBy(e -> (String) e.get("direction"), Collectors.counting()));
        analysis.put("directionDistribution", directionDistribution);

        // 识别关系变化趋势（活跃度上升/下降）
        long incoming = directionDistribution.getOrDefault("incoming", 0L);
        long outgoing = directionDistribution.getOrDefault("outgoing", 0L);
        analysis.put("trend", incoming > outgoing ? "被引用增加" : outgoing > incoming ? "主动关联增加" : "均衡");

        return analysis;
    }

    /**
     * 预测关系演化趋势
     * @param relationType 关系类型
     * @return 趋势预测
     */
    public Map<String, Object> predictRelationTrend(String relationType) {
        List<KnowledgeGraphRelation> relations = graphService.getRelationsByType(relationType);
        
        // 简单趋势分析：按创建时间分组统计
        Map<String, Long> trendData = new LinkedHashMap<>();
        for (KnowledgeGraphRelation rel : relations) {
            if (rel.getProperties() != null) {
                try {
                    JSONObject props = JSON.parseObject(rel.getProperties());
                    String createdAt = props.getString("createdAt");
                    if (createdAt != null && createdAt.length() >= 10) {
                        String date = createdAt.substring(0, 10); // YYYY-MM-DD
                        trendData.merge(date, 1L, Long::sum);
                    }
                } catch (Exception ignored) {}
            }
        }

        Map<String, Object> prediction = new HashMap<>();
        prediction.put("relationType", relationType);
        prediction.put("totalCount", relations.size());
        prediction.put("trendData", trendData);

        // 简单趋势判断
        if (trendData.size() >= 2) {
            List<Long> values = new ArrayList<>(trendData.values());
            long latest = values.get(values.size() - 1);
            long previous = values.get(values.size() - 2);
            prediction.put("trend", latest > previous ? "上升" : latest < previous ? "下降" : "稳定");
            prediction.put("changeRate", previous > 0 ? 
                    String.format("%.1f%%", (latest - previous) * 100.0 / previous) : "N/A");
        }

        return prediction;
    }

    // ========== 时序约束验证 ==========

    /**
     * 验证图谱中的时序一致性
     * 检查：前置关系的时间顺序是否正确
     * @return 验证报告
     */
    public Map<String, Object> validateTemporalConsistency() {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<KnowledgeGraphRelation> precedenceRelations = graphService.getRelationsByType("前置");

        for (KnowledgeGraphRelation rel : precedenceRelations) {
            if (rel.getProperties() != null) {
                try {
                    JSONObject props = JSON.parseObject(rel.getProperties());
                    String validFrom = props.getString("validFrom");
                    if (validFrom != null) {
                        // 检查目标节点是否在源节点之后才出现
                        List<KnowledgeGraphRelation> targetRels = graphService.getNodeRelations(rel.getTargetNodeId());
                        for (KnowledgeGraphRelation tr : targetRels) {
                            if (tr.getProperties() != null) {
                                JSONObject tp = JSON.parseObject(tr.getProperties());
                                String tvf = tp.getString("validFrom");
                                if (tvf != null && tvf.compareTo(validFrom) < 0) {
                                    Map<String, Object> issue = new HashMap<>();
                                    issue.put("type", "时序不一致");
                                    issue.put("relation", rel.getSourceNodeName() + " 前置 " + rel.getTargetNodeName());
                                    issue.put("detail", "目标节点关系时间早于前置关系时间");
                                    issues.add(issue);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("时序验证异常: {}", e.getMessage());
                }
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("valid", issues.isEmpty());
        report.put("issueCount", issues.size());
        report.put("issues", issues);
        return report;
    }

    // ========== 辅助方法 ==========

    /**
     * 获取所有关系
     */
    private List<KnowledgeGraphRelation> getAllRelations() {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        Set<String> allRelationIds = new HashSet<>();
        List<KnowledgeGraphRelation> allRelations = new ArrayList<>();

        for (KnowledgeGraphNode node : allNodes) {
            List<KnowledgeGraphRelation> nodeRels = graphService.getNodeRelations(node.getNodeId());
            for (KnowledgeGraphRelation rel : nodeRels) {
                if (allRelationIds.add(rel.getRelationId())) {
                    allRelations.add(rel);
                }
            }
        }

        return allRelations;
    }

    /**
     * 判断关系在指定时间点是否有效
     */
    private boolean isRelationValidAt(KnowledgeGraphRelation relation, LocalDateTime time) {
        if (relation.getProperties() == null) {
            return true; // 没有时间约束的关系始终有效
        }

        try {
            JSONObject props = JSON.parseObject(relation.getProperties());
            
            String validFromStr = props.getString("validFrom");
            String validUntilStr = props.getString("validUntil");

            if (validFromStr != null) {
                LocalDateTime validFrom = LocalDateTime.parse(validFromStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                if (time.isBefore(validFrom)) return false;
            }

            if (validUntilStr != null) {
                LocalDateTime validUntil = LocalDateTime.parse(validUntilStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                if (time.isAfter(validUntil)) return false;
            }

            return true;
        } catch (Exception e) {
            return true; // 解析失败视为有效
        }
    }
}
