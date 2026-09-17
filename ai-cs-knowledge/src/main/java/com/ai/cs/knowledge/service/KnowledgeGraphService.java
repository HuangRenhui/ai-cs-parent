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
     * 获取全部已发布节点（内部图算法、可视化共用）
     */
    public List<KnowledgeGraphNode> getAllNodes() {
        return listNodes(null);
    }

    /**
     * 按关键字筛选已发布节点；keyword 为空时等同 {@link #getAllNodes()}。
     *
     * @param keyword 名称 / 标签 / 描述模糊匹配，可空
     * @return 命中节点，未填关键字则全量
     */
    public List<KnowledgeGraphNode> listNodes(String keyword) {
        LambdaQueryWrapper<KnowledgeGraphNode> q = new LambdaQueryWrapper<KnowledgeGraphNode>()
                .eq(KnowledgeGraphNode::getDelFlag, 0)
                .eq(KnowledgeGraphNode::getStatus, 1);
        // 管理页搜索框会带 keyword，不传时保持原「拉全量」行为
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(KnowledgeGraphNode::getName, k)
                    .or().like(KnowledgeGraphNode::getLabel, k)
                    .or().like(KnowledgeGraphNode::getDescription, k));
        }
        return nodeMapper.selectList(q);
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
    /**
     * 从文本构建知识图谱（占位：不抽取、不建图）
     *
     * <p>TODO 后续实现：用 LLM 从文本抽取实体与关系（提示词见 {@code buildEntityExtractionPrompt}，
     * 解析见 {@code parseEntityRelationResponse}），逐条落库：
     * 节点补 uuid、名称、类型、描述、来源文档、重要度与状态；
     * 关系按两端名称回查节点后写入类型、描述、权重、置信度与来源文档；
     * 最后返回「创建节点 N 个, 关系 M 条」摘要。全程事务，异常抛出并回滚。</p>
     *
     * <p>当前不调用模型、不写库，返回未实现提示。</p>
     *
     * @param text               原始文本
     * @param sourceDocumentId   来源文档ID
     * @param sourceDocumentName 来源文档名称
     * @return 未实现提示
     */
    @Transactional(rollbackFor = Exception.class)
    public String buildFromText(String text, String sourceDocumentId, String sourceDocumentName) {
        log.warn("[占位] 从文本构建知识图谱未实现");
        return "知识图谱构建为占位实现，后端未接入实体关系抽取";
    }

    /**
     * 批量从文档构建图谱（占位：不构建）
     *
     * <p>TODO 后续实现：配合 {@code DocumentLoadService} 取文档内容后逐个调用
     * {@link #buildFromText} 并汇总统计结果。</p>
     *
     * @param documentIds 文档ID列表
     * @return 未实现提示
     */
    public String buildFromDocuments(List<String> documentIds) {
        log.warn("[占位] 批量图谱构建未实现");
        return "批量知识图谱构建为占位实现";
    }

    // ========== 图谱查询（占位） ==========

    /**
     * 基于图谱的问答（占位：不检索、不调用模型）
     *
     * <p>TODO 后续实现：从问题提取关键实体 → 取相关节点及其关系拼成图谱上下文
     * （形如「- 实体: X (类型: Y)」「-[关系]-> Z」）→ 组装
     * 「基于以下知识图谱信息回答问题，信息不足请明确说明」的提示词调用 LLM。</p>
     *
     * @param question 用户问题
     * @return 未实现提示
     */
    public String graphQa(String question) {
        log.warn("[占位] 图谱问答未实现");
        return "图谱问答为占位实现，后端未接入图谱检索与模型生成";
    }

    /**
     * 搜索与问题相关的节点（占位：返回空列表）
     *
     * <p>TODO 后续实现：按中英文标点与空白分词，用长度 ≥2 的关键词匹配节点名称与描述，
     * 命中后按 {@code importance} 倒序返回。</p>
     *
     * @return 空列表
     */
    public List<KnowledgeGraphNode> searchRelatedNodes(String question) {
        log.info("[占位] 图谱相关节点检索未实现，返回空列表");
        return List.of();
    }

    // ========== 图谱可视化数据（占位） ==========

    /**
     * 生成图谱可视化数据（ECharts 格式）（占位：返回空 Map）
     *
     * <p>TODO 后续实现：节点转 {@code {id,name,category,symbolSize,itemStyle}}，
     * 关系转 {@code {source,target,name,des,lineStyle}}，再附分类（categories）与
     * 统计（nodeCount / relationCount）。分类映射、节点尺寸（按重要度与核心度）、
     * 节点与关系配色由本类的私有辅助方法提供。</p>
     *
     * @return 空 Map
     */
    public Map<String, Object> getGraphVisualizationData() {
        log.info("[占位] 图谱可视化数据未实现，返回空 Map");
        return Map.of();
    }

    /**
     * 获取子图谱（某节点及其 N 度邻居）（占位：返回空 Map）
     *
     * <p>TODO 后续实现：以中心节点为起点逐层 BFS（最多 degree 层），
     * 记录访问过的节点与关系，返回 {@code {nodeIds, relationIds, centerNodeId, degree}}。</p>
     *
     * @return 空 Map
     */
    public Map<String, Object> getSubGraph(String nodeId, int degree) {
        log.info("[占位] 子图谱查询未实现 nodeId={} degree={}", nodeId, degree);
        return Map.of();
    }

    // ========== 图谱统计分析（占位） ==========

    /**
     * 获取图谱统计信息（占位：返回空 Map）
     *
     * <p>TODO 后续实现：节点总数、按标签分组计数、关系总数、按关系类型分组计数、核心节点数量。</p>
     *
     * @return 空 Map
     */
    public Map<String, Object> getGraphStatistics() {
        log.info("[占位] 图谱统计未实现，返回空 Map");
        return Map.of();
    }

    // ========== 实体关系抽取（占位） ==========

    /**
     * 实体关系抽取（占位：不调用模型）
     *
     * <p>TODO 后续实现：用 {@code buildEntityExtractionPrompt} 组提示词调用 LLM，
     * 经 {@code parseEntityRelationResponse} 解析为 {@code {entities, relations}}；
     * 失败时返回空列表并附 error 字段。</p>
     *
     * @return 含空 entities/relations 且 implemented=false 的结果
     */
    public Map<String, Object> extractEntities(String text) {
        log.warn("[占位] 实体关系抽取未实现");
        Map<String, Object> empty = new HashMap<>();
        empty.put("entities", Collections.emptyList());
        empty.put("relations", Collections.emptyList());
        empty.put("implemented", false);
        return empty;
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

            com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(jsonStr);
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
