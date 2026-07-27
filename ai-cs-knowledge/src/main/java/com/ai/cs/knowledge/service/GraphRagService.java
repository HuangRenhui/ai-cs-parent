package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.ai.cs.knowledge.util.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphRAG 服务 - 知识图谱与RAG检索深度融合
 * 结合图谱结构化知识和向量检索的优势，提供更精准的问答能力
 * 
 * 增强功能：知识图谱与RAG检索深度融合（GraphRAG）
 */
@Slf4j
@Service
public class GraphRagService {

    private final KnowledgeGraphService graphService;
    private final HybridRetrievalService hybridRetrievalService;
    private final LlmClient llmClient;
    private final PromptTemplateService promptTemplateService;

    public GraphRagService(KnowledgeGraphService graphService,
                            HybridRetrievalService hybridRetrievalService,
                            LlmClient llmClient,
                            PromptTemplateService promptTemplateService) {
        this.graphService = graphService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.llmClient = llmClient;
        this.promptTemplateService = promptTemplateService;
    }

    // ========== GraphRAG 核心问答 ==========

    /**
     * GraphRAG 问答：融合图谱上下文和向量检索结果
     * @param question 用户问题
     * @param topK 向量检索返回数量
     * @return 综合回答
     */
    public Map<String, Object> graphRagQa(String question, int topK) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 从图谱中提取结构化上下文
            Map<String, Object> graphContext = extractGraphContext(question);
            result.put("graphContext", graphContext);

            // 2. 从向量库检索语义相似文档
            List<Map<String, Object>> vectorResults = retrieveFromVectorStore(question, topK);
            result.put("vectorResults", vectorResults);

            // 3. 融合两种上下文构建增强Prompt
            String enhancedPrompt = buildEnhancedPrompt(question, graphContext, vectorResults);

            // 4. 调用LLM生成最终回答（使用增强Prompt模板）
            String systemPrompt = promptTemplateService.buildSystemPrompt();
            String answer = llmClient.callWithSystem(systemPrompt, enhancedPrompt);
            result.put("answer", answer);

            // 5. 提取引用来源
            List<Map<String, Object>> citations = extractCitations(graphContext, vectorResults);
            result.put("citations", citations);

            result.put("success", true);
        } catch (Exception e) {
            log.error("GraphRAG问答失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 实体增强检索：先通过图谱定位实体，再向量检索
     * @param question 用户问题
     * @return 增强检索结果
     */
    public Map<String, Object> entityEnhancedRetrieval(String question) {
        Map<String, Object> result = new HashMap<>();

        // 1. 从问题中识别关键实体
        List<KnowledgeGraphNode> entities = graphService.searchRelatedNodes(question);
        result.put("identifiedEntities", entities.stream()
                .map(n -> Map.of("name", n.getName(), "label", n.getLabel()))
                .collect(Collectors.toList()));

        // 2. 展开实体子图（1度邻居）
        List<Map<String, Object>> entityContexts = new ArrayList<>();
        for (KnowledgeGraphNode entity : entities) {
            Map<String, Object> entityContext = new HashMap<>();
            entityContext.put("entity", entity.getName());
            entityContext.put("label", entity.getLabel());

            // 获取关系
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(entity.getNodeId());
            List<Map<String, String>> relatedEntities = new ArrayList<>();
            for (KnowledgeGraphRelation rel : relations) {
                String relatedName = rel.getSourceNodeId().equals(entity.getNodeId())
                        ? rel.getTargetNodeName() : rel.getSourceNodeName();
                relatedEntities.add(Map.of(
                        "entity", relatedName,
                        "relation", rel.getRelationType(),
                        "direction", rel.getSourceNodeId().equals(entity.getNodeId()) ? "outgoing" : "incoming"
                ));
            }
            entityContext.put("relatedEntities", relatedEntities);
            entityContexts.add(entityContext);
        }
        result.put("entityContexts", entityContexts);

        // 3. 用实体信息增强向量检索
        StringBuilder entityEnhancedQuery = new StringBuilder(question);
        if (!entities.isEmpty()) {
            entityEnhancedQuery.append(" 相关实体: ");
            entityEnhancedQuery.append(entities.stream()
                    .map(KnowledgeGraphNode::getName)
                    .collect(Collectors.joining(", ")));
        }

        List<Map<String, Object>> enhancedResults = retrieveFromVectorStore(
                entityEnhancedQuery.toString(), 5);
        result.put("enhancedResults", enhancedResults);

        return result;
    }

    /**
     * 图谱引导的检索路径：根据图谱结构指导检索方向
     * @param question 用户问题
     * @param maxPathLength 最大路径长度
     * @return 检索路径和结果
     */
    public Map<String, Object> graphGuidedRetrieval(String question, int maxPathLength) {
        Map<String, Object> result = new HashMap<>();

        // 1. 定位起始实体
        List<KnowledgeGraphNode> startNodes = graphService.searchRelatedNodes(question);
        if (startNodes.isEmpty()) {
            result.put("found", false);
            result.put("message", "未找到相关实体");
            return result;
        }

        result.put("startNodes", startNodes.stream()
                .map(KnowledgeGraphNode::getName)
                .collect(Collectors.toList()));

        // 2. 沿图谱路径逐步检索
        List<Map<String, Object>> retrievalPaths = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (KnowledgeGraphNode startNode : startNodes) {
            exploreAndRetrieve(startNode, visited, retrievalPaths, 0, maxPathLength);
        }

        result.put("retrievalPaths", retrievalPaths);
        result.put("totalPaths", retrievalPaths.size());
        result.put("found", !retrievalPaths.isEmpty());

        return result;
    }

    // ========== 上下文提取与融合 ==========

    /**
     * 从知识图谱提取问题相关上下文
     */
    private Map<String, Object> extractGraphContext(String question) {
        Map<String, Object> context = new HashMap<>();

        // 相关实体
        List<KnowledgeGraphNode> relatedNodes = graphService.searchRelatedNodes(question);
        List<Map<String, Object>> entityList = new ArrayList<>();
        for (KnowledgeGraphNode node : relatedNodes) {
            Map<String, Object> entity = new HashMap<>();
            entity.put("name", node.getName());
            entity.put("label", node.getLabel());
            entity.put("description", node.getDescription());
            entity.put("importance", node.getImportance());
            entityList.add(entity);
        }
        context.put("entities", entityList);

        // 提取实体间的关系链
        List<Map<String, Object>> relationChains = new ArrayList<>();
        for (int i = 0; i < relatedNodes.size() && relationChains.size() < 10; i++) {
            for (int j = i + 1; j < relatedNodes.size() && relationChains.size() < 10; j++) {
                // 查找两个实体间的关系
                List<KnowledgeGraphRelation> rels1 = graphService.getNodeRelations(relatedNodes.get(i).getNodeId());
                for (KnowledgeGraphRelation rel : rels1) {
                    String otherId = rel.getSourceNodeId().equals(relatedNodes.get(i).getNodeId())
                            ? rel.getTargetNodeId() : rel.getSourceNodeId();
                    if (otherId.equals(relatedNodes.get(j).getNodeId())) {
                        Map<String, Object> chain = new HashMap<>();
                        chain.put("path", relatedNodes.get(i).getName() + " -[" + rel.getRelationType() + "]-> " 
                                + relatedNodes.get(j).getName());
                        chain.put("relationType", rel.getRelationType());
                        chain.put("description", rel.getDescription());
                        relationChains.add(chain);
                    }
                }
            }
        }
        context.put("relationChains", relationChains);

        return context;
    }

    /**
     * 从向量库检索
     */
    private List<Map<String, Object>> retrieveFromVectorStore(String query, int topK) {
        try {
            // 通过混合检索服务进行向量检索（使用默认模态）
            List<HybridRetrievalService.HybridSearchResult> retrievalResults = 
                    hybridRetrievalService.hybridSearch(query, null, topK);
            if (retrievalResults != null) {
                return retrievalResults.stream()
                        .map(r -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", r.id);
                            m.put("content", r.content);
                            m.put("modality", r.modality);
                            m.put("score", r.score);
                            return m;
                        })
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("向量检索失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 构建增强Prompt
     */
    private String buildEnhancedPrompt(String question, Map<String, Object> graphContext,
                                        List<Map<String, Object>> vectorResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于以下结构化知识和文档内容回答问题。\n\n");

        // 图谱结构化知识
        prompt.append("【知识图谱结构化知识】\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) graphContext.get("entities");
        if (entities != null && !entities.isEmpty()) {
            prompt.append("相关实体：\n");
            for (Map<String, Object> entity : entities) {
                prompt.append(String.format("- %s (类型: %s): %s\n",
                        entity.get("name"), entity.get("label"),
                        entity.getOrDefault("description", "")));
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chains = (List<Map<String, Object>>) graphContext.get("relationChains");
        if (chains != null && !chains.isEmpty()) {
            prompt.append("\n实体关系：\n");
            for (Map<String, Object> chain : chains) {
                prompt.append("- ").append(chain.get("path")).append("\n");
            }
        }

        // 向量检索结果
        prompt.append("\n【相关文档内容】\n");
        if (vectorResults != null && !vectorResults.isEmpty()) {
            for (int i = 0; i < vectorResults.size(); i++) {
                Map<String, Object> doc = vectorResults.get(i);
                prompt.append(String.format("%d. %s\n", i + 1,
                        doc.getOrDefault("content", doc.getOrDefault("text", ""))));
            }
        } else {
            prompt.append("（无相关文档）\n");
        }

        prompt.append("\n问题：").append(question).append("\n\n");
        prompt.append("请综合以上信息，提供准确、完整的回答。如果信息不足，请明确指出。");

        return prompt.toString();
    }

    /**
     * 提取引用来源
     */
    private List<Map<String, Object>> extractCitations(Map<String, Object> graphContext,
                                                         List<Map<String, Object>> vectorResults) {
        List<Map<String, Object>> citations = new ArrayList<>();

        // 图谱来源
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) graphContext.get("entities");
        if (entities != null) {
            for (Map<String, Object> entity : entities) {
                citations.add(Map.of(
                        "type", "knowledge_graph",
                        "source", entity.get("name"),
                        "label", entity.getOrDefault("label", ""),
                        "description", entity.getOrDefault("description", "")
                ));
            }
        }

        // 向量检索来源
        if (vectorResults != null) {
            for (Map<String, Object> doc : vectorResults) {
                citations.add(Map.of(
                        "type", "vector_retrieval",
                        "source", doc.getOrDefault("source", doc.getOrDefault("id", "unknown")),
                        "score", doc.getOrDefault("score", 0.0)
                ));
            }
        }

        return citations;
    }

    /**
     * 沿图谱路径探索和检索
     */
    private void exploreAndRetrieve(KnowledgeGraphNode node, Set<String> visited,
                                     List<Map<String, Object>> paths, int depth, int maxDepth) {
        if (depth > maxDepth || visited.contains(node.getNodeId())) return;

        visited.add(node.getNodeId());

        Map<String, Object> pathNode = new HashMap<>();
        pathNode.put("depth", depth);
        pathNode.put("entity", node.getName());
        pathNode.put("label", node.getLabel());

        List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
        List<Map<String, String>> nextEntities = new ArrayList<>();
        for (KnowledgeGraphRelation rel : relations) {
            String nextId = rel.getSourceNodeId().equals(node.getNodeId())
                    ? rel.getTargetNodeId() : rel.getSourceNodeId();
            String nextName = rel.getSourceNodeId().equals(node.getNodeId())
                    ? rel.getTargetNodeName() : rel.getSourceNodeName();

            nextEntities.add(Map.of(
                    "entity", nextName,
                    "relation", rel.getRelationType()
            ));

            if (depth < maxDepth && !visited.contains(nextId)) {
                KnowledgeGraphNode nextNode = graphService.findById(nextId);
                if (nextNode != null) {
                    exploreAndRetrieve(nextNode, visited, paths, depth + 1, maxDepth);
                }
            }
        }

        pathNode.put("nextEntities", nextEntities);
        paths.add(pathNode);
    }

    // ========== 混合排序融合 ==========

    /**
     * 图谱增强的排序融合：将图谱相关性分数与向量检索分数融合
     * @param question 查询问题
     * @param vectorResults 向量检索结果
     * @return 融合排序后的结果
     */
    public List<Map<String, Object>> graphEnhancedRanking(String question, List<Map<String, Object>> vectorResults) {
        // 获取图谱相关实体
        List<KnowledgeGraphNode> graphEntities = graphService.searchRelatedNodes(question);
        Set<String> graphEntityNames = graphEntities.stream()
                .map(KnowledgeGraphNode::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // 为每个结果计算图谱增强分数
        for (Map<String, Object> result : vectorResults) {
            double vectorScore = ((Number) result.getOrDefault("score", 0.0)).doubleValue();
            double graphBonus = 0.0;

            // 检查结果内容是否包含图谱实体
            String content = (String) result.getOrDefault("content", result.getOrDefault("text", ""));
            if (content != null) {
                for (String entityName : graphEntityNames) {
                    if (content.toLowerCase().contains(entityName)) {
                        graphBonus += 0.15; // 每个匹配的实体加0.15分
                    }
                }
            }

            // 加权融合
            double fusedScore = vectorScore * 0.6 + Math.min(graphBonus, 0.4);
            result.put("fusedScore", fusedScore);
            result.put("vectorScore", vectorScore);
            result.put("graphBonus", graphBonus);
        }

        // 按融合分数排序
        vectorResults.sort((a, b) -> {
            double sa = ((Number) a.getOrDefault("fusedScore", 0.0)).doubleValue();
            double sb = ((Number) b.getOrDefault("fusedScore", 0.0)).doubleValue();
            return Double.compare(sb, sa);
        });

        return vectorResults;
    }

    /**
     * 获取图谱增强的检索统计信息
     */
    public Map<String, Object> getGraphRagStats() {
        Map<String, Object> stats = new HashMap<>();

        // 图谱统计
        Map<String, Object> graphStats = graphService.getGraphStatistics();
        stats.put("graphStats", graphStats);

        // 图谱覆盖率（有实体连接的文档比例）
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        Set<String> docIds = new HashSet<>();
        for (KnowledgeGraphNode node : allNodes) {
            if (node.getSourceDocumentId() != null && !node.getSourceDocumentId().isEmpty()) {
                docIds.add(node.getSourceDocumentId());
            }
        }
        stats.put("documentsInGraph", docIds.size());

        // 实体密度
        long totalRelations = ((Number) graphStats.getOrDefault("totalRelations", 0)).longValue();
        long totalNodes = ((Number) graphStats.getOrDefault("totalNodes", 0)).longValue();
        double density = totalNodes > 0 ? (double) totalRelations / totalNodes : 0;
        stats.put("graphDensity", String.format("%.2f", density));
        stats.put("avgRelationsPerNode", String.format("%.2f", density));

        return stats;
    }
}
