package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.ai.cs.knowledge.mapper.MultimodalKnowledgeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多模态知识自动关联与聚类服务
 * 自动发现不同模态知识条目之间的关联关系，进行聚类分组
 *
 * 增强功能：多模态知识自动关联与聚类
 */
@Slf4j
@Service
public class KnowledgeAutoClusterService {

    private final MultimodalKnowledgeMapper knowledgeMapper;
    private final KnowledgeGraphService graphService;

    public KnowledgeAutoClusterService(MultimodalKnowledgeMapper knowledgeMapper,
                                        KnowledgeGraphService graphService) {
        this.knowledgeMapper = knowledgeMapper;
        this.graphService = graphService;
    }

    /**
     * 知识聚类结果
     */
    public static class KnowledgeCluster {
        public String clusterId;
        public String clusterName;
        public String description;
        public List<String> memberIds;
        public Map<String, Integer> modalityDistribution;
        public List<String> commonKeywords;
        public double cohesion; // 凝聚度 0-1
    }

    /**
     * 跨模态关联结果
     */
    public static class CrossModalLink {
        public String sourceId;
        public String targetId;
        public String sourceModality;
        public String targetModality;
        public String linkType; // SIMILAR, COMPLEMENTARY, CAUSAL, TEMPORAL
        public double confidence;
        public String reason;
    }

    /**
     * 自动发现跨模态关联关系
     * 分析不同模态知识条目之间的语义关联
     */
    public List<CrossModalLink> discoverCrossModalLinks() {
        List<CrossModalLink> links = new ArrayList<>();
        List<MultimodalKnowledge> allKnowledge = knowledgeMapper.selectList(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getStatus, 1)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
        );

        if (allKnowledge.size() < 2) return links;

        // 按模态分组
        Map<String, List<MultimodalKnowledge>> byModality = allKnowledge.stream()
                .collect(Collectors.groupingBy(MultimodalKnowledge::getModality));

        // 跨模态比较
        List<String> modalities = new ArrayList<>(byModality.keySet());
        for (int i = 0; i < modalities.size(); i++) {
            for (int j = i + 1; j < modalities.size(); j++) {
                String modA = modalities.get(i);
                String modB = modalities.get(j);

                for (MultimodalKnowledge ka : byModality.get(modA)) {
                    for (MultimodalKnowledge kb : byModality.get(modB)) {
                        CrossModalLink link = analyzePairwiseLink(ka, kb);
                        if (link != null && link.confidence > 0.5) {
                            links.add(link);
                        }
                    }
                }
            }
        }

        // 按置信度排序
        links.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        log.info("发现 {} 条跨模态关联关系", links.size());
        return links;
    }

    /**
     * 自动聚类 - 将所有多模态知识条目按语义聚为多个簇
     */
    public List<KnowledgeCluster> autoCluster(int numClusters) {
        List<MultimodalKnowledge> allKnowledge = knowledgeMapper.selectList(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getStatus, 1)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
        );

        if (allKnowledge.isEmpty()) return Collections.emptyList();

        // 提取所有关键词
        Map<String, Set<String>> knowledgeKeywords = new HashMap<>();
        Set<String> allKeywords = new LinkedHashSet<>();

        for (MultimodalKnowledge mk : allKnowledge) {
            Set<String> keywords = extractKeywordSet(mk);
            knowledgeKeywords.put(mk.getKnowledgeId(), keywords);
            allKeywords.addAll(keywords);
        }

        // 使用TF-IDF启发式聚类
        List<KnowledgeCluster> clusters = new ArrayList<>();
        List<String> keywordList = new ArrayList<>(allKeywords);

        // 选择top关键词作为初始聚类中心
        Map<String, Integer> keywordFreq = new HashMap<>();
        for (Set<String> kws : knowledgeKeywords.values()) {
            for (String kw : kws) {
                keywordFreq.merge(kw, 1, Integer::sum);
            }
        }

        List<String> topKeywords = keywordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(numClusters)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 为每个聚类中心分配知识条目
        for (int i = 0; i < topKeywords.size(); i++) {
            String centerKeyword = topKeywords.get(i);
            KnowledgeCluster cluster = new KnowledgeCluster();
            cluster.clusterId = "cluster_" + (i + 1);
            cluster.clusterName = centerKeyword;
            cluster.description = "围绕关键词 '" + centerKeyword + "' 的知识簇";
            cluster.memberIds = new ArrayList<>();
            cluster.modalityDistribution = new HashMap<>();
            cluster.commonKeywords = new ArrayList<>();

            for (MultimodalKnowledge mk : allKnowledge) {
                Set<String> kws = knowledgeKeywords.get(mk.getKnowledgeId());
                if (kws.contains(centerKeyword)) {
                    cluster.memberIds.add(mk.getKnowledgeId());
                    cluster.modalityDistribution.merge(mk.getModality(), 1, Integer::sum);
                }
            }

            if (!cluster.memberIds.isEmpty()) {
                cluster.cohesion = calculateClusterCohesion(cluster, knowledgeKeywords);
                cluster.commonKeywords = findCommonKeywords(cluster, knowledgeKeywords);
                clusters.add(cluster);
            }
        }

        // 未分配的知识条目归入最近的聚类
        Set<String> assigned = new HashSet<>();
        for (KnowledgeCluster c : clusters) {
            assigned.addAll(c.memberIds);
        }

        for (MultimodalKnowledge mk : allKnowledge) {
            if (!assigned.contains(mk.getKnowledgeId())) {
                // 分配到关键词最相似的聚类
                KnowledgeCluster nearest = findNearestCluster(mk, clusters, knowledgeKeywords);
                if (nearest != null) {
                    nearest.memberIds.add(mk.getKnowledgeId());
                    nearest.modalityDistribution.merge(mk.getModality(), 1, Integer::sum);
                }
            }
        }

        log.info("自动聚类完成: {} 个簇, 覆盖 {} 条知识", clusters.size(),
                clusters.stream().mapToInt(c -> c.memberIds.size()).sum());
        return clusters;
    }

    /**
     * 基于图谱的关联发现
     * 使用知识图谱中的实体关系推断多模态知识关联
     */
    public List<CrossModalLink> discoverGraphBasedLinks() {
        List<CrossModalLink> links = new ArrayList<>();

        // 获取所有图谱节点
        List<KnowledgeGraphNode> nodes = graphService.getAllNodes();

        for (KnowledgeGraphNode node : nodes) {
            // 获取节点的所有关系
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
            for (KnowledgeGraphRelation rel : relations) {
                String otherNodeId = rel.getSourceNodeId().equals(node.getNodeId())
                        ? rel.getTargetNodeId() : rel.getSourceNodeId();

                // 查找与这些节点关联的多模态知识
                List<MultimodalKnowledge> sourceKnowledge = findKnowledgeByEntity(node.getName());
                List<MultimodalKnowledge> targetKnowledge = findKnowledgeByEntity(
                        graphService.findByName(rel.getTargetNodeName()) != null
                                ? rel.getTargetNodeName() : rel.getSourceNodeName());

                for (MultimodalKnowledge sk : sourceKnowledge) {
                    for (MultimodalKnowledge tk : targetKnowledge) {
                        if (!sk.getKnowledgeId().equals(tk.getKnowledgeId())) {
                            CrossModalLink link = new CrossModalLink();
                            link.sourceId = sk.getKnowledgeId();
                            link.targetId = tk.getKnowledgeId();
                            link.sourceModality = sk.getModality();
                            link.targetModality = tk.getModality();
                            link.linkType = rel.getRelationType();
                            link.confidence = rel.getConfidence() != null ? rel.getConfidence() : 0.7;
                            link.reason = "图谱关系: " + node.getName() + " -[" + rel.getRelationType() + "]-> " + otherNodeId;
                            links.add(link);
                        }
                    }
                }
            }
        }

        log.info("基于图谱发现 {} 条关联关系", links.size());
        return links;
    }

    /**
     * 知识簇质量评估
     */
    public Map<String, Object> evaluateClusters(List<KnowledgeCluster> clusters) {
        Map<String, Object> evaluation = new HashMap<>();

        double avgCohesion = clusters.stream().mapToDouble(c -> c.cohesion).average().orElse(0);
        double avgSize = clusters.stream().mapToInt(c -> c.memberIds.size()).average().orElse(0);
        long emptyClusters = clusters.stream().filter(c -> c.memberIds.isEmpty()).count();

        evaluation.put("totalClusters", clusters.size());
        evaluation.put("averageCohesion", avgCohesion);
        evaluation.put("averageSize", avgSize);
        evaluation.put("emptyClusters", emptyClusters);
        evaluation.put("quality", avgCohesion > 0.5 ? "GOOD" : avgCohesion > 0.3 ? "FAIR" : "POOR");

        return evaluation;
    }

    // ========== 辅助方法 ==========

    private CrossModalLink analyzePairwiseLink(MultimodalKnowledge a, MultimodalKnowledge b) {
        // 基于关键词、标签、描述的相似度分析
        Set<String> keywordsA = extractKeywordSet(a);
        Set<String> keywordsB = extractKeywordSet(b);

        // Jaccard相似度
        Set<String> intersection = new HashSet<>(keywordsA);
        intersection.retainAll(keywordsB);
        Set<String> union = new HashSet<>(keywordsA);
        union.addAll(keywordsB);

        double similarity = union.isEmpty() ? 0 : (double) intersection.size() / union.size();

        if (similarity > 0.2) {
            CrossModalLink link = new CrossModalLink();
            link.sourceId = a.getKnowledgeId();
            link.targetId = b.getKnowledgeId();
            link.sourceModality = a.getModality();
            link.targetModality = b.getModality();
            link.confidence = similarity;

            if (similarity > 0.7) link.linkType = "SIMILAR";
            else if (similarity > 0.4) link.linkType = "COMPLEMENTARY";
            else link.linkType = "TEMPORAL";

            link.reason = "关键词相似度: " + String.format("%.2f", similarity) +
                    " (" + String.join(",", intersection) + ")";
            return link;
        }
        return null;
    }

    private Set<String> extractKeywordSet(MultimodalKnowledge mk) {
        Set<String> keywords = new LinkedHashSet<>();
        if (mk.getKeywords() != null) {
            for (String kw : mk.getKeywords().split(",")) {
                String trimmed = kw.trim().toLowerCase();
                if (!trimmed.isEmpty()) keywords.add(trimmed);
            }
        }
        if (mk.getTags() != null) {
            for (String tag : mk.getTags().split(",")) {
                String trimmed = tag.trim().toLowerCase();
                if (!trimmed.isEmpty()) keywords.add(trimmed);
            }
        }
        return keywords;
    }

    private double calculateClusterCohesion(KnowledgeCluster cluster,
                                             Map<String, Set<String>> knowledgeKeywords) {
        if (cluster.memberIds.size() <= 1) return 1.0;

        double totalSimilarity = 0;
        int pairs = 0;

        for (int i = 0; i < cluster.memberIds.size(); i++) {
            for (int j = i + 1; j < cluster.memberIds.size(); j++) {
                Set<String> kwsA = knowledgeKeywords.get(cluster.memberIds.get(i));
                Set<String> kwsB = knowledgeKeywords.get(cluster.memberIds.get(j));
                if (kwsA != null && kwsB != null) {
                    Set<String> inter = new HashSet<>(kwsA);
                    inter.retainAll(kwsB);
                    Set<String> union = new HashSet<>(kwsA);
                    union.addAll(kwsB);
                    totalSimilarity += union.isEmpty() ? 0 : (double) inter.size() / union.size();
                    pairs++;
                }
            }
        }

        return pairs > 0 ? totalSimilarity / pairs : 0;
    }

    private List<String> findCommonKeywords(KnowledgeCluster cluster,
                                             Map<String, Set<String>> knowledgeKeywords) {
        Map<String, Integer> freq = new HashMap<>();
        for (String id : cluster.memberIds) {
            Set<String> kws = knowledgeKeywords.get(id);
            if (kws != null) {
                for (String kw : kws) {
                    freq.merge(kw, 1, Integer::sum);
                }
            }
        }

        int threshold = Math.max(2, cluster.memberIds.size() / 3);
        return freq.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private KnowledgeCluster findNearestCluster(MultimodalKnowledge mk,
                                                  List<KnowledgeCluster> clusters,
                                                  Map<String, Set<String>> knowledgeKeywords) {
        Set<String> mkKeywords = knowledgeKeywords.get(mk.getKnowledgeId());
        if (mkKeywords == null || mkKeywords.isEmpty()) return clusters.isEmpty() ? null : clusters.get(0);

        KnowledgeCluster best = null;
        double bestScore = 0;

        for (KnowledgeCluster cluster : clusters) {
            double score = 0;
            for (String id : cluster.memberIds) {
                Set<String> kws = knowledgeKeywords.get(id);
                if (kws != null) {
                    Set<String> inter = new HashSet<>(mkKeywords);
                    inter.retainAll(kws);
                    score += inter.size();
                }
            }
            score = cluster.memberIds.isEmpty() ? 0 : score / cluster.memberIds.size();

            if (score > bestScore) {
                bestScore = score;
                best = cluster;
            }
        }

        return best;
    }

    private List<MultimodalKnowledge> findKnowledgeByEntity(String entityName) {
        if (entityName == null) return Collections.emptyList();
        return knowledgeMapper.selectList(
                new LambdaQueryWrapper<MultimodalKnowledge>()
                        .eq(MultimodalKnowledge::getStatus, 1)
                        .eq(MultimodalKnowledge::getDelFlag, 0)
                        .and(w -> w.like(MultimodalKnowledge::getKeywords, entityName)
                                .or().like(MultimodalKnowledge::getDescription, entityName)
                                .or().like(MultimodalKnowledge::getEntities, entityName))
        );
    }
}
