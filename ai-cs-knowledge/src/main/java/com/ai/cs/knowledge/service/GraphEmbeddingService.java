package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱嵌入（Graph Embedding）服务
 * 支持节点向量化表示、链接预测、节点分类、相似度计算
 * 
 * 增强功能：图谱嵌入（Graph Embedding）用于链接预测
 */
@Slf4j
@Service
public class GraphEmbeddingService {

    private final KnowledgeGraphService graphService;

    // 嵌入向量维度
    private static final int EMBEDDING_DIM = 128;

    // 节点嵌入缓存
    private final Map<String, double[]> nodeEmbeddings = new HashMap<>();

    // 关系类型嵌入缓存
    private final Map<String, double[]> relationEmbeddings = new HashMap<>();

    public GraphEmbeddingService(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    // ========== 嵌入生成 ==========

    /**
     * 为所有节点生成嵌入向量（基于DeepWalk启发式随机游走 + 简化版Node2Vec）
     * @return 生成统计
     */
    public Map<String, Object> generateAllEmbeddings() {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        Map<String, Integer> nodeIndex = new HashMap<>();
        List<String> nodeIds = new ArrayList<>();

        for (int i = 0; i < allNodes.size(); i++) {
            nodeIndex.put(allNodes.get(i).getNodeId(), i);
            nodeIds.add(allNodes.get(i).getNodeId());
        }

        // 构建邻接矩阵
        int n = allNodes.size();
        double[][] adjacency = new double[n][n];

        for (KnowledgeGraphNode node : allNodes) {
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
            for (KnowledgeGraphRelation rel : relations) {
                int si = nodeIndex.get(rel.getSourceNodeId());
                int ti = nodeIndex.get(rel.getTargetNodeId());
                double weight = rel.getWeight() != null ? rel.getWeight() / 100.0 : 0.5;
                adjacency[si][ti] = Math.max(adjacency[si][ti], weight);
                adjacency[ti][si] = Math.max(adjacency[ti][si], weight);
            }
        }

        // 简化版随机游走 + SVD近似
        // 使用邻接矩阵的幂迭代来近似特征向量
        Random random = new Random(42);
        int walksPerNode = 10;
        int walkLength = 20;

        // 初始化嵌入
        for (int i = 0; i < n; i++) {
            double[] emb = new double[EMBEDDING_DIM];
            for (int d = 0; d < EMBEDDING_DIM; d++) {
                emb[d] = random.nextGaussian() * 0.1;
            }
            nodeEmbeddings.put(nodeIds.get(i), emb);
        }

        // 随机游走 + SkipGram 训练（简化版）
        for (int epoch = 0; epoch < 5; epoch++) {
            for (int i = 0; i < n; i++) {
                for (int w = 0; w < walksPerNode; w++) {
                    List<Integer> walk = randomWalk(i, adjacency, walkLength, random);
                    // SkipGram更新（简化版：对相邻节点拉近嵌入）
                    for (int j = 0; j < walk.size(); j++) {
                        int center = walk.get(j);
                        for (int k = Math.max(0, j - 2); k <= Math.min(walk.size() - 1, j + 2); k++) {
                            if (j != k) {
                                int context = walk.get(k);
                                updateEmbedding(nodeIds.get(center), nodeIds.get(context), 0.01);
                            }
                        }
                    }
                }
            }
        }

        // 生成关系类型嵌入
        Set<String> relationTypes = new HashSet<>();
        for (KnowledgeGraphNode node : allNodes) {
            for (KnowledgeGraphRelation rel : graphService.getNodeRelations(node.getNodeId())) {
                relationTypes.add(rel.getRelationType());
            }
        }

        for (String type : relationTypes) {
            double[] emb = new double[EMBEDDING_DIM];
            for (int d = 0; d < EMBEDDING_DIM; d++) {
                emb[d] = random.nextGaussian() * 0.1;
            }
            relationEmbeddings.put(type, emb);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", n);
        stats.put("embeddingDim", EMBEDDING_DIM);
        stats.put("relationTypes", relationTypes.size());
        stats.put("epochs", 5);
        return stats;
    }

    // ========== 链接预测 ==========

    /**
     * 预测两个节点之间可能存在的关系
     * @param nodeId1 节点1 ID
     * @param nodeId2 节点2 ID
     * @return 预测结果
     */
    public Map<String, Object> predictLink(String nodeId1, String nodeId2) {
        KnowledgeGraphNode node1 = graphService.findById(nodeId1);
        KnowledgeGraphNode node2 = graphService.findById(nodeId2);

        if (node1 == null || node2 == null) {
            return Map.of("predicted", false, "message", "节点不存在");
        }

        double[] emb1 = getNodeEmbedding(nodeId1);
        double[] emb2 = getNodeEmbedding(nodeId2);

        if (emb1 == null || emb2 == null) {
            // 没有嵌入时使用启发式预测
            return heuristicLinkPrediction(node1, node2);
        }

        // 计算余弦相似度
        double similarity = cosineSimilarity(emb1, emb2);

        // 预测可能存在的关系类型
        Map<String, Double> predictedTypes = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : relationEmbeddings.entrySet()) {
            double relScore = cosineSimilarity(subtract(emb2, emb1), entry.getValue());
            predictedTypes.put(entry.getKey(), relScore);
        }

        // 按分数排序
        List<Map.Entry<String, Double>> sorted = predictedTypes.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("predicted", true);
        result.put("node1Name", node1.getName());
        result.put("node2Name", node2.getName());
        result.put("similarity", similarity);
        result.put("topPredictions", sorted.subList(0, Math.min(3, sorted.size())).stream()
                .map(e -> Map.of("relationType", e.getKey(), "score", e.getValue()))
                .collect(Collectors.toList()));

        return result;
    }

    /**
     * 批量链接预测：找出最可能存在缺失关系的节点对
     * @param topK 返回前K个预测
     * @return 预测结果列表
     */
    public List<Map<String, Object>> predictMissingLinks(int topK) {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        List<Map<String, Object>> predictions = new ArrayList<>();

        // 获取已有的关系对
        Set<String> existingPairs = new HashSet<>();
        for (KnowledgeGraphNode node : allNodes) {
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
            for (KnowledgeGraphRelation rel : relations) {
                String pair = rel.getSourceNodeId() + "_" + rel.getTargetNodeId();
                existingPairs.add(pair);
                existingPairs.add(rel.getTargetNodeId() + "_" + rel.getSourceNodeId());
            }
        }

        // 计算未连接节点对的相似度
        for (int i = 0; i < allNodes.size() && predictions.size() < topK * 2; i++) {
            for (int j = i + 1; j < allNodes.size() && predictions.size() < topK * 2; j++) {
                String pair = allNodes.get(i).getNodeId() + "_" + allNodes.get(j).getNodeId();
                if (existingPairs.contains(pair)) continue;

                Map<String, Object> prediction = predictLink(
                        allNodes.get(i).getNodeId(), allNodes.get(j).getNodeId());
                
                double similarity = (double) prediction.getOrDefault("similarity", 0.0);
                if (similarity > 0.3) {
                    predictions.add(prediction);
                }
            }
        }

        // 按相似度排序
        predictions.sort((a, b) -> {
            double sa = (double) a.getOrDefault("similarity", 0.0);
            double sb = (double) b.getOrDefault("similarity", 0.0);
            return Double.compare(sb, sa);
        });

        return predictions.size() > topK ? predictions.subList(0, topK) : predictions;
    }

    // ========== 节点相似度 ==========

    /**
     * 计算两个节点的相似度
     */
    public double computeNodeSimilarity(String nodeId1, String nodeId2) {
        double[] emb1 = getNodeEmbedding(nodeId1);
        double[] emb2 = getNodeEmbedding(nodeId2);

        if (emb1 == null || emb2 == null) {
            return 0.0;
        }

        return cosineSimilarity(emb1, emb2);
    }

    /**
     * 查找与指定节点最相似的K个节点
     * @param nodeId 节点ID
     * @param topK 返回数量
     * @return 相似节点列表
     */
    public List<Map<String, Object>> findSimilarNodes(String nodeId, int topK) {
        double[] targetEmb = getNodeEmbedding(nodeId);
        if (targetEmb == null) {
            return Collections.emptyList();
        }

        KnowledgeGraphNode targetNode = graphService.findById(nodeId);
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();

        List<Map<String, Object>> results = new ArrayList<>();
        for (KnowledgeGraphNode node : allNodes) {
            if (node.getNodeId().equals(nodeId)) continue;

            double[] emb = getNodeEmbedding(node.getNodeId());
            if (emb != null) {
                double similarity = cosineSimilarity(targetEmb, emb);
                Map<String, Object> item = new HashMap<>();
                item.put("nodeId", node.getNodeId());
                item.put("nodeName", node.getName());
                item.put("label", node.getLabel());
                item.put("similarity", similarity);
                results.add(item);
            }
        }

        results.sort((a, b) -> {
            double sa = (double) a.get("similarity");
            double sb = (double) b.get("similarity");
            return Double.compare(sb, sa);
        });

        return results.size() > topK ? results.subList(0, topK) : results;
    }

    // ========== 节点分类 ==========

    /**
     * 基于嵌入对节点进行聚类
     * @param numClusters 聚类数量
     * @return 聚类结果
     */
    public Map<String, Object> clusterNodes(int numClusters) {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        
        // K-Means简化版聚类
        // 1. 初始化聚类中心
        Random random = new Random(42);
        List<double[]> centroids = new ArrayList<>();
        Set<Integer> chosenIndices = new HashSet<>();

        for (int c = 0; c < numClusters && c < allNodes.size(); c++) {
            int idx;
            do {
                idx = random.nextInt(allNodes.size());
            } while (chosenIndices.contains(idx));
            chosenIndices.add(idx);

            double[] emb = getNodeEmbedding(allNodes.get(idx).getNodeId());
            if (emb != null) {
                centroids.add(emb.clone());
            }
        }

        if (centroids.isEmpty()) {
            return Map.of("clusters", Collections.emptyList());
        }

        // 2. 迭代分配
        Map<Integer, List<String>> clusters = new HashMap<>();
        for (int iter = 0; iter < 10; iter++) {
            clusters.clear();
            for (int c = 0; c < centroids.size(); c++) {
                clusters.put(c, new ArrayList<>());
            }

            for (KnowledgeGraphNode node : allNodes) {
                double[] emb = getNodeEmbedding(node.getNodeId());
                if (emb == null) continue;

                int bestCluster = 0;
                double bestDist = Double.MAX_VALUE;
                for (int c = 0; c < centroids.size(); c++) {
                    double dist = euclideanDistance(emb, centroids.get(c));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestCluster = c;
                    }
                }
                clusters.get(bestCluster).add(node.getName());
            }

            // 更新中心点
            for (int c = 0; c < centroids.size(); c++) {
                double[] newCentroid = new double[EMBEDDING_DIM];
                List<String> members = clusters.get(c);
                int count = 0;
                for (String name : members) {
                    KnowledgeGraphNode node = graphService.findByName(name);
                    if (node != null) {
                        double[] emb = getNodeEmbedding(node.getNodeId());
                        if (emb != null) {
                            for (int d = 0; d < EMBEDDING_DIM; d++) {
                                newCentroid[d] += emb[d];
                            }
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    for (int d = 0; d < EMBEDDING_DIM; d++) {
                        newCentroid[d] /= count;
                    }
                    centroids.set(c, newCentroid);
                }
            }
        }

        List<Map<String, Object>> clusterResults = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : clusters.entrySet()) {
            Map<String, Object> cluster = new HashMap<>();
            cluster.put("clusterId", entry.getKey());
            cluster.put("size", entry.getValue().size());
            cluster.put("members", entry.getValue().stream().limit(20).collect(Collectors.toList()));
            clusterResults.add(cluster);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("numClusters", centroids.size());
        result.put("clusters", clusterResults);
        return result;
    }

    // ========== 嵌入管理 ==========

    /**
     * 获取节点嵌入向量
     */
    public Map<String, Object> getNodeEmbeddingData(String nodeId) {
        double[] emb = nodeEmbeddings.get(nodeId);
        KnowledgeGraphNode node = graphService.findById(nodeId);

        if (emb == null || node == null) {
            return Map.of("found", false);
        }

        List<Double> vector = new ArrayList<>();
        for (double v : emb) {
            vector.add(Math.round(v * 10000.0) / 10000.0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("found", true);
        result.put("nodeId", nodeId);
        result.put("nodeName", node.getName());
        result.put("dimension", EMBEDDING_DIM);
        result.put("vector", vector);
        return result;
    }

    /**
     * 导出所有节点嵌入（用于外部模型）
     */
    public Map<String, List<Double>> exportAllEmbeddings() {
        Map<String, List<Double>> export = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : nodeEmbeddings.entrySet()) {
            List<Double> vec = new ArrayList<>();
            for (double v : entry.getValue()) {
                vec.add(v);
            }
            export.put(entry.getKey(), vec);
        }
        return export;
    }

    // ========== 辅助方法 ==========

    /**
     * 获取节点嵌入（如不存在则随机生成）
     */
    private double[] getNodeEmbedding(String nodeId) {
        double[] emb = nodeEmbeddings.get(nodeId);
        if (emb == null) {
            // 如果嵌入未生成，返回基于节点ID哈希的伪随机向量
            emb = new double[EMBEDDING_DIM];
            Random r = new Random(nodeId.hashCode());
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                emb[i] = r.nextGaussian() * 0.1;
            }
            nodeEmbeddings.put(nodeId, emb);
        }
        return emb;
    }

    /**
     * 随机游走
     */
    private List<Integer> randomWalk(int startNode, double[][] adjacency, int length, Random random) {
        List<Integer> walk = new ArrayList<>();
        walk.add(startNode);
        int current = startNode;

        for (int step = 1; step < length; step++) {
            // 获取邻居
            List<Integer> neighbors = new ArrayList<>();
            List<Double> weights = new ArrayList<>();

            for (int j = 0; j < adjacency.length; j++) {
                if (adjacency[current][j] > 0) {
                    neighbors.add(j);
                    weights.add(adjacency[current][j]);
                }
            }

            if (neighbors.isEmpty()) break;

            // 按权重随机选择邻居
            double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();
            double r = random.nextDouble() * totalWeight;
            double cumulative = 0;
            int chosen = neighbors.get(neighbors.size() - 1);

            for (int i = 0; i < neighbors.size(); i++) {
                cumulative += weights.get(i);
                if (r <= cumulative) {
                    chosen = neighbors.get(i);
                    break;
                }
            }

            walk.add(chosen);
            current = chosen;
        }

        return walk;
    }

    /**
     * 更新嵌入（简化版SGD）
     */
    private void updateEmbedding(String centerId, String contextId, double learningRate) {
        double[] center = nodeEmbeddings.get(centerId);
        double[] context = nodeEmbeddings.get(contextId);

        if (center == null || context == null) return;

        // 简化版梯度更新：将两个嵌入拉近
        for (int d = 0; d < EMBEDDING_DIM; d++) {
            double diff = context[d] - center[d];
            center[d] += learningRate * diff;
            context[d] -= learningRate * diff;
        }
    }

    /**
     * 余弦相似度
     */
    private double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;

        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 欧几里得距离
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * 向量减法
     */
    private double[] subtract(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] - b[i];
        }
        return result;
    }

    /**
     * 启发式链接预测（无嵌入时使用）
     */
    private Map<String, Object> heuristicLinkPrediction(KnowledgeGraphNode node1, KnowledgeGraphNode node2) {
        Map<String, Object> result = new HashMap<>();
        result.put("predicted", true);
        result.put("node1Name", node1.getName());
        result.put("node2Name", node2.getName());
        result.put("method", "heuristic");

        // 标签匹配度
        double score = 0.0;
        if (node1.getLabel() != null && node1.getLabel().equals(node2.getLabel())) {
            score += 0.3;
        }

        // 共有邻居数量（Jaccard相似度）
        List<KnowledgeGraphRelation> rels1 = graphService.getNodeRelations(node1.getNodeId());
        List<KnowledgeGraphRelation> rels2 = graphService.getNodeRelations(node2.getNodeId());

        Set<String> neighbors1 = new HashSet<>();
        for (KnowledgeGraphRelation rel : rels1) {
            neighbors1.add(rel.getSourceNodeId().equals(node1.getNodeId()) 
                    ? rel.getTargetNodeId() : rel.getSourceNodeId());
        }

        Set<String> neighbors2 = new HashSet<>();
        for (KnowledgeGraphRelation rel : rels2) {
            neighbors2.add(rel.getSourceNodeId().equals(node2.getNodeId()) 
                    ? rel.getTargetNodeId() : rel.getSourceNodeId());
        }

        Set<String> intersection = new HashSet<>(neighbors1);
        intersection.retainAll(neighbors2);
        
        Set<String> union = new HashSet<>(neighbors1);
        union.addAll(neighbors2);

        double jaccard = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
        score += jaccard * 0.5;

        // 描述相似度
        if (node1.getDescription() != null && node2.getDescription() != null) {
            Set<String> words1 = new HashSet<>(Arrays.asList(node1.getDescription().split("\\s+")));
            Set<String> words2 = new HashSet<>(Arrays.asList(node2.getDescription().split("\\s+")));
            Set<String> wordIntersection = new HashSet<>(words1);
            wordIntersection.retainAll(words2);
            double descSimilarity = words1.isEmpty() || words2.isEmpty() ? 0 
                    : (double) wordIntersection.size() / Math.max(words1.size(), words2.size());
            score += descSimilarity * 0.2;
        }

        result.put("similarity", Math.min(score, 1.0));
        result.put("sharedNeighbors", intersection.size());
        result.put("jaccardIndex", jaccard);

        return result;
    }
}
