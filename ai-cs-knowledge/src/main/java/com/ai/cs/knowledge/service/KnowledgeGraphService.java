package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.KnowledgeGraphProperties;
import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.ai.cs.knowledge.mapper.KnowledgeGraphNodeMapper;
import com.ai.cs.knowledge.mapper.KnowledgeGraphRelationMapper;
import com.ai.cs.knowledge.util.LlmClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱服务
 * 提供知识图谱构建、查询、实体关系抽取、图谱可视化数据生成等功能
 */
@Slf4j
@Service
public class KnowledgeGraphService extends ServiceImpl<KnowledgeGraphNodeMapper, KnowledgeGraphNode> {

    private final KnowledgeGraphNodeMapper nodeMapper;
    private final KnowledgeGraphRelationMapper relationMapper;
    private final KnowledgeGraphProperties graphProperties;
    private final LlmClient llmClient;

    public KnowledgeGraphService(KnowledgeGraphNodeMapper nodeMapper,
                                  KnowledgeGraphRelationMapper relationMapper,
                                  KnowledgeGraphProperties graphProperties,
                                  LlmClient llmClient) {
        this.nodeMapper = nodeMapper;
        this.relationMapper = relationMapper;
        this.graphProperties = graphProperties;
        this.llmClient = llmClient;
    }

    // ========== 节点管理 ==========

    /**
     * 创建或更新知识图谱节点
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeGraphNode saveNode(KnowledgeGraphNode node) {
        // 检查节点是否已存在
        KnowledgeGraphNode existing = nodeMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphNode>()
                        .eq(KnowledgeGraphNode::getNodeId, node.getNodeId())
                        .eq(KnowledgeGraphNode::getDelFlag, 0)
        );

        if (existing != null) {
            node.setId(existing.getId());
            nodeMapper.updateById(node);
            log.info("更新知识图谱节点: {}", node.getNodeId());
        } else {
            if (node.getNodeId() == null || node.getNodeId().isEmpty()) {
                node.setNodeId(UUID.randomUUID().toString());
            }
            nodeMapper.insert(node);
            log.info("创建知识图谱节点: {}", node.getNodeId());
        }
        return node;
    }

    /**
     * 根据ID查找节点
     */
    public KnowledgeGraphNode findById(String nodeId) {
        return nodeMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphNode>()
                        .eq(KnowledgeGraphNode::getNodeId, nodeId)
                        .eq(KnowledgeGraphNode::getDelFlag, 0)
        );
    }

    /**
     * 根据名称查找节点
     */
    public KnowledgeGraphNode findByName(String name) {
        return nodeMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphNode>()
                        .eq(KnowledgeGraphNode::getName, name)
                        .eq(KnowledgeGraphNode::getDelFlag, 0)
        );
    }

    /**
     * 获取所有节点
     */
    public List<KnowledgeGraphNode> getAllNodes() {
        return nodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeGraphNode>()
                        .eq(KnowledgeGraphNode::getDelFlag, 0)
                        .eq(KnowledgeGraphNode::getStatus, 1)
        );
    }

    /**
     * 根据标签获取节点
     */
    public List<KnowledgeGraphNode> getNodesByLabel(String label) {
        return nodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeGraphNode>()
                        .eq(KnowledgeGraphNode::getLabel, label)
                        .eq(KnowledgeGraphNode::getDelFlag, 0)
        );
    }

    /**
     * 删除节点（同时删除关联关系）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNode(String nodeId) {
        // 删除关联关系
        relationMapper.delete(new LambdaQueryWrapper<KnowledgeGraphRelation>()
                .and(w -> w.eq(KnowledgeGraphRelation::getSourceNodeId, nodeId)
                        .or().eq(KnowledgeGraphRelation::getTargetNodeId, nodeId))
        );
        // 删除节点
        return nodeMapper.delete(new LambdaQueryWrapper<KnowledgeGraphNode>()
                .eq(KnowledgeGraphNode::getNodeId, nodeId)) > 0;
    }

    // ========== 关系管理 ==========

    /**
     * 创建或更新关系
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeGraphRelation saveRelation(KnowledgeGraphRelation relation) {
        if (relation.getRelationId() == null || relation.getRelationId().isEmpty()) {
            relation.setRelationId(UUID.randomUUID().toString());
        }

        // 检查关系是否已存在
        KnowledgeGraphRelation existing = relationMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraphRelation>()
                        .eq(KnowledgeGraphRelation::getSourceNodeId, relation.getSourceNodeId())
                        .eq(KnowledgeGraphRelation::getTargetNodeId, relation.getTargetNodeId())
                        .eq(KnowledgeGraphRelation::getRelationType, relation.getRelationType())
                        .eq(KnowledgeGraphRelation::getDelFlag, 0)
        );

        if (existing != null) {
            relation.setId(existing.getId());
            relationMapper.updateById(relation);
        } else {
            relationMapper.insert(relation);
        }
        log.info("保存图谱关系: {} -[{}]-> {}", relation.getSourceNodeName(), relation.getRelationType(), relation.getTargetNodeName());
        return relation;
    }

    /**
     * 获取节点的所有关系
     */
    public List<KnowledgeGraphRelation> getNodeRelations(String nodeId) {
        return relationMapper.selectList(
                new LambdaQueryWrapper<KnowledgeGraphRelation>()
                        .and(w -> w.eq(KnowledgeGraphRelation::getSourceNodeId, nodeId)
                                .or().eq(KnowledgeGraphRelation::getTargetNodeId, nodeId))
                        .eq(KnowledgeGraphRelation::getDelFlag, 0)
        );
    }

    /**
     * 获取指定类型的关系
     */
    public List<KnowledgeGraphRelation> getRelationsByType(String relationType) {
        return relationMapper.selectList(
                new LambdaQueryWrapper<KnowledgeGraphRelation>()
                        .eq(KnowledgeGraphRelation::getRelationType, relationType)
                        .eq(KnowledgeGraphRelation::getDelFlag, 0)
        );
    }

    /**
     * 删除关系
     */
    public boolean deleteRelation(String relationId) {
        return relationMapper.delete(new LambdaQueryWrapper<KnowledgeGraphRelation>()
                .eq(KnowledgeGraphRelation::getRelationId, relationId)) > 0;
    }

    // ========== 图谱构建 ==========

    /**
     * 从文本中自动抽取实体和关系构建图谱
     * @param text 文本内容
     * @param sourceDocumentId 来源文档ID
     * @param sourceDocumentName 来源文档名称
     * @return 构建结果摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public String buildFromText(String text, String sourceDocumentId, String sourceDocumentName) {
        try {
            log.info("开始从文本构建知识图谱, 文本长度={}", text.length());
            int nodeCount = 0;
            int relationCount = 0;

            // 使用LLM抽取实体和关系
            String prompt = buildEntityExtractionPrompt(text);
            String llmResponse = llmClient.call(prompt);

            // 解析LLM返回的实体和关系
            Map<String, Object> extracted = parseEntityRelationResponse(llmResponse);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> entities = (List<Map<String, String>>) extracted.get("entities");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> relations = (List<Map<String, String>>) extracted.get("relations");

            if (entities != null) {
                for (Map<String, String> entity : entities) {
                    KnowledgeGraphNode node = new KnowledgeGraphNode();
                    node.setNodeId(UUID.randomUUID().toString());
                    node.setName(entity.get("name"));
                    node.setLabel(entity.getOrDefault("type", "概念"));
                    node.setDescription(entity.getOrDefault("description", ""));
                    node.setSourceDocumentId(sourceDocumentId);
                    node.setSourceDocumentName(sourceDocumentName);
                    node.setImportance(50);
                    node.setIsCore(0);
                    node.setStatus(1);
                    saveNode(node);
                    nodeCount++;
                }
            }

            if (relations != null) {
                for (Map<String, String> rel : relations) {
                    KnowledgeGraphNode sourceNode = findByName(rel.get("source"));
                    KnowledgeGraphNode targetNode = findByName(rel.get("target"));
                    if (sourceNode != null && targetNode != null) {
                        KnowledgeGraphRelation relation = new KnowledgeGraphRelation();
                        relation.setRelationId(UUID.randomUUID().toString());
                        relation.setSourceNodeId(sourceNode.getNodeId());
                        relation.setTargetNodeId(targetNode.getNodeId());
                        relation.setSourceNodeName(sourceNode.getName());
                        relation.setTargetNodeName(targetNode.getName());
                        relation.setRelationType(rel.getOrDefault("type", "相关"));
                        relation.setDescription(rel.getOrDefault("description", ""));
                        relation.setWeight(50);
                        relation.setConfidence(0.7);
                        relation.setSourceDocumentId(sourceDocumentId);
                        relation.setStatus(1);
                        saveRelation(relation);
                        relationCount++;
                    }
                }
            }

            String result = String.format("知识图谱构建完成: 创建节点 %d 个, 关系 %d 条", nodeCount, relationCount);
            log.info(result);
            return result;
        } catch (Exception e) {
            log.error("知识图谱构建失败", e);
            throw new RuntimeException("知识图谱构建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量从文档构建图谱
     * @param documentIds 文档ID列表
     * @return 构建统计
     */
    public String buildFromDocuments(List<String> documentIds) {
        // 批量构建框架，实际需要配合 DocumentLoadService 获取文档内容
        StringBuilder result = new StringBuilder("批量图谱构建结果:\n");
        for (String docId : documentIds) {
            try {
                result.append("文档 ").append(docId).append(": 已触发构建任务\n");
            } catch (Exception e) {
                result.append("文档 ").append(docId).append(": 失败 - ").append(e.getMessage()).append("\n");
            }
        }
        return result.toString();
    }

    // ========== 图谱查询 ==========

    /**
     * 基于图谱的问答
     * @param question 用户问题
     * @return 基于图谱的回答
     */
    public String graphQa(String question) {
        try {
            // 1. 从问题中提取关键实体
            List<KnowledgeGraphNode> relatedNodes = searchRelatedNodes(question);

            if (relatedNodes.isEmpty()) {
                return "未在图谱中找到与您问题相关的实体。";
            }

            // 2. 获取相关实体的关系和邻居节点
            StringBuilder context = new StringBuilder();
            context.append("知识图谱相关信息:\n");
            Set<String> processedNodes = new HashSet<>();

            for (KnowledgeGraphNode node : relatedNodes) {
                if (!processedNodes.contains(node.getNodeId())) {
                    processedNodes.add(node.getNodeId());
                    context.append(String.format("- 实体: %s (类型: %s)\n", node.getName(), node.getLabel()));
                    if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                        context.append(String.format("  描述: %s\n", node.getDescription()));
                    }

                    // 获取关系
                    List<KnowledgeGraphRelation> nodeRelations = getNodeRelations(node.getNodeId());
                    for (KnowledgeGraphRelation rel : nodeRelations) {
                        String relatedName = rel.getSourceNodeId().equals(node.getNodeId())
                                ? rel.getTargetNodeName() : rel.getSourceNodeName();
                        context.append(String.format("  -[%s]-> %s\n", rel.getRelationType(), relatedName));
                    }
                }
            }

            // 3. 构建Prompt并调用LLM
            String prompt = String.format(
                    "基于以下知识图谱信息回答问题。如果信息不足，请明确说明。\n\n%s\n\n问题: %s\n\n回答:",
                    context.toString(), question
            );

            return llmClient.call(prompt);
        } catch (Exception e) {
            log.error("图谱问答失败", e);
            return "图谱问答出错: " + e.getMessage();
        }
    }

    /**
     * 搜索与问题相关的节点
     */
    public List<KnowledgeGraphNode> searchRelatedNodes(String question) {
        // 关键词匹配 + 模糊搜索
        List<KnowledgeGraphNode> allNodes = getAllNodes();
        List<KnowledgeGraphNode> matched = new ArrayList<>();

        // 分词匹配（简单实现：按常见分隔符分词）
        String[] keywords = question.split("[，,。.!！?？\\s]+");

        for (KnowledgeGraphNode node : allNodes) {
            for (String keyword : keywords) {
                if (keyword.length() >= 2 && node.getName().contains(keyword)) {
                    matched.add(node);
                    break;
                }
                if (node.getDescription() != null && node.getDescription().contains(keyword)) {
                    matched.add(node);
                    break;
                }
            }
        }

        // 按重要性排序
        matched.sort((a, b) -> {
            int impA = a.getImportance() != null ? a.getImportance() : 0;
            int impB = b.getImportance() != null ? b.getImportance() : 0;
            return Integer.compare(impB, impA);
        });

        return matched;
    }

    // ========== 图谱可视化数据 ==========

    /**
     * 生成图谱可视化数据（ECharts格式）
     * @return 可视化JSON数据
     */
    public Map<String, Object> getGraphVisualizationData() {
        Map<String, Object> result = new HashMap<>();

        // 节点数据
        List<KnowledgeGraphNode> nodes = getAllNodes();
        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (KnowledgeGraphNode node : nodes) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", node.getNodeId());
            n.put("name", node.getName());
            n.put("category", getNodeCategory(node.getLabel()));
            n.put("symbolSize", calculateNodeSize(node.getImportance(), node.getIsCore()));
            n.put("itemStyle", getNodeStyle(node.getLabel()));
            nodeList.add(n);
        }
        result.put("nodes", nodeList);

        // 关系数据
        List<KnowledgeGraphRelation> allRelations = relationMapper.selectList(
                new LambdaQueryWrapper<KnowledgeGraphRelation>()
                        .eq(KnowledgeGraphRelation::getDelFlag, 0)
        );
        List<Map<String, Object>> linkList = new ArrayList<>();
        for (KnowledgeGraphRelation rel : allRelations) {
            Map<String, Object> l = new HashMap<>();
            l.put("source", rel.getSourceNodeId());
            l.put("target", rel.getTargetNodeId());
            l.put("name", rel.getRelationType());
            l.put("des", rel.getDescription());
            l.put("lineStyle", getRelationStyle(rel.getRelationType()));
            linkList.add(l);
        }
        result.put("links", linkList);

        // 分类数据
        List<Map<String, Object>> categories = buildCategories(nodes);
        result.put("categories", categories);

        // 统计信息
        Map<String, Object> stats = new HashMap<>();
        stats.put("nodeCount", nodes.size());
        stats.put("relationCount", allRelations.size());
        result.put("statistics", stats);

        return result;
    }

    /**
     * 获取子图谱（某节点及其N度邻居）
     */
    public Map<String, Object> getSubGraph(String nodeId, int degree) {
        Set<String> visitedNodes = new HashSet<>();
        Set<String> visitedRelations = new HashSet<>();
        List<String> currentLevel = new ArrayList<>();
        currentLevel.add(nodeId);
        visitedNodes.add(nodeId);

        for (int d = 0; d < degree && !currentLevel.isEmpty(); d++) {
            List<String> nextLevel = new ArrayList<>();
            for (String currentNodeId : currentLevel) {
                List<KnowledgeGraphRelation> relations = getNodeRelations(currentNodeId);
                for (KnowledgeGraphRelation rel : relations) {
                    visitedRelations.add(rel.getRelationId());
                    String neighborId = rel.getSourceNodeId().equals(currentNodeId)
                            ? rel.getTargetNodeId() : rel.getSourceNodeId();
                    if (!visitedNodes.contains(neighborId)) {
                        visitedNodes.add(neighborId);
                        nextLevel.add(neighborId);
                    }
                }
            }
            currentLevel = nextLevel;
        }

        // 构建子图数据
        Map<String, Object> subGraph = new HashMap<>();
        subGraph.put("nodeIds", new ArrayList<>(visitedNodes));
        subGraph.put("relationIds", new ArrayList<>(visitedRelations));
        subGraph.put("centerNodeId", nodeId);
        subGraph.put("degree", degree);
        return subGraph;
    }

    // ========== 图谱统计分析 ==========

    /**
     * 获取图谱统计信息
     */
    public Map<String, Object> getGraphStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 节点统计
        List<KnowledgeGraphNode> allNodes = getAllNodes();
        stats.put("totalNodes", allNodes.size());

        // 按标签分组统计
        Map<String, Long> labelStats = allNodes.stream()
                .collect(Collectors.groupingBy(KnowledgeGraphNode::getLabel, Collectors.counting()));
        stats.put("nodeByLabel", labelStats);

        // 关系统计
        List<KnowledgeGraphRelation> allRelations = relationMapper.selectList(
                new LambdaQueryWrapper<KnowledgeGraphRelation>().eq(KnowledgeGraphRelation::getDelFlag, 0)
        );
        stats.put("totalRelations", allRelations.size());

        // 按关系类型统计
        Map<String, Long> relationStats = allRelations.stream()
                .collect(Collectors.groupingBy(KnowledgeGraphRelation::getRelationType, Collectors.counting()));
        stats.put("relationsByType", relationStats);

        // 核心节点数量
        long coreCount = allNodes.stream().filter(n -> n.getIsCore() != null && n.getIsCore() == 1).count();
        stats.put("coreNodes", coreCount);

        return stats;
    }

    // ========== 实体关系抽取 ==========

    /**
     * 实体关系抽取（从文本中识别实体和关系）
     */
    public Map<String, Object> extractEntities(String text) {
        try {
            String prompt = buildEntityExtractionPrompt(text);
            String llmResponse = llmClient.call(prompt);
            return parseEntityRelationResponse(llmResponse);
        } catch (Exception e) {
            log.error("实体关系抽取失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("entities", Collections.emptyList());
            errorResult.put("relations", Collections.emptyList());
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 构建实体关系抽取的Prompt
     */
    private String buildEntityExtractionPrompt(String text) {
        return String.format(
                "请从以下文本中提取实体和关系，以JSON格式返回。\n" +
                "实体类型包括：概念、人物、文档、技术、产品、组织、时间、地点、事件\n" +
                "关系类型包括：包含、依赖、属于、等同于、引用、相关、前置、后置\n\n" +
                "返回格式：\n" +
                "{\n" +
                "  \"entities\": [{\"name\": \"实体名\", \"type\": \"实体类型\", \"description\": \"简短描述\"}],\n" +
                "  \"relations\": [{\"source\": \"源实体名\", \"target\": \"目标实体名\", \"type\": \"关系类型\", \"description\": \"关系描述\"}]\n" +
                "}\n\n" +
                "文本内容：\n%s\n\n" +
                "请只返回JSON，不要包含其他内容。", text);
    }

    /**
     * 解析LLM返回的实体关系JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEntityRelationResponse(String llmResponse) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 尝试提取JSON部分
            String jsonStr = llmResponse;
            int jsonStart = llmResponse.indexOf('{');
            int jsonEnd = llmResponse.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonStr = llmResponse.substring(jsonStart, jsonEnd + 1);
            }

            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(jsonStr);
            result.put("entities", json.getJSONArray("entities") != null
                    ? json.getJSONArray("entities").toJavaList(Map.class) : Collections.emptyList());
            result.put("relations", json.getJSONArray("relations") != null
                    ? json.getJSONArray("relations").toJavaList(Map.class) : Collections.emptyList());
        } catch (Exception e) {
            log.warn("解析实体关系JSON失败，使用空结果: {}", e.getMessage());
            result.put("entities", Collections.emptyList());
            result.put("relations", Collections.emptyList());
            result.put("rawResponse", llmResponse);
        }
        return result;
    }

    private int getNodeCategory(String label) {
        if (label == null) return 0;
        return switch (label) {
            case "概念" -> 0;
            case "技术" -> 1;
            case "产品" -> 2;
            case "文档" -> 3;
            case "人物" -> 4;
            case "组织" -> 5;
            case "事件" -> 6;
            default -> 7;
        };
    }

    private int calculateNodeSize(Integer importance, Integer isCore) {
        int base = 30;
        if (isCore != null && isCore == 1) base = 50;
        if (importance != null) base += importance / 5;
        return Math.min(base, 80);
    }

    private Map<String, Object> getNodeStyle(String label) {
        Map<String, Object> style = new HashMap<>();
        // 根据类型设置不同颜色
        String color = switch (label != null ? label : "") {
            case "概念" -> "#5470c6";
            case "技术" -> "#91cc75";
            case "产品" -> "#fac858";
            case "文档" -> "#ee6666";
            case "人物" -> "#73c0de";
            case "组织" -> "#3ba272";
            case "事件" -> "#fc8452";
            default -> "#9a60b4";
        };
        style.put("color", color);
        return style;
    }

    private Map<String, Object> getRelationStyle(String relationType) {
        Map<String, Object> style = new HashMap<>();
        String color = switch (relationType != null ? relationType : "") {
            case "包含" -> "#91cc75";
            case "依赖" -> "#ee6666";
            case "属于" -> "#5470c6";
            case "等同于" -> "#fac858";
            case "引用" -> "#73c0de";
            case "相关" -> "#9a60b4";
            case "前置" -> "#fc8452";
            case "后置" -> "#3ba272";
            default -> "#999999";
        };
        style.put("color", color);
        style.put("width", 1.5);
        return style;
    }

    private List<Map<String, Object>> buildCategories(List<KnowledgeGraphNode> nodes) {
        Set<String> labels = nodes.stream()
                .map(KnowledgeGraphNode::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Map<String, Object>> categories = new ArrayList<>();
        for (String label : labels) {
            Map<String, Object> cat = new HashMap<>();
            cat.put("name", label);
            categories.add(cat);
        }
        return categories;
    }
}
