package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.ai.cs.knowledge.entity.KnowledgeGraphRelation;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多语言知识图谱融合服务
 * 支持跨语言知识对齐、多语言实体链接、翻译桥接
 * 
 * 增强功能：多语言知识图谱融合
 */
@Slf4j
@Service
public class MultilingualGraphFusionService {

    private final KnowledgeGraphService graphService;

    // 语言代码常量
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";
    public static final String LANG_JA = "ja";
    public static final String LANG_KO = "ko";

    // 常见跨语言同义实体映射（示例数据，实际应接入翻译API/词典）
    private static final Map<String, Map<String, String>> MULTILINGUAL_SYNONYMS = new HashMap<>();

    static {
        // 技术术语
        Map<String, String> aiTerms = new HashMap<>();
        aiTerms.put(LANG_ZH, "人工智能");
        aiTerms.put(LANG_EN, "Artificial Intelligence");
        aiTerms.put(LANG_JA, "人工知能");
        aiTerms.put(LANG_KO, "인공지능");
        MULTILINGUAL_SYNONYMS.put("AI", aiTerms);

        Map<String, String> mlTerms = new HashMap<>();
        mlTerms.put(LANG_ZH, "机器学习");
        mlTerms.put(LANG_EN, "Machine Learning");
        mlTerms.put(LANG_JA, "機械学習");
        mlTerms.put(LANG_KO, "머신러닝");
        MULTILINGUAL_SYNONYMS.put("ML", mlTerms);

        Map<String, String> dlTerms = new HashMap<>();
        dlTerms.put(LANG_ZH, "深度学习");
        dlTerms.put(LANG_EN, "Deep Learning");
        dlTerms.put(LANG_JA, "深層学習");
        dlTerms.put(LANG_KO, "딥러닝");
        MULTILINGUAL_SYNONYMS.put("DL", dlTerms);

        Map<String, String> nlpTerms = new HashMap<>();
        nlpTerms.put(LANG_ZH, "自然语言处理");
        nlpTerms.put(LANG_EN, "Natural Language Processing");
        nlpTerms.put(LANG_JA, "自然言語処理");
        nlpTerms.put(LANG_KO, "자연어처리");
        MULTILINGUAL_SYNONYMS.put("NLP", nlpTerms);
    }

    public MultilingualGraphFusionService(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    // ========== 多语言实体链接 ==========

    /**
     * 查找多语言等价实体
     * @param entityName 实体名称
     * @param sourceLang 源语言
     * @return 其他语言中的等价实体
     */
    public Map<String, Object> findMultilingualEquivalents(String entityName, String sourceLang) {
        Map<String, Object> result = new HashMap<>();
        result.put("sourceName", entityName);
        result.put("sourceLang", sourceLang);
        
        Map<String, List<String>> equivalents = new HashMap<>();

        // 1. 从内置同义词库查找
        for (Map.Entry<String, Map<String, String>> entry : MULTILINGUAL_SYNONYMS.entrySet()) {
            Map<String, String> terms = entry.getValue();
            for (Map.Entry<String, String> term : terms.entrySet()) {
                if (term.getValue().equalsIgnoreCase(entityName) && !term.getKey().equals(sourceLang)) {
                    equivalents.computeIfAbsent(term.getKey(), k -> new ArrayList<>()).add(term.getValue());
                }
            }
        }

        // 2. 从图谱中查找已有翻译关系
        KnowledgeGraphNode node = graphService.findByName(entityName);
        if (node != null) {
            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(node.getNodeId());
            for (KnowledgeGraphRelation rel : relations) {
                if ("等同于".equals(rel.getRelationType()) || "翻译".equals(rel.getRelationType())) {
                    String otherName = rel.getSourceNodeId().equals(node.getNodeId())
                            ? rel.getTargetNodeName() : rel.getSourceNodeName();
                    // 尝试检测语言
                    String detectedLang = detectLanguage(otherName);
                    equivalents.computeIfAbsent(detectedLang, k -> new ArrayList<>()).add(otherName);
                }
            }
        }

        // 3. 从图谱中模糊搜索
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        for (KnowledgeGraphNode n : allNodes) {
            if (n.getName().equalsIgnoreCase(entityName)) continue;
            // 检查是否有别名包含
            if (n.getProperties() != null) {
                try {
                    JSONObject props = JSON.parseObject(n.getProperties());
                    JSONArray aliases = props.getJSONArray("aliases");
                    if (aliases != null && aliases.contains(entityName)) {
                        String lang = detectLanguage(n.getName());
                        equivalents.computeIfAbsent(lang, k -> new ArrayList<>()).add(n.getName());
                    }
                } catch (Exception ignored) {}
            }
        }

        result.put("equivalents", equivalents);
        result.put("totalEquivalents", equivalents.values().stream().mapToInt(List::size).sum());
        return result;
    }

    /**
     * 创建跨语言实体链接
     * @param entityName1 实体1名称
     * @param lang1 实体1语言
     * @param entityName2 实体2名称
     * @param lang2 实体2语言
     */
    public void createCrossLingualLink(String entityName1, String lang1, String entityName2, String lang2) {
        KnowledgeGraphNode node1 = graphService.findByName(entityName1);
        KnowledgeGraphNode node2 = graphService.findByName(entityName2);

        if (node1 == null) {
            node1 = new KnowledgeGraphNode();
            node1.setNodeId(UUID.randomUUID().toString());
            node1.setName(entityName1);
            node1.setLabel("概念");
            JSONObject props = new JSONObject();
            props.put("language", lang1);
            node1.setProperties(props.toJSONString());
            node1.setStatus(1);
            graphService.saveNode(node1);
        }

        if (node2 == null) {
            node2 = new KnowledgeGraphNode();
            node2.setNodeId(UUID.randomUUID().toString());
            node2.setName(entityName2);
            node2.setLabel("概念");
            JSONObject props = new JSONObject();
            props.put("language", lang2);
            node2.setProperties(props.toJSONString());
            node2.setStatus(1);
            graphService.saveNode(node2);
        }

        // 创建跨语言等同关系
        KnowledgeGraphRelation relation = new KnowledgeGraphRelation();
        relation.setRelationId(UUID.randomUUID().toString());
        relation.setSourceNodeId(node1.getNodeId());
        relation.setTargetNodeId(node2.getNodeId());
        relation.setSourceNodeName(node1.getName());
        relation.setTargetNodeName(node2.getName());
        relation.setRelationType("翻译");
        relation.setDescription(String.format("跨语言实体链接: %s(%s) <-> %s(%s)", entityName1, lang1, entityName2, lang2));
        relation.setWeight(80);
        relation.setConfidence(0.9);

        JSONObject props = new JSONObject();
        props.put("crossLingual", true);
        props.put("sourceLang", lang1);
        props.put("targetLang", lang2);
        relation.setProperties(props.toJSONString());
        relation.setStatus(1);

        graphService.saveRelation(relation);
        log.info("创建跨语言链接: {} ({}) <-> {} ({})", entityName1, lang1, entityName2, lang2);
    }

    // ========== 多语言图谱融合 ==========

    /**
     * 融合两个语言的知识图谱
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 融合统计
     */
    public Map<String, Object> fuseLanguageGraphs(String sourceLang, String targetLang) {
        Map<String, Object> stats = new HashMap<>();
        int linkedEntities = 0;
        int newRelations = 0;

        // 获取所有源语言和目标语言节点
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        
        List<KnowledgeGraphNode> sourceNodes = allNodes.stream()
                .filter(n -> getNodeLanguage(n).equals(sourceLang))
                .collect(Collectors.toList());
        
        List<KnowledgeGraphNode> targetNodes = allNodes.stream()
                .filter(n -> getNodeLanguage(n).equals(targetLang))
                .collect(Collectors.toList());

        // 尝试匹配同义实体
        for (KnowledgeGraphNode sourceNode : sourceNodes) {
            for (Map.Entry<String, Map<String, String>> synonymEntry : MULTILINGUAL_SYNONYMS.entrySet()) {
                Map<String, String> terms = synonymEntry.getValue();
                String sourceTerm = terms.get(sourceLang);
                String targetTerm = terms.get(targetLang);

                if (sourceTerm != null && targetTerm != null) {
                    if (sourceNode.getName().toLowerCase().contains(sourceTerm.toLowerCase())) {
                        for (KnowledgeGraphNode targetNode : targetNodes) {
                            if (targetNode.getName().toLowerCase().contains(targetTerm.toLowerCase())) {
                                // 找到匹配，创建链接
                                try {
                                    createCrossLingualLink(sourceNode.getName(), sourceLang, 
                                            targetNode.getName(), targetLang);
                                    linkedEntities++;
                                } catch (Exception e) {
                                    log.warn("创建跨语言链接失败: {} -> {}", sourceNode.getName(), targetNode.getName());
                                }
                            }
                        }
                    }
                }
            }
        }

        // 迁移关系：如果源语言实体有翻译关系，为目标语言实体复制相似关系
        for (KnowledgeGraphNode sourceNode : sourceNodes) {
            List<KnowledgeGraphRelation> sourceRelations = graphService.getNodeRelations(sourceNode.getNodeId());
            for (KnowledgeGraphRelation rel : sourceRelations) {
                if ("翻译".equals(rel.getRelationType())) {
                    KnowledgeGraphNode targetEquivalent = graphService.findById(
                            rel.getTargetNodeId().equals(sourceNode.getNodeId()) 
                                    ? rel.getSourceNodeId() : rel.getTargetNodeId()
                    );
                    
                    if (targetEquivalent != null && getNodeLanguage(targetEquivalent).equals(targetLang)) {
                        // 复制关系（排除翻译关系本身）
                        for (KnowledgeGraphRelation sr : sourceRelations) {
                            if (!"翻译".equals(sr.getRelationType()) && !sr.getRelationId().equals(rel.getRelationId())) {
                                try {
                                    KnowledgeGraphRelation newRel = new KnowledgeGraphRelation();
                                    newRel.setRelationId(UUID.randomUUID().toString());
                                    newRel.setSourceNodeId(targetEquivalent.getNodeId());
                                    newRel.setSourceNodeName(targetEquivalent.getName());
                                    newRel.setTargetNodeId(sr.getTargetNodeId());
                                    newRel.setTargetNodeName(sr.getTargetNodeName());
                                    newRel.setRelationType(sr.getRelationType());
                                    newRel.setDescription("[多语言融合] " + sr.getDescription());
                                    newRel.setWeight((int)(sr.getWeight() * 0.7));
                                    newRel.setConfidence(sr.getConfidence() * 0.8);
                                    
                                    JSONObject props = new JSONObject();
                                    props.put("fused", true);
                                    props.put("sourceLang", sourceLang);
                                    props.put("targetLang", targetLang);
                                    newRel.setProperties(props.toJSONString());
                                    newRel.setStatus(1);
                                    
                                    graphService.saveRelation(newRel);
                                    newRelations++;
                                } catch (Exception e) {
                                    log.debug("复制关系失败: {}", e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }

        stats.put("linkedEntities", linkedEntities);
        stats.put("newRelations", newRelations);
        stats.put("sourceNodeCount", sourceNodes.size());
        stats.put("targetNodeCount", targetNodes.size());
        return stats;
    }

    // ========== 翻译桥接 ==========

    /**
     * 通过翻译桥接查找跨语言路径
     * @param entityName 起始实体
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param maxDepth 最大跳数
     * @return 跨语言关联实体
     */
    public List<Map<String, Object>> findCrossLingualRelations(String entityName, String sourceLang, 
                                                                String targetLang, int maxDepth) {
        List<Map<String, Object>> results = new ArrayList<>();
        KnowledgeGraphNode startNode = graphService.findByName(entityName);

        if (startNode == null) {
            return results;
        }

        // BFS搜索跨语言关系
        Set<String> visited = new HashSet<>();
        Queue<SearchState> queue = new LinkedList<>();
        queue.add(new SearchState(startNode.getNodeId(), startNode.getName(), 0, new ArrayList<>()));

        while (!queue.isEmpty() && results.size() < 50) {
            SearchState current = queue.poll();
            if (visited.contains(current.nodeId) || current.depth > maxDepth) continue;
            visited.add(current.nodeId);

            KnowledgeGraphNode currentNode = graphService.findById(current.nodeId);
            if (currentNode == null) continue;

            String currentLang = getNodeLanguage(currentNode);
            if (currentLang.equals(targetLang) && current.depth > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("entity", currentNode.getName());
                result.put("language", targetLang);
                result.put("pathLength", current.depth);
                result.put("path", current.path);
                results.add(result);
            }

            List<KnowledgeGraphRelation> relations = graphService.getNodeRelations(current.nodeId);
            for (KnowledgeGraphRelation rel : relations) {
                String nextId = rel.getSourceNodeId().equals(current.nodeId) 
                        ? rel.getTargetNodeId() : rel.getSourceNodeId();
                if (!visited.contains(nextId)) {
                    List<String> newPath = new ArrayList<>(current.path);
                    newPath.add(rel.getRelationType() + ": " + rel.getDescription());
                    queue.add(new SearchState(nextId, "", current.depth + 1, newPath));
                }
            }
        }

        return results;
    }

    // ========== 语言检测 ==========

    /**
     * 检测图谱中实体的语言分布
     * @return 语言分布统计
     */
    public Map<String, Object> detectLanguageDistribution() {
        List<KnowledgeGraphNode> allNodes = graphService.getAllNodes();
        Map<String, Long> distribution = new HashMap<>();
        Map<String, List<String>> samples = new HashMap<>();

        for (KnowledgeGraphNode node : allNodes) {
            String lang = getNodeLanguage(node);
            distribution.merge(lang, 1L, Long::sum);
            samples.computeIfAbsent(lang, k -> new ArrayList<>())
                    .add(node.getName());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("distribution", distribution);
        result.put("totalLanguages", distribution.size());

        // 每种语言取前5个样本
        Map<String, List<String>> samplePreview = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : samples.entrySet()) {
            samplePreview.put(entry.getKey(), 
                    entry.getValue().stream().limit(5).collect(Collectors.toList()));
        }
        result.put("samples", samplePreview);

        return result;
    }

    /**
     * 为实体添加语言标签
     * @param nodeId 节点ID
     * @param language 语言代码
     */
    public void tagNodeLanguage(String nodeId, String language) {
        KnowledgeGraphNode node = graphService.findById(nodeId);
        if (node != null) {
            JSONObject props;
            if (node.getProperties() != null) {
                props = JSON.parseObject(node.getProperties());
            } else {
                props = new JSONObject();
            }
            props.put("language", language);
            node.setProperties(props.toJSONString());
            graphService.saveNode(node);
        }
    }

    /**
     * 为实体添加多语言别名
     * @param nodeId 节点ID
     * @param aliases 别名映射 (语言 -> 名称)
     */
    public void addMultilingualAliases(String nodeId, Map<String, String> aliases) {
        KnowledgeGraphNode node = graphService.findById(nodeId);
        if (node != null) {
            JSONObject props;
            if (node.getProperties() != null) {
                props = JSON.parseObject(node.getProperties());
            } else {
                props = new JSONObject();
            }

            JSONArray existingAliases = props.getJSONArray("multilingualAliases");
            if (existingAliases == null) {
                existingAliases = new JSONArray();
            }

            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                JSONObject alias = new JSONObject();
                alias.put("language", entry.getKey());
                alias.put("name", entry.getValue());
                existingAliases.add(alias);
            }

            props.put("multilingualAliases", existingAliases);
            node.setProperties(props.toJSONString());
            graphService.saveNode(node);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 获取节点的语言
     */
    private String getNodeLanguage(KnowledgeGraphNode node) {
        if (node.getProperties() != null) {
            try {
                JSONObject props = JSON.parseObject(node.getProperties());
                String lang = props.getString("language");
                if (lang != null) return lang;
            } catch (Exception ignored) {}
        }
        return detectLanguage(node.getName());
    }

    /**
     * 检测文本语言（简单启发式）
     */
    private String detectLanguage(String text) {
        if (text == null || text.isEmpty()) return "unknown";

        boolean hasCJK = false;
        boolean hasHangul = false;
        boolean hasHiragana = false;
        boolean hasLatin = false;

        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') hasCJK = true;
            else if (c >= '\uac00' && c <= '\ud7af') hasHangul = true;
            else if (c >= '\u3040' && c <= '\u309f' || c >= '\u30a0' && c <= '\u30ff') hasHiragana = true;
            else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') hasLatin = true;
        }

        if (hasHiragana) return LANG_JA;
        if (hasHangul) return LANG_KO;
        if (hasCJK && !hasLatin) return LANG_ZH;
        if (hasLatin) return LANG_EN;
        return "unknown";
    }

    /**
     * BFS搜索状态
     */
    private static class SearchState {
        final String nodeId;
        final String nodeName;
        final int depth;
        final List<String> path;

        SearchState(String nodeId, String nodeName, int depth, List<String> path) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.depth = depth;
            this.path = path;
        }
    }
}
