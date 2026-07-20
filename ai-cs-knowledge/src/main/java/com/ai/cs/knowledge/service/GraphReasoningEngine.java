package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 图谱推理引擎
 * 提供基于规则的推理、逻辑推理链、路径查找、实体消歧等能力
 * 
 * 增强功能：图谱推理引擎（基于规则的推理、路径查找）
 */
@Slf4j
@Service
public class GraphReasoningEngine {

    private final KnowledgeGraphService graphService;

    public GraphReasoningEngine(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    // ========== 推理规则定义 ==========

    /**
     * 推理规则
     */
    public static class Rule {
        private final String name;
        private final String description;
        private final List<Condition> conditions;
        private final Conclusion conclusion;

        public Rule(String name, String description, List<Condition> conditions, Conclusion conclusion) {
            this.name = name;
            this.description = description;
            this.conditions = conditions;
            this.conclusion = conclusion;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<Condition> getConditions() { return conditions; }
        public Conclusion getConclusion() { return conclusion; }
    }

    /**
     * 条件
     */
    public static class Condition {
        private final String type;     // path_exists, node_has_label, relation_exists, property_equals
        private final Map<String, String> params;

        public Condition(String type, Map<String, String> params) {
            this.type = type;
            this.params = params;
        }

        public String getType() { return type; }
        public Map<String, String> getParams() { return params; }
    }

    /**
     * 结论
     */
    public static class Conclusion {
        private final String type;     // new_relation, set_property, new_node
        private final Map<String, String> params;

        public Conclusion(String type, Map<String, String> params) {
            this.type = type;
            this.params = params;
        }

        public String getType() { return type; }
        public Map<String, String> getParams() { return params; }
    }

    /**
     * 推理结果
     */
    public static class ReasoningResult {
        private final String ruleName;
        private final boolean applied;
        private final String description;
        private final List<Map<String, Object>> inferredRelations;

        public ReasoningResult(String ruleName, boolean applied, String description,
                                List<Map<String, Object>> inferredRelations) {
            this.ruleName = ruleName;
            this.applied = applied;
            this.description = description;
            this.inferredRelations = inferredRelations;
        }

        public String getRuleName() { return ruleName; }
        public boolean isApplied() { return applied; }
        public String getDescription() { return description; }
        public List<Map<String, Object>> getInferredRelations() { return inferredRelations; }
    }

    // ========== 内置推理规则 ==========

    /**
     * 获取所有内置推理规则
     */
    public List<Rule> getBuiltinRules() {
        List<Rule> rules = new ArrayList<>();

        // 规则1：传递性推理 (A 包含 B, B 包含 C => A 包含 C)
        rules.add(new Rule(
                "transitive_containment",
                "传递包含关系：如果A包含B且B包含C，则推断A包含C",
                List.of(
                        new Condition("relation_exists", Map.of("source", "?A", "target", "?B", "type", "包含")),
                        new Condition("relation_exists", Map.of("source", "?B", "target", "?C", "type", "包含"))
                ),
                new Conclusion("new_relation", Map.of("source", "?A", "target", "?C", "type", "包含"))
        ));

        // 规则2：依赖传递 (A 依赖 B, B 依赖 C => A 依赖 C)
        rules.add(new Rule(
                "transitive_dependency",
                "传递依赖关系：如果A依赖B且B依赖C，则推断A依赖C",
                List.of(
                        new Condition("relation_exists", Map.of("source", "?A", "target", "?B", "type", "依赖")),
                        new Condition("relation_exists", Map.of("source", "?B", "target", "?C", "type", "依赖"))
                ),
                new Conclusion("new_relation", Map.of("source", "?A", "target", "?C", "type", "依赖"))
        ));

        // 规则3：对称关系推理 (A 等同于 B => B 等同于 A)
        rules.add(new Rule(
                "symmetric_equivalence",
                "对称等同关系：如果A等同于B，则B等同于A",
                List.of(
                        new Condition("relation_exists", Map.of("source", "?A", "target", "?B", "type", "等同于"))
                ),
                new Conclusion("new_relation", Map.of("source", "?B", "target", "?A", "type", "等同于"))
        ));

        // 规则4：概念层级推理 (A 属于 B 且 B 是概念 => A 继承B的属性)
        rules.add(new Rule(
                "hierarchical_inheritance",
                "层级继承：如果A属于B，则A继承B的属性",
                List.of(
                        new Condition("relation_exists", Map.of("source", "?A", "target", "?B", "type", "属于"))
                ),
                new Conclusion("set_property", Map.of("node", "?A", "inheritsFrom", "?B"))
        ));

        // 规则5：前置链推理 (A 前置 B, B 前置 C => A 前置 C)
        rules.add(new Rule(
                "transitive_precedence",
                "传递前置关系：如果A前置B且B前置C，则A前置C",
                List.of(
                        new Condition("relation_exists", Map.of("source", "?A", "target", "?B", "type", "前置")),
                        new Condition("relation_exists", Map.of("source", "?B", "target", "?C", "type", "前置"))
                ),
                new Conclusion("new_relation", Map.of("source", "?A", "target", "?C", "type", "前置"))
        ));

        // 规则6：引用传递 (A 引用 B, B 包含 C => A 间接引用 C)
        rules.add(new Rule(
                "indirect_reference",
                "间接引用：如果A引用B且B包含C，则A间接引用C",
                List.of(
                        new Condition("relation_exists", Map.of("source", "?A", "target", "?B", "type", "引用")),
                        new Condition("relation_exists", Map.of("source", "?B", "target", "?C", "type", "包含"))
                ),
                new Conclusion("new_relation", Map.of("source", "?A", "target", "?C", "type", "引用"))
        ));

        return rules;
    }

    // ========== 推理执行 ==========

    /**
     * 执行所有推理规则
     * @return 推理结果汇总
     */
    public Map<String, Object> executeAllRules() {
        List<ReasoningResult> allResults = new ArrayList<>();
        List<Rule> rules = getBuiltinRules();

        for (Rule rule : rules) {
            try {
                ReasoningResult result = executeRule(rule);
                allResults.add(result);
            } catch (Exception e) {
                log.error("执行规则 {} 失败: {}", rule.getName(), e.getMessage());
                allResults.add(new ReasoningResult(rule.getName(), false, 
                        "执行失败: " + e.getMessage(), Collections.emptyList()));
            }
        }

        long appliedCount = allResults.stream().filter(ReasoningResult::isApplied).count();
        long totalInferred = allResults.stream()
                .mapToLong(r -> r.getInferredRelations().size()).sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRules", rules.size());
        summary.put("appliedRules", appliedCount);
        summary.put("totalInferredRelations", totalInferred);
        summary.put("results", allResults);
        return summary;
    }

    /**
     * 执行单个推理规则
     */
    public ReasoningResult executeRule(Rule rule) {
        List<Map<String, Object>> inferredRelations = new ArrayList<>();

        try {
            if (rule.getName().equals("transitive_containment")) {
                inferredRelations = inferTransitiveRelations("包含");
            } else if (rule.getName().equals("transitive_dependency")) {
                inferredRelations = inferTransitiveRelations("依赖");
            } else if (rule.getName().equals("symmetric_equivalence")) {
                inferredRelations = inferSymmetricRelations("等同于");
            } else if (rule.getName().equals("hierarchical_inheritance")) {
                inferredRelations = inferInheritance();
            } else if (rule.getName().equals("transitive_precedence")) {
                inferredRelations = inferTransitiveRelations("前置");
            } else if (rule.getName().equals("indirect_reference")) {
                inferredRelations = inferIndirectReferences();
            }

            boolean applied = !inferredRelations.isEmpty();
            if (applied) {
                log.info("推理规则 [{}] 应用成功，推断出 {} 条新关系", rule.getName(), inferredRelations.size());
            }

            return new ReasoningResult(rule.getName(), applied,
                    applied ? "成功推断" + inferredRelations.size() + "条新关系" : "无新关系可推断",
                    inferredRelations);
        } catch (Exception e) {
            log.error("推理规则 [{}] 执行异常: {}", rule.getName(), e.getMessage());
            return new ReasoningResult(rule.getName(), false, "执行异常: " + e.getMessage(), Collections.emptyList());
        }
    }

    // ========== 路径查找推理 ==========

    /**
     * 查找两个实体之间的所有路径（带推理）
     * @param sourceName 源实体名称
     * @param targetName 目标实体名称
     * @param maxDepth 最大深度
     * @return 路径列表
     */
    public List<Map<String, Object>> findAllPaths(String sourceName, String targetName, int maxDepth) {
        KnowledgeGraphNode source = graphService.findByName(sourceName);
        KnowledgeGraphNode target = graphService.findByName(targetName);

        if (source == null || target == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> paths = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<String> currentPath = new ArrayList<>();
        List<String> currentRelations = new ArrayList<>();

        dfsFindPaths(source.getNodeId(), target.getNodeId(), visited, currentPath, 
                currentRelations, paths, maxDepth, 0);

        return paths;
    }

    /**
     * 查找概念层级路径（向上或向下查找）
     * @param nodeName 节点名称
     * @param direction "up" 向上（找父概念） / "down" 向下（找子概念）
     * @return 层级路径
     */
    public Map<String, Object> findHierarchyPath(String nodeName, String direction) {
        KnowledgeGraphNode node = graphService.findByName(nodeName);
        if (node == null) {
            return Map.of("found", false, "message", "节点不存在");
        }

        List<Map<String, Object>> hierarchy = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        buildHierarchy(node.getNodeId(), direction, hierarchy, visited, 0, 5);

        Map<String, Object> result = new HashMap<>();
        result.put("found", !hierarchy.isEmpty());
        result.put("startNode", nodeName);
        result.put("direction", direction);
        result.put("hierarchy", hierarchy);
        return result;
    }

    // ========== 实体消歧 ==========

    /**
     * 实体消歧：识别图谱中可能指向同一实体的不同节点
     * @return 消歧建议
     */
    public List<Map<String, Object>> entityDisambiguation() {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 按名称相似度分组
        for (int i = 0; i < allNodes.size(); i++) {
            for (int j = i + 1; j < allNodes.size(); j++) {
                KnowledgeGraphNode node1 = allNodes.get(i);
                KnowledgeGraphNode node2 = allNodes.get(j);
                
                double similarity = calculateNameSimilarity(node1.getName(), node2.getName());
                if (similarity > 0.7 && similarity < 1.0) {
                    Map<String, Object> suggestion = new HashMap<>();
                    suggestion.put("node1Id", node1.getNodeId());
                    suggestion.put("node1Name", node1.getName());
                    suggestion.put("node2Id", node2.getNodeId());
                    suggestion.put("node2Name", node2.getName());
                    suggestion.put("similarity", similarity);
                    suggestion.put("reason", "名称高度相似，可能是同一实体");

                    // 检查是否已有关系
                    List<KnowledgeGraphRelation> rels1 = graphService.getNodeRelations(node1.getNodeId());
                    List<KnowledgeGraphRelation> rels2 = graphService.getNodeRelations(node2.getNodeId());
                    suggestion.put("node1RelationCount", rels1.size());
                    suggestion.put("node2RelationCount", rels2.size());

                    suggestions.add(suggestion);
                }
            }
        }

        // 按相似度排序
        suggestions.sort((a, b) -> {
            double sa = (double) a.get("similarity");
            double sb = (double) b.get("similarity");
            return Double.compare(sb, sa);
        });

        return suggestions;
    }

    /**
     * 合并实体
     * @param keepNodeId 保留的节点ID
     * @param removeNodeId 要删除的节点ID
     * @return 合并结果
     */
    public Map<String, Object> mergeEntities(String keepNodeId, String removeNodeId) {
        KnowledgeGraphNode keepNode = graphService.getById(keepNodeId);
        KnowledgeGraphNode removeNode = graphService.getById(removeNodeId);

        if (keepNode == null || removeNode == null) {
            throw new IllegalArgumentException("节点不存在");
        }

        Map<String, Object> result = new HashMap<>();
        int relocatedRelations = 0;

        // 将所有指向被合并节点的关系重定向到保留节点
        List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(removeNodeId);
        for (KnowledgeGraphRelation rel : relations) {
            KnowledgeGraphRelation newRel = new KnowledgeGraphRelation();
            newRel.setRelationId(UUID.randomUUID().toString());
            newRel.setRelationType(rel.getRelationType());
            newRel.setDescription(rel.getDescription() + " (实体合并迁移)");
            newRel.setWeight(rel.getWeight());
            newRel.setConfidence(rel.getConfidence() != null ? rel.getConfidence() * 0.9 : 0.7);

            if (rel.getSourceNodeId().equals(removeNodeId)) {
                newRel.setSourceNodeId(keepNodeId);
                newRel.setSourceNodeName(keepNode.getName());
                newRel.setTargetNodeId(rel.getTargetNodeId());
                newRel.setTargetNodeName(rel.getTargetNodeName());
            } else {
                newRel.setSourceNodeId(rel.getSourceNodeId());
                newRel.setSourceNodeName(rel.getSourceNodeName());
                newRel.setTargetNodeId(keepNodeId);
                newRel.setTargetNodeName(keepNode.getName());
            }

            graphService.saveRelation(newRel);
            relocatedRelations++;
        }

        // 删除被合并节点
        graphService.deleteNode(removeNodeId);

        result.put("merged", true);
        result.put("keepNode", keepNode.getName());
        result.put("removedNode", removeNode.getName());
        result.put("relocatedRelations", relocatedRelations);
        return result;
    }

    // ========== 推理链生成 ==========

    /**
     * 生成推理链（解释两个实体之间为什么存在某种关系）
     * @param sourceName 源实体名
     * @param targetName 目标实体名
     * @return 推理链
     */
    public Map<String, Object> generateReasoningChain(String sourceName, String targetName) {
        KnowledgeGraphNode source = graphService.findByName(sourceName);
        KnowledgeGraphNode target = graphService.findByName(targetName);

        if (source == null || target == null) {
            return Map.of("found", false, "message", "实体不存在");
        }

        List<Map<String, Object>> paths = findAllPaths(sourceName, targetName, 4);
        
        Map<String, Object> result = new HashMap<>();
        result.put("found", !paths.isEmpty());
        result.put("source", sourceName);
        result.put("target", targetName);
        result.put("pathCount", paths.size());
        result.put("paths", paths);

        if (!paths.isEmpty()) {
            Map<String, Object> shortestPath = paths.get(0);
            result.put("shortestPathLength", shortestPath.get("length"));
            result.put("reasoningChain", buildReasoningChainDescription(shortestPath));
        }

        return result;
    }

    // ========== 辅助方法 ==========

    /**
     * 传递关系推理
     */
    private List<Map<String, Object>> inferTransitiveRelations(String relationType) {
        List<Map<String, Object>> inferred = new ArrayList<>();
        List<KnowledgeGraphRelation> allRelations = graphService.getRelationsByType(relationType);

        // 构建邻接表
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (KnowledgeGraphRelation rel : allRelations) {
            adjacency.computeIfAbsent(rel.getSourceNodeId(), k -> new HashSet<>())
                    .add(rel.getTargetNodeId());
        }

        // 传递闭包（Floyd-Warshall启发式）
        for (Map.Entry<String, Set<String>> entry : adjacency.entrySet()) {
            String source = entry.getKey();
            for (String intermediate : entry.getValue()) {
                Set<String> transitiveTargets = adjacency.get(intermediate);
                if (transitiveTargets != null) {
                    for (String target : transitiveTargets) {
                        if (!source.equals(target) && !adjacency.get(source).contains(target)) {
                            Map<String, Object> inferredRel = new HashMap<>();
                            inferredRel.put("sourceNodeId", source);
                            inferredRel.put("targetNodeId", target);
                            inferredRel.put("relationType", relationType);
                            inferredRel.put("inferenceRule", "transitive");
                            inferredRel.put("via", intermediate);
                            inferred.add(inferredRel);

                            // 写入图谱
                            saveInferredRelation(source, target, relationType, "传递推理: via " + intermediate);
                        }
                    }
                }
            }
        }

        return inferred;
    }

    /**
     * 对称关系推理
     */
    private List<Map<String, Object>> inferSymmetricRelations(String relationType) {
        List<Map<String, Object>> inferred = new ArrayList<>();
        List<KnowledgeGraphRelation> relations = graphService.getRelationsByType(relationType);

        for (KnowledgeGraphRelation rel : relations) {
            // 检查是否已有反向关系
            boolean hasReverse = relations.stream()
                    .anyMatch(r -> r.getSourceNodeId().equals(rel.getTargetNodeId()) 
                            && r.getTargetNodeId().equals(rel.getSourceNodeId()));

            if (!hasReverse) {
                Map<String, Object> inferredRel = new HashMap<>();
                inferredRel.put("sourceNodeId", rel.getTargetNodeId());
                inferredRel.put("targetNodeId", rel.getSourceNodeId());
                inferredRel.put("relationType", relationType);
                inferredRel.put("inferenceRule", "symmetric");
                inferred.add(inferredRel);

                saveInferredRelation(rel.getTargetNodeId(), rel.getSourceNodeId(), 
                        relationType, "对称推理");
            }
        }

        return inferred;
    }

    /**
     * 继承关系推理
     */
    private List<Map<String, Object>> inferInheritance() {
        List<Map<String, Object>> inferred = new ArrayList<>();
        List<KnowledgeGraphRelation> belongsTo = graphService.getRelationsByType("属于");

        for (KnowledgeGraphRelation rel : belongsTo) {
            KnowledgeGraphNode parent = graphService.findById(rel.getTargetNodeId());
            if (parent != null && parent.getProperties() != null) {
                try {
                    JSONObject props = JSON.parseObject(parent.getProperties());
                    // 将父节点的关键属性继承给子节点
                    KnowledgeGraphNode child = graphService.findById(rel.getSourceNodeId());
                    if (child != null) {
                        JSONObject childProps = child.getProperties() != null 
                                ? JSON.parseObject(child.getProperties()) : new JSONObject();
                        
                        boolean inherited = false;
                        for (String key : props.keySet()) {
                            if (!childProps.containsKey(key)) {
                                childProps.put(key, props.get(key));
                                inherited = true;
                            }
                        }

                        if (inherited) {
                            child.setProperties(childProps.toJSONString());
                            graphService.saveNode(child);

                            Map<String, Object> inf = new HashMap<>();
                            inf.put("sourceNodeId", child.getNodeId());
                            inf.put("targetNodeId", parent.getNodeId());
                            inf.put("relationType", "属性继承");
                            inf.put("inferenceRule", "hierarchical_inheritance");
                            inferred.add(inf);
                        }
                    }
                } catch (Exception e) {
                    log.debug("属性继承推理失败: {}", e.getMessage());
                }
            }
        }

        return inferred;
    }

    /**
     * 间接引用推理
     */
    private List<Map<String, Object>> inferIndirectReferences() {
        List<Map<String, Object>> inferred = new ArrayList<>();
        List<KnowledgeGraphRelation> references = graphService.getRelationsByType("引用");
        List<KnowledgeGraphRelation> contains = graphService.getRelationsByType("包含");

        for (KnowledgeGraphRelation ref : references) {
            for (KnowledgeGraphRelation cont : contains) {
                if (ref.getTargetNodeId().equals(cont.getSourceNodeId())) {
                    boolean exists = graphService.getNodeRelations(ref.getSourceNodeId()).stream()
                            .anyMatch(r -> r.getTargetNodeId().equals(cont.getTargetNodeId()) 
                                    && "引用".equals(r.getRelationType()));

                    if (!exists && !ref.getSourceNodeId().equals(cont.getTargetNodeId())) {
                        Map<String, Object> inf = new HashMap<>();
                        inf.put("sourceNodeId", ref.getSourceNodeId());
                        inf.put("targetNodeId", cont.getTargetNodeId());
                        inf.put("relationType", "引用");
                        inf.put("inferenceRule", "indirect_reference");
                        inf.put("via", ref.getTargetNodeId());
                        inferred.add(inf);

                        saveInferredRelation(ref.getSourceNodeId(), cont.getTargetNodeId(), 
                                "引用", "间接引用推理: via " + ref.getTargetNodeName());
                    }
                }
            }
        }

        return inferred;
    }

    /**
     * 深度优先搜索查找所有路径
     */
    private void dfsFindPaths(String currentId, String targetId, Set<String> visited,
                               List<String> currentPath, List<String> currentRelations,
                               List<Map<String, Object>> paths, int maxDepth, int depth) {
        if (depth > maxDepth || visited.contains(currentId)) return;

        visited.add(currentId);
        KnowledgeGraphNode currentNode = graphService.findById(currentId);
        currentPath.add(currentNode != null ? currentNode.getName() : currentId);

        if (currentId.equals(targetId)) {
            Map<String, Object> path = new HashMap<>();
            path.put("nodes", new ArrayList<>(currentPath));
            path.put("relations", new ArrayList<>(currentRelations));
            path.put("length", depth);
            paths.add(path);
        } else if (depth < maxDepth) {
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(currentId);
            for (KnowledgeGraphRelation rel : relations) {
                String nextId = rel.getSourceNodeId().equals(currentId) 
                        ? rel.getTargetNodeId() : rel.getSourceNodeId();
                if (!visited.contains(nextId)) {
                    currentRelations.add(rel.getRelationType());
                    dfsFindPaths(nextId, targetId, visited, currentPath, currentRelations, 
                            paths, maxDepth, depth + 1);
                    currentRelations.remove(currentRelations.size() - 1);
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        visited.remove(currentId);
    }

    /**
     * 构建概念层级
     */
    private void buildHierarchy(String nodeId, String direction, List<Map<String, Object>> hierarchy,
                                 Set<String> visited, int depth, int maxDepth) {
        if (depth > maxDepth || visited.contains(nodeId)) return;

        visited.add(nodeId);
        KnowledgeGraphNode node = graphService.findById(nodeId);
        if (node == null) return;

        Map<String, Object> level = new HashMap<>();
        level.put("name", node.getName());
        level.put("label", node.getLabel());
        level.put("depth", depth);
        hierarchy.add(level);

        List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(nodeId);
        for (KnowledgeGraphRelation rel : relations) {
            boolean match = false;
            String nextId = null;

            if ("up".equals(direction)) {
                // 向上查找：当前节点是关系中的target（属于某个概念）
                if (rel.getTargetNodeId().equals(nodeId) && "属于".equals(rel.getRelationType())) {
                    nextId = rel.getSourceNodeId();
                    match = true;
                }
            } else {
                // 向下查找：当前节点是关系中的source（包含子概念）
                if (rel.getSourceNodeId().equals(nodeId) && "包含".equals(rel.getRelationType())) {
                    nextId = rel.getTargetNodeId();
                    match = true;
                }
            }

            if (match && nextId != null) {
                buildHierarchy(nextId, direction, hierarchy, visited, depth + 1, maxDepth);
            }
        }
    }

    /**
     * 计算名称相似度（基于编辑距离）
     */
    private double calculateNameSimilarity(String name1, String name2) {
        if (name1 == null || name2 == null) return 0.0;
        if (name1.equals(name2)) return 1.0;

        int maxLen = Math.max(name1.length(), name2.length());
        if (maxLen == 0) return 1.0;

        int distance = levenshteinDistance(name1, name2);
        return 1.0 - (double) distance / maxLen;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    /**
     * 保存推理出的关系
     */
    private void saveInferredRelation(String sourceId, String targetId, String relationType, String description) {
        try {
            KnowledgeGraphNode source = graphService.findById(sourceId);
            KnowledgeGraphNode target = graphService.findById(targetId);
            if (source == null || target == null) return;

            KnowledgeGraphRelation relation = new KnowledgeGraphRelation();
            relation.setRelationId(UUID.randomUUID().toString());
            relation.setSourceNodeId(sourceId);
            relation.setTargetNodeId(targetId);
            relation.setSourceNodeName(source.getName());
            relation.setTargetNodeName(target.getName());
            relation.setRelationType(relationType);
            relation.setDescription("[推理] " + description);
            relation.setWeight(30);
            relation.setConfidence(0.5);
            relation.setStatus(1);
            graphService.saveRelation(relation);
        } catch (Exception e) {
            log.warn("保存推理关系失败: {}", e.getMessage());
        }
    }

    /**
     * 构建推理链描述
     */
    private String buildReasoningChainDescription(Map<String, Object> path) {
        @SuppressWarnings("unchecked")
        List<String> nodes = (List<String>) path.get("nodes");
        @SuppressWarnings("unchecked")
        List<String> relations = (List<String>) path.get("relations");

        if (nodes == null || relations == null || nodes.size() < 2) {
            return "无法构建推理链";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("推理链: ");
        for (int i = 0; i < nodes.size() - 1; i++) {
            sb.append(nodes.get(i));
            if (i < relations.size()) {
                sb.append(" --[").append(relations.get(i)).append("]--> ");
            }
        }
        sb.append(nodes.get(nodes.size() - 1));
        return sb.toString();
    }
}
