package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * 3D 模型知识库服务
 * 支持 3D 模型（OBJ/STL/GLTF/GLB/FBX）的知识化存储、检索和分析
 * 
 * 增强功能：3D 模型知识库支持
 */
@Slf4j
@Service
public class Model3DKnowledgeService {

    private final MultimodalKnowledgeService multimodalKnowledgeService;
    private final VisionLLMClient visionLLMClient;

    public Model3DKnowledgeService(MultimodalKnowledgeService multimodalKnowledgeService,
                                    VisionLLMClient visionLLMClient) {
        this.multimodalKnowledgeService = multimodalKnowledgeService;
        this.visionLLMClient = visionLLMClient;
    }

    // ========== 3D 模型格式常量 ==========
    public static final String FORMAT_OBJ = "OBJ";
    public static final String FORMAT_STL = "STL";
    public static final String FORMAT_GLTF = "GLTF";
    public static final String FORMAT_GLB = "GLB";
    public static final String FORMAT_FBX = "FBX";
    public static final String FORMAT_USDZ = "USDZ";
    public static final String FORMAT_PLY = "PLY";
    public static final String MODALITY_3D = "3D_MODEL";

    private static final Set<String> SUPPORTED_FORMATS = Set.of(
            "obj", "stl", "gltf", "glb", "fbx", "usdz", "ply"
    );

    // ========== 模型入库 ==========

    /**
     * 导入 3D 模型到知识库
     * @param modelPath 模型文件路径
     * @param modelName 模型名称
     * @param description 模型描述
     * @param tags 标签列表
     * @return 创建的多模态知识条目
     */
    public MultimodalKnowledge import3DModel(String modelPath, String modelName,
                                              String description, List<String> tags) {
        log.info("导入3D模型: {}, 名称: {}", modelPath, modelName);

        // 1. 校验文件格式
        String format = getModelFormat(modelPath);
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new IllegalArgumentException("不支持的3D模型格式: " + format 
                    + "，支持的格式: " + String.join(", ", SUPPORTED_FORMATS));
        }

        // 2. 解析模型基本信息
        Map<String, Object> modelInfo = parseModelInfo(modelPath, format);
        
        // 3. 生成缩略图描述（通过多视角截图让视觉模型理解）
        String thumbnailDescription = "";
        try {
            thumbnailDescription = generateThumbnailDescription(modelPath, modelName);
        } catch (Exception e) {
            log.warn("生成3D模型缩略图描述失败: {}", e.getMessage());
            thumbnailDescription = "3D模型: " + modelName;
        }

        // 4. 使用LLM生成模型的结构化描述
        String analysis = buildModelAnalysis(modelName, description, modelInfo, thumbnailDescription, format, tags);

        // 5. 创建多模态知识条目
        MultimodalKnowledge knowledge = new MultimodalKnowledge();
        knowledge.setKnowledgeId(UUID.randomUUID().toString());
        knowledge.setModality(MODALITY_3D);
        knowledge.setTitle(modelName);
        knowledge.setResourcePath(modelPath);
        knowledge.setDescription(thumbnailDescription);
        knowledge.setAnalysis(analysis);
        knowledge.setKeywords(extractKeywords(modelInfo, tags));
        knowledge.setEntities(JSON.toJSONString(extractEntities(modelInfo)));
        knowledge.setTags(String.join(",", tags != null ? tags : List.of()));
        knowledge.setConfidence(0.85);
        knowledge.setStatus(1);

        multimodalKnowledgeService.save(knowledge);
        log.info("3D模型入库成功: knowledgeId={}", knowledge.getKnowledgeId());
        return knowledge;
    }

    /**
     * 批量导入 3D 模型
     * @param modelInfos 模型信息列表（path, name, description, tags）
     * @return 导入统计
     */
    public Map<String, Object> batchImport3DModels(List<Map<String, Object>> modelInfos) {
        int success = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> info : modelInfos) {
            try {
                String path = (String) info.get("path");
                String name = (String) info.get("name");
                String desc = (String) info.getOrDefault("description", "");
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) info.getOrDefault("tags", Collections.emptyList());
                
                import3DModel(path, name, desc, tags);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add(info.get("name") + ": " + e.getMessage());
                log.error("导入3D模型失败: {}", info.get("name"), e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", modelInfos.size());
        result.put("success", success);
        result.put("fail", fail);
        result.put("errors", errors);
        return result;
    }

    // ========== 模型检索 ==========

    /**
     * 根据关键词搜索 3D 模型知识
     * @param keyword 搜索关键词
     * @return 匹配的3D模型知识列表
     */
    public List<MultimodalKnowledge> search3DModels(String keyword) {
        List<MultimodalKnowledge> all3DModels = multimodalKnowledgeService.listByModality(MODALITY_3D);
        if (all3DModels == null || all3DModels.isEmpty()) {
            return Collections.emptyList();
        }

        List<MultimodalKnowledge> results = new ArrayList<>();
        for (MultimodalKnowledge model : all3DModels) {
            double score = calculateMatchScore(model, keyword);
            if (score > 0.1) {
                results.add(model);
            }
        }

        results.sort((a, b) -> {
            double sa = calculateMatchScore(a, keyword);
            double sb = calculateMatchScore(b, keyword);
            return Double.compare(sb, sa);
        });

        return results;
    }

    /**
     * 获取所有 3D 模型知识
     * @return 3D模型知识列表
     */
    public List<MultimodalKnowledge> getAll3DModels() {
        return multimodalKnowledgeService.listByModality(MODALITY_3D);
    }

    /**
     * 按标签筛选 3D 模型
     * @param tag 标签
     * @return 筛选结果
     */
    public List<MultimodalKnowledge> get3DModelsByTag(String tag) {
        List<MultimodalKnowledge> all = getAll3DModels();
        if (all == null) return Collections.emptyList();
        
        return all.stream()
                .filter(m -> m.getTags() != null && Arrays.asList(m.getTags().split(",")).contains(tag))
                .toList();
    }

    /**
     * 获取相似 3D 模型
     * @param knowledgeId 源模型ID
     * @param limit 返回数量
     * @return 相似模型列表
     */
    public List<MultimodalKnowledge> getSimilar3DModels(String knowledgeId, int limit) {
        MultimodalKnowledge source = multimodalKnowledgeService.getById(knowledgeId);
        if (source == null) {
            return Collections.emptyList();
        }

        List<MultimodalKnowledge> allModels = getAll3DModels();
        if (allModels == null) allModels = Collections.emptyList();

        // 基于标签相似度计算
        Set<String> sourceTags = source.getTags() != null 
                ? new HashSet<>(Arrays.asList(source.getTags().split(","))) 
                : Collections.emptySet();

        Map<MultimodalKnowledge, Integer> similarityMap = new HashMap<>();
        for (MultimodalKnowledge model : allModels) {
            if (model.getKnowledgeId().equals(knowledgeId)) continue;
            
            Set<String> modelTags = model.getTags() != null 
                    ? new HashSet<>(Arrays.asList(model.getTags().split(","))) 
                    : Collections.emptySet();
            
            Set<String> intersection = new HashSet<>(sourceTags);
            intersection.retainAll(modelTags);
            int similarity = intersection.size();
            
            if (similarity > 0) {
                similarityMap.put(model, similarity);
            }
        }

        return similarityMap.entrySet().stream()
                .sorted(Map.Entry.<MultimodalKnowledge, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ========== 模型分析 ==========

    /**
     * 获取 3D 模型详细分析报告
     * @param knowledgeId 知识条目ID
     * @return 分析报告
     */
    public Map<String, Object> getModelAnalysisReport(String knowledgeId) {
        MultimodalKnowledge model = multimodalKnowledgeService.getById(knowledgeId);
        if (model == null) {
            throw new IllegalArgumentException("3D模型知识不存在: " + knowledgeId);
        }

        Map<String, Object> report = new HashMap<>();
        report.put("knowledgeId", model.getKnowledgeId());
        report.put("modelName", model.getTitle());
        report.put("format", getModelFormat(model.getResourcePath()));
        report.put("modality", model.getModality());
        report.put("description", model.getDescription());
        report.put("analysis", model.getAnalysis());
        report.put("tags", model.getTags());
        report.put("confidence", model.getConfidence());
        report.put("status", model.getStatus());
        report.put("createdAt", model.getCreateTime());

        // 解析结构信息
        if (model.getEntities() != null) {
            report.put("entities", JSON.parseArray(model.getEntities()));
        }
        if (model.getKeywords() != null) {
            report.put("keywords", model.getKeywords());
        }

        return report;
    }

    /**
     * 比较两个 3D 模型的差异
     * @param knowledgeId1 模型1 ID
     * @param knowledgeId2 模型2 ID
     * @return 差异分析结果
     */
    public Map<String, Object> compare3DModels(String knowledgeId1, String knowledgeId2) {
        MultimodalKnowledge model1 = multimodalKnowledgeService.getById(knowledgeId1);
        MultimodalKnowledge model2 = multimodalKnowledgeService.getById(knowledgeId2);

        if (model1 == null || model2 == null) {
            throw new IllegalArgumentException("3D模型不存在");
        }

        Map<String, Object> comparison = new HashMap<>();
        comparison.put("model1", Map.of("name", model1.getTitle(), "format", getModelFormat(model1.getResourcePath())));
        comparison.put("model2", Map.of("name", model2.getTitle(), "format", getModelFormat(model2.getResourcePath())));

        // 标签比较
        Set<String> tags1 = model1.getTags() != null 
                ? new HashSet<>(Arrays.asList(model1.getTags().split(","))) : Collections.emptySet();
        Set<String> tags2 = model2.getTags() != null 
                ? new HashSet<>(Arrays.asList(model2.getTags().split(","))) : Collections.emptySet();
        
        Set<String> commonTags = new HashSet<>(tags1);
        commonTags.retainAll(tags2);
        
        Set<String> onlyIn1 = new HashSet<>(tags1);
        onlyIn1.removeAll(tags2);
        
        Set<String> onlyIn2 = new HashSet<>(tags2);
        onlyIn2.removeAll(tags1);

        comparison.put("commonTags", commonTags);
        comparison.put("tagsOnlyInModel1", onlyIn1);
        comparison.put("tagsOnlyInModel2", onlyIn2);

        // 格式比较
        comparison.put("sameFormat", getModelFormat(model1.getResourcePath())
                .equals(getModelFormat(model2.getResourcePath())));

        return comparison;
    }

    // ========== 模型问答 ==========

    /**
     * 基于3D模型知识的问答
     * @param question 问题
     * @param knowledgeId 指定模型ID（可选，不传则搜索所有模型）
     * @return 回答
     */
    public String modelQa(String question, String knowledgeId) {
        StringBuilder context = new StringBuilder();
        context.append("以下是3D模型的知识信息：\n\n");

        if (knowledgeId != null && !knowledgeId.isEmpty()) {
            MultimodalKnowledge model = multimodalKnowledgeService.getById(knowledgeId);
            if (model != null) {
                appendModelContext(context, model);
            }
        } else {
            List<MultimodalKnowledge> relatedModels = search3DModels(question);
            for (MultimodalKnowledge model : relatedModels) {
                appendModelContext(context, model);
            }
        }

        context.append("\n请基于以上3D模型信息回答用户问题。\n问题: ").append(question);

        try {
            return visionLLMClient.multimodalChat(context.toString(), null, null);
        } catch (Exception e) {
            log.error("3D模型问答失败", e);
            return "3D模型问答出错: " + e.getMessage();
        }
    }

    // ========== 格式转换建议 ==========

    /**
     * 获取 3D 模型格式转换建议
     * @param knowledgeId 知识条目ID
     * @param targetFormat 目标格式
     * @return 转换建议
     */
    public Map<String, Object> getFormatConversionAdvice(String knowledgeId, String targetFormat) {
        MultimodalKnowledge model = multimodalKnowledgeService.getById(knowledgeId);
        if (model == null) {
            throw new IllegalArgumentException("3D模型不存在: " + knowledgeId);
        }

        String sourceFormat = getModelFormat(model.getResourcePath());
        Map<String, Object> advice = new HashMap<>();
        advice.put("sourceFormat", sourceFormat);
        advice.put("targetFormat", targetFormat.toUpperCase());
        advice.put("modelName", model.getTitle());

        // 格式兼容性建议
        boolean compatible = isFormatConversionCompatible(sourceFormat, targetFormat);
        advice.put("compatible", compatible);

        if (compatible) {
            advice.put("recommendation", "格式转换可行，建议使用标准转换工具");
            advice.put("tools", getConversionTools(sourceFormat, targetFormat));
        } else {
            advice.put("recommendation", "该格式转换可能丢失信息，建议保留原始格式");
            advice.put("risks", List.of("可能丢失材质信息", "可能丢失动画数据", "可能丢失骨骼信息"));
        }

        return advice;
    }

    // ========== 辅助方法 ==========

    /**
     * 获取模型文件格式
     */
    private String getModelFormat(String path) {
        if (path == null) return "UNKNOWN";
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0) return "UNKNOWN";
        return path.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 判断是否为支持的3D格式
     */
    public boolean isSupportedFormat(String fileName) {
        return SUPPORTED_FORMATS.contains(getModelFormat(fileName));
    }

    /**
     * 解析模型基本信息
     */
    private Map<String, Object> parseModelInfo(String modelPath, String format) {
        Map<String, Object> info = new HashMap<>();
        info.put("format", format.toUpperCase());
        info.put("path", modelPath);

        File file = new File(modelPath);
        if (file.exists()) {
            info.put("fileSize", file.length());
            info.put("fileName", file.getName());
        }

        // 根据格式推断模型特征
        switch (format) {
            case "gltf", "glb" -> {
                info.put("features", List.of("PBR材质", "骨骼动画", "节点层级"));
                info.put("standard", "glTF 2.0");
            }
            case "obj" -> {
                info.put("features", List.of("几何网格", "UV坐标", "法线"));
                info.put("standard", "Wavefront OBJ");
            }
            case "stl" -> {
                info.put("features", List.of("三角面片", "3D打印就绪"));
                info.put("standard", "STereoLithography");
            }
            case "fbx" -> {
                info.put("features", List.of("完整场景", "骨骼绑定", "动画轨道"));
                info.put("standard", "Autodesk FBX");
            }
            case "usdz" -> {
                info.put("features", List.of("AR就绪", "USD场景图"));
                info.put("standard", "Pixar USDZ");
            }
            default -> {
                info.put("features", Collections.emptyList());
                info.put("standard", "Unknown");
            }
        }

        return info;
    }

    /**
     * 生成缩略图描述（框架实现 - 实际使用需配合Three.js/渲染引擎）
     */
    private String generateThumbnailDescription(String modelPath, String modelName) throws Exception {
        // 框架实现：实际生产环境中使用 Three.js 或其他渲染引擎生成多视角截图
        // 然后用视觉大模型分析截图内容
        String prompt = String.format(
                "请根据以下3D模型信息，生成一段专业的模型描述。\n" +
                "模型名称: %s\n模型路径: %s\n格式: %s\n" +
                "请描述该模型可能的外观特征、几何复杂度、用途等。",
                modelName, modelPath, getModelFormat(modelPath)
        );

        return visionLLMClient.multimodalChat(prompt, null, null);
    }

    /**
     * 构建模型分析文本
     */
    private String buildModelAnalysis(String name, String description, Map<String, Object> modelInfo,
                                       String thumbnailDesc, String format, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("【3D模型分析报告】\n");
        sb.append("模型名称: ").append(name).append("\n");
        sb.append("文件格式: ").append(format.toUpperCase()).append("\n");
        sb.append("外观描述: ").append(thumbnailDesc).append("\n");

        if (description != null && !description.isEmpty()) {
            sb.append("用户描述: ").append(description).append("\n");
        }

        if (modelInfo.containsKey("fileSize")) {
            sb.append("文件大小: ").append(formatFileSize((Long) modelInfo.get("fileSize"))).append("\n");
        }

        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) modelInfo.get("features");
        if (features != null && !features.isEmpty()) {
            sb.append("格式特性: ").append(String.join(", ", features)).append("\n");
        }

        if (tags != null && !tags.isEmpty()) {
            sb.append("标签: ").append(String.join(", ", tags)).append("\n");
        }

        sb.append("技术标准: ").append(modelInfo.getOrDefault("standard", "未知")).append("\n");
        sb.append("适用场景: ").append(suggestUsageScenarios(format, tags)).append("\n");

        return sb.toString();
    }

    /**
     * 提取关键词
     */
    private String extractKeywords(Map<String, Object> modelInfo, List<String> tags) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add("3D模型");
        keywords.add(modelInfo.get("format").toString());

        if (tags != null) {
            keywords.addAll(tags);
        }

        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) modelInfo.get("features");
        if (features != null) {
            keywords.addAll(features);
        }

        return String.join(",", keywords);
    }

    /**
     * 提取实体
     */
    private List<Map<String, String>> extractEntities(Map<String, Object> modelInfo) {
        List<Map<String, String>> entities = new ArrayList<>();
        
        Map<String, String> entity = new HashMap<>();
        entity.put("name", modelInfo.get("format").toString());
        entity.put("type", "文件格式");
        entities.add(entity);

        return entities;
    }

    /**
     * 计算匹配分数
     */
    private double calculateMatchScore(MultimodalKnowledge model, String keyword) {
        double score = 0.0;
        String lowerKeyword = keyword.toLowerCase();

        if (model.getTitle() != null && model.getTitle().toLowerCase().contains(lowerKeyword)) {
            score += 0.5;
        }
        if (model.getDescription() != null && model.getDescription().toLowerCase().contains(lowerKeyword)) {
            score += 0.3;
        }
        if (model.getTags() != null && model.getTags().toLowerCase().contains(lowerKeyword)) {
            score += 0.2;
        }
        if (model.getKeywords() != null && model.getKeywords().toLowerCase().contains(lowerKeyword)) {
            score += 0.15;
        }
        if (model.getAnalysis() != null && model.getAnalysis().toLowerCase().contains(lowerKeyword)) {
            score += 0.1;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 添加模型上下文到StringBuilder
     */
    private void appendModelContext(StringBuilder context, MultimodalKnowledge model) {
        context.append("--- 模型: ").append(model.getTitle()).append(" ---\n");
        context.append("格式: ").append(getModelFormat(model.getResourcePath())).append("\n");
        if (model.getDescription() != null) {
            context.append("描述: ").append(model.getDescription()).append("\n");
        }
        if (model.getAnalysis() != null) {
            context.append("分析: ").append(model.getAnalysis()).append("\n");
        }
        if (model.getTags() != null) {
            context.append("标签: ").append(model.getTags()).append("\n");
        }
        context.append("\n");
    }

    /**
     * 判断格式转换兼容性
     */
    private boolean isFormatConversionCompatible(String source, String target) {
        source = source.toLowerCase();
        target = target.toLowerCase();
        
        // 同族格式可互转
        if (source.equals(target)) return true;
        if (Set.of("gltf", "glb").contains(source) && Set.of("gltf", "glb").contains(target)) return true;
        if (Set.of("obj", "stl", "ply").contains(source) && Set.of("obj", "stl", "ply").contains(target)) return true;
        
        return false;
    }

    /**
     * 获取格式转换工具推荐
     */
    private List<String> getConversionTools(String source, String target) {
        List<String> tools = new ArrayList<>();
        tools.add("Blender (开源)");
        
        if (Set.of("gltf", "glb").contains(target.toLowerCase())) {
            tools.add("gltf-transform CLI");
        }
        if (target.equalsIgnoreCase("usdz")) {
            tools.add("Apple Reality Converter");
        }
        if (target.equalsIgnoreCase("fbx")) {
            tools.add("Autodesk FBX Converter");
        }
        
        return tools;
    }

    /**
     * 建议使用场景
     */
    private String suggestUsageScenarios(String format, List<String> tags) {
        return switch (format.toLowerCase()) {
            case "gltf", "glb" -> "Web端3D展示、游戏引擎导入、AR/VR应用";
            case "obj" -> "3D建模交换、游戏开发、3D打印预处理";
            case "stl" -> "3D打印、快速原型制作";
            case "fbx" -> "游戏开发、影视动画、MotionBuilder工作流";
            case "usdz" -> "iOS AR应用、Apple设备3D预览";
            case "ply" -> "3D扫描数据存储、点云处理";
            default -> "通用3D应用";
        };
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
