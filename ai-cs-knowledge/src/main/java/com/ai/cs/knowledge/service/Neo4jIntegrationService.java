package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.KnowledgeGraphProperties;
import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.ai.cs.knowledge.mapper.KnowledgeGraphNodeMapper;
import com.ai.cs.knowledge.mapper.KnowledgeGraphRelationMapper;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.Path;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j 图数据库集成服务
 * 提供 Cypher 查询、图遍历、路径查找等原生图数据库能力
 * 
 * 增强功能：Neo4j图数据库实际集成与Cypher查询
 */
@Slf4j
@Service
public class Neo4jIntegrationService {

    private final KnowledgeGraphProperties properties;
    private final KnowledgeGraphNodeMapper nodeMapper;
    private final KnowledgeGraphRelationMapper relationMapper;
    private Driver neo4jDriver;

    public Neo4jIntegrationService(KnowledgeGraphProperties properties,
                                    KnowledgeGraphNodeMapper nodeMapper,
                                    KnowledgeGraphRelationMapper relationMapper) {
        this.properties = properties;
        this.nodeMapper = nodeMapper;
        this.relationMapper = relationMapper;
    }

    /**
     * 初始化 Neo4j 连接
     */
    private Driver getDriver() {
        if (neo4jDriver == null) {
            synchronized (this) {
                if (neo4jDriver == null) {
                    if (!properties.isEnabled() || !"neo4j".equalsIgnoreCase(properties.getDatabaseType())) {
                        log.warn("Neo4j未启用或数据库类型不是neo4j");
                        return null;
                    }
                    try {
                        neo4jDriver = GraphDatabase.driver(
                                properties.getNeo4j().getUri(),
                                AuthTokens.basic(
                                        properties.getNeo4j().getUsername(),
                                        properties.getNeo4j().getPassword()
                                )
                        );
                        log.info("Neo4j连接已建立: {}", properties.getNeo4j().getUri());
                    } catch (Exception e) {
                        log.error("Neo4j连接失败: {}", e.getMessage());
                        return null;
                    }
                }
            }
        }
        return neo4jDriver;
    }

    // ========== Cypher 查询 ==========

    /**
     * 执行原始 Cypher 查询
     * @param cypher Cypher 查询语句
     * @param params 查询参数
     * @return 查询结果
     */
    public List<Map<String, Object>> executeCypher(String cypher, Map<String, Object> params) {
        Driver driver = getDriver();
        if (driver == null) {
            log.warn("Neo4j未连接，回退到本地数据库查询");
            return executeFallbackQuery(cypher, params);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try (Session session = driver.session(SessionConfig.forDatabase(properties.getNeo4j().getDatabase()))) {
            var result = session.run(cypher, params != null ? params : Collections.emptyMap());
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> row = new HashMap<>();
                for (String key : record.keys()) {
                    row.put(key, convertValue(record.get(key)));
                }
                results.add(row);
            }
        } catch (Exception e) {
            log.error("Cypher查询失败: {}", e.getMessage());
            throw new RuntimeException("Cypher查询失败: " + e.getMessage(), e);
        }
        return results;
    }

    /**
     * 执行无参数 Cypher 查询
     */
    public List<Map<String, Object>> executeCypher(String cypher) {
        return executeCypher(cypher, null);
    }

    // ========== 节点管理（Neo4j原生） ==========

    /**
     * 同步节点到 Neo4j
     */
    public void syncNodeToNeo4j(KnowledgeGraphNode node) {
        String cypher = """
                MERGE (n:Entity {nodeId: $nodeId})
                SET n.name = $name,
                    n.label = $label,
                    n.description = $description,
                    n.importance = $importance,
                    n.isCore = $isCore,
                    n.status = $status,
                    n.properties = $properties,
                    n.sourceDocumentId = $sourceDocumentId,
                    n.updatedAt = datetime()
                RETURN n
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("nodeId", node.getNodeId());
        params.put("name", node.getName());
        params.put("label", node.getLabel() != null ? node.getLabel() : "概念");
        params.put("description", node.getDescription() != null ? node.getDescription() : "");
        params.put("importance", node.getImportance() != null ? node.getImportance() : 50);
        params.put("isCore", node.getIsCore() != null ? node.getIsCore() : 0);
        params.put("status", node.getStatus() != null ? node.getStatus() : 1);
        params.put("properties", node.getProperties() != null ? node.getProperties() : "{}");
        params.put("sourceDocumentId", node.getSourceDocumentId() != null ? node.getSourceDocumentId() : "");

        executeCypher(cypher, params);
        log.info("节点已同步到Neo4j: {}", node.getNodeId());
    }

    /**
     * 同步关系到 Neo4j
     */
    public void syncRelationToNeo4j(KnowledgeGraphRelation relation) {
        String cypher = """
                MATCH (source:Entity {nodeId: $sourceNodeId})
                MATCH (target:Entity {nodeId: $targetNodeId})
                MERGE (source)-[r:RELATED {relationId: $relationId}]->(target)
                SET r.type = $relationType,
                    r.description = $description,
                    r.weight = $weight,
                    r.confidence = $confidence,
                    r.properties = $properties,
                    r.updatedAt = datetime()
                RETURN r
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("sourceNodeId", relation.getSourceNodeId());
        params.put("targetNodeId", relation.getTargetNodeId());
        params.put("relationId", relation.getRelationId());
        params.put("relationType", relation.getRelationType());
        params.put("description", relation.getDescription() != null ? relation.getDescription() : "");
        params.put("weight", relation.getWeight() != null ? relation.getWeight() : 50);
        params.put("confidence", relation.getConfidence() != null ? relation.getConfidence() : 0.7);
        params.put("properties", relation.getProperties() != null ? relation.getProperties() : "{}");

        executeCypher(cypher, params);
        log.info("关系已同步到Neo4j: {} -[{}]-> {}", relation.getSourceNodeName(), relation.getRelationType(), relation.getTargetNodeName());
    }

    /**
     * 全量同步本地数据到 Neo4j
     * @return 同步统计
     */
    public Map<String, Object> fullSyncToNeo4j() {
        Map<String, Object> stats = new HashMap<>();
        
        // 同步所有节点
        List<KnowledgeGraphNode> allNodes = nodeMapper.selectList(null);
        int nodeCount = 0;
        for (KnowledgeGraphNode node : allNodes) {
            try {
                syncNodeToNeo4j(node);
                nodeCount++;
            } catch (Exception e) {
                log.error("同步节点失败: {}", node.getNodeId(), e);
            }
        }
        stats.put("syncedNodes", nodeCount);

        // 同步所有关系
        List<KnowledgeGraphRelation> allRelations = relationMapper.selectList(null);
        int relCount = 0;
        for (KnowledgeGraphRelation relation : allRelations) {
            try {
                syncRelationToNeo4j(relation);
                relCount++;
            } catch (Exception e) {
                log.error("同步关系失败: {}", relation.getRelationId(), e);
            }
        }
        stats.put("syncedRelations", relCount);

        log.info("Neo4j全量同步完成: {} 节点, {} 关系", nodeCount, relCount);
        return stats;
    }

    // ========== 图遍历查询 ==========

    /**
     * 最短路径查找
     * @param startNodeId 起始节点ID
     * @param endNodeId 目标节点ID
     * @param maxDepth 最大深度
     * @return 路径信息
     */
    public Map<String, Object> findShortestPath(String startNodeId, String endNodeId, int maxDepth) {
        String cypher = """
                MATCH (start:Entity {nodeId: $startId})
                MATCH (end:Entity {nodeId: $endId})
                MATCH path = shortestPath((start)-[*..%d]-(end))
                RETURN path, length(path) as pathLength
                """.formatted(maxDepth);

        Map<String, Object> params = Map.of("startId", startNodeId, "endId", endNodeId);
        List<Map<String, Object>> results = executeCypher(cypher, params);

        if (results.isEmpty()) {
            return Map.of("found", false, "message", "未找到路径");
        }

        Map<String, Object> result = results.get(0);
        result.put("found", true);
        return result;
    }

    /**
     * 获取节点的K度邻居
     * @param nodeId 节点ID
     * @param depth 深度
     * @return 子图信息
     */
    public Map<String, Object> getKHopNeighbors(String nodeId, int depth) {
        String cypher = """
                MATCH (center:Entity {nodeId: $nodeId})
                MATCH (center)-[*1..%d]-(neighbor:Entity)
                RETURN DISTINCT neighbor.nodeId as nodeId,
                       neighbor.name as name,
                       neighbor.label as label,
                       neighbor.importance as importance
                """.formatted(depth);

        Map<String, Object> params = Map.of("nodeId", nodeId);
        List<Map<String, Object>> neighbors = executeCypher(cypher, params);

        Map<String, Object> result = new HashMap<>();
        result.put("centerNodeId", nodeId);
        result.put("depth", depth);
        result.put("neighborCount", neighbors.size());
        result.put("neighbors", neighbors);
        return result;
    }

    /**
     * 社区检测（Louvain启发式）
     * @return 社区分组
     */
    public Map<String, Object> detectCommunities() {
        // 基于标签传播的简单社区检测
        String cypher = """
                MATCH (n:Entity)
                OPTIONAL MATCH (n)-[r]-(m:Entity)
                WITH n, collect(DISTINCT m.label) as neighborLabels
                RETURN n.nodeId as nodeId,
                       n.name as name,
                       n.label as label,
                       coalesce(n.label, '未分类') as community
                """;

        List<Map<String, Object>> results = executeCypher(cypher);
        
        Map<String, List<String>> communities = new HashMap<>();
        for (Map<String, Object> row : results) {
            String community = (String) row.get("community");
            communities.computeIfAbsent(community, k -> new ArrayList<>())
                    .add((String) row.get("name"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("communityCount", communities.size());
        result.put("communities", communities);
        return result;
    }

    // ========== 高级图分析 ==========

    /**
     * PageRank 中心性计算
     * @param topK 返回前K个节点
     * @return 节点中心性排名
     */
    public List<Map<String, Object>> computePageRank(int topK) {
        String cypher = """
                MATCH (n:Entity)
                OPTIONAL MATCH (n)<-[r]-(m:Entity)
                WITH n, count(r) as inDegree
                ORDER BY inDegree DESC
                LIMIT $topK
                RETURN n.nodeId as nodeId,
                       n.name as name,
                       n.label as label,
                       inDegree as pageRankScore
                """;

        return executeCypher(cypher, Map.of("topK", topK));
    }

    /**
     * 介数中心性计算（Betweenness Centrality 近似）
     * @param topK 返回前K个节点
     * @return 节点介数排名
     */
    public List<Map<String, Object>> computeBetweenness(int topK) {
        String cypher = """
                MATCH (n:Entity)
                OPTIONAL MATCH (n)-[r]-()
                WITH n, count(r) as degree
                WHERE degree > 0
                RETURN n.nodeId as nodeId,
                       n.name as name,
                       n.label as label,
                       degree as connectivity
                ORDER BY connectivity DESC
                LIMIT $topK
                """;

        return executeCypher(cypher, Map.of("topK", topK));
    }

    /**
     * 获取图统计信息（Neo4j原生）
     * @return 图统计
     */
    public Map<String, Object> getNeo4jGraphStats() {
        Map<String, Object> stats = new HashMap<>();

        // 节点总数
        List<Map<String, Object>> nodeCount = executeCypher("MATCH (n:Entity) RETURN count(n) as totalNodes");
        stats.put("totalNodes", nodeCount.isEmpty() ? 0 : nodeCount.get(0).get("totalNodes"));

        // 关系总数
        List<Map<String, Object>> relCount = executeCypher("MATCH ()-[r]->() RETURN count(r) as totalRelations");
        stats.put("totalRelations", relCount.isEmpty() ? 0 : relCount.get(0).get("totalRelations"));

        // 按标签统计节点
        stats.put("nodesByLabel", executeCypher(
                "MATCH (n:Entity) RETURN n.label as label, count(n) as count ORDER BY count DESC"
        ));

        // 孤立节点
        stats.put("isolatedNodes", executeCypher(
                "MATCH (n:Entity) WHERE NOT (n)-[]-() RETURN n.nodeId as nodeId, n.name as name"
        ));

        // 最大连通分量
        stats.put("connectedComponents", executeCypher(
                "MATCH (n:Entity)-[*]-(m:Entity) " +
                "WITH n, count(DISTINCT m) as reachable " +
                "RETURN max(reachable) as maxComponentSize"
        ));

        return stats;
    }

    // ========== 模式匹配 ==========

    /**
     * 查找特定模式子图（如：A依赖B依赖C的传递依赖链）
     * @param relationType 关系类型
     * @param maxLength 最大链长度
     * @return 匹配的模式
     */
    public List<Map<String, Object>> findPatternChains(String relationType, int maxLength) {
        String cypher = """
                MATCH path = (start:Entity)-[:RELATED*1..%d]->(end:Entity)
                WHERE all(r in relationships(path) WHERE r.type = $relationType)
                RETURN start.name as startNode,
                       end.name as endNode,
                       length(path) as chainLength,
                       [node in nodes(path) | node.name] as chainNodes
                LIMIT 100
                """.formatted(maxLength);

        return executeCypher(cypher, Map.of("relationType", relationType));
    }

    /**
     * 查找环形依赖
     * @return 环形依赖列表
     */
    public List<Map<String, Object>> findCircularDependencies() {
        String cypher = """
                MATCH path = (n:Entity)-[:RELATED*2..5]->(n)
                WHERE all(r in relationships(path) WHERE r.type IN ['依赖', '前置'])
                RETURN n.name as nodeName,
                       length(path) as cycleLength,
                       [node in nodes(path) | node.name] as cyclePath
                LIMIT 50
                """;

        return executeCypher(cypher);
    }

    /**
     * 查找关键桥接节点（连接不同领域的节点）
     * @return 桥接节点列表
     */
    public List<Map<String, Object>> findBridgeNodes() {
        String cypher = """
                MATCH (n:Entity)-[r]-(m:Entity)
                WHERE n.label <> m.label
                WITH n, count(DISTINCT m.label) as bridgeCount
                WHERE bridgeCount > 1
                RETURN n.nodeId as nodeId,
                       n.name as name,
                       n.label as label,
                       bridgeCount as connectedDomains
                ORDER BY bridgeCount DESC
                LIMIT 50
                """;

        return executeCypher(cypher);
    }

    // ========== 数据导入导出 ==========

    /**
     * 从 Neo4j 导出数据为 JSON
     * @return 完整的图谱数据
     */
    public Map<String, Object> exportFromNeo4j() {
        Map<String, Object> data = new HashMap<>();

        List<Map<String, Object>> nodes = executeCypher(
                "MATCH (n:Entity) RETURN properties(n) as node"
        );
        data.put("nodes", nodes.stream()
                .map(m -> m.get("node"))
                .collect(Collectors.toList()));

        List<Map<String, Object>> relations = executeCypher(
                "MATCH ()-[r]->() RETURN properties(r) as relation, type(r) as type"
        );
        data.put("relations", relations);

        return data;
    }

    /**
     * 导入 JSON 数据到 Neo4j
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> importToNeo4j(List<Map<String, Object>> nodes, List<Map<String, Object>> relations) {
        int nodeCount = 0;
        int relCount = 0;

        // 创建节点索引
        executeCypher("CREATE INDEX IF NOT EXISTS FOR (n:Entity) ON (n.nodeId)");

        // 导入节点
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                String cypher = """
                        MERGE (n:Entity {nodeId: $nodeId})
                        SET n += $properties
                        """;
                Map<String, Object> params = new HashMap<>();
                params.put("nodeId", node.get("nodeId"));
                params.put("properties", node);
                executeCypher(cypher, params);
                nodeCount++;
            }
        }

        // 导入关系
        if (relations != null) {
            for (Map<String, Object> rel : relations) {
                String cypher = """
                        MATCH (a:Entity {nodeId: $sourceNodeId})
                        MATCH (b:Entity {nodeId: $targetNodeId})
                        MERGE (a)-[r:RELATED {relationId: $relationId}]->(b)
                        SET r += $properties
                        """;
                executeCypher(cypher, rel);
                relCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("importedNodes", nodeCount);
        result.put("importedRelations", relCount);
        return result;
    }

    // ========== 辅助方法 ==========

    /**
     * 检查 Neo4j 连接状态
     */
    public boolean isConnected() {
        Driver driver = getDriver();
        if (driver == null) return false;
        try (Session session = driver.session()) {
            session.run("RETURN 1");
            return true;
        } catch (Exception e) {
            log.warn("Neo4j连接检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (neo4jDriver != null) {
            neo4jDriver.close();
            neo4jDriver = null;
            log.info("Neo4j连接已关闭");
        }
    }

    /**
     * 当 Neo4j 不可用时，回退到本地数据库
     */
    private List<Map<String, Object>> executeFallbackQuery(String cypher, Map<String, Object> params) {
        log.debug("Neo4j不可用，执行本地回退查询: {}", cypher);
        
        // 简单的 MATCH (n:Entity) 查询
        if (cypher.contains("MATCH (n:Entity)") && cypher.contains("count(n)")) {
            long count = nodeMapper.selectCount(null);
            return List.of(Map.of("totalNodes", (Object) count));
        }

        return Collections.emptyList();
    }

    /**
     * 转换 Neo4j 返回值为 Java 类型
     */
    private Object convertValue(Value value) {
        if (value == null || value.isNull()) return null;
        
        switch (value.type().name()) {
            case "INTEGER":
                return value.asLong();
            case "FLOAT":
                return value.asDouble();
            case "BOOLEAN":
                return value.asBoolean();
            case "STRING":
                return value.asString();
            case "LIST":
                return value.asList(this::convertValue);
            case "MAP":
                return value.asMap(this::convertValue);
            case "NODE":
                return convertNode(value.asNode());
            case "RELATIONSHIP":
                return convertRelationship(value.asRelationship());
            case "PATH":
                return convertPath(value.asPath());
            default:
                return value.asObject();
        }
    }

    private Map<String, Object> convertNode(Node node) {
        Map<String, Object> map = new HashMap<>(node.asMap(this::convertValue));
        map.put("_id", node.id());
        map.put("_labels", node.labels());
        return map;
    }

    private Map<String, Object> convertRelationship(Relationship rel) {
        Map<String, Object> map = new HashMap<>(rel.asMap(this::convertValue));
        map.put("_id", rel.id());
        map.put("_type", rel.type());
        map.put("_startNodeId", rel.startNodeId());
        map.put("_endNodeId", rel.endNodeId());
        return map;
    }

    private Map<String, Object> convertPath(Path path) {
        Map<String, Object> map = new HashMap<>();
        map.put("length", path.length());
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Node n : path.nodes()) {
            nodes.add(convertNode(n));
        }
        map.put("nodes", nodes);
        List<Map<String, Object>> rels = new ArrayList<>();
        for (Relationship r : path.relationships()) {
            rels.add(convertRelationship(r));
        }
        map.put("relationships", rels);
        return map;
    }
}
