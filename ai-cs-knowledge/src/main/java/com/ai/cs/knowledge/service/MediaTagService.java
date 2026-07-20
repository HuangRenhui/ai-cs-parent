package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多媒体智能标签服务
 * 使用AI自动识别图片/音频内容并打标签
 * 支持自定义标签体系和自动标签推荐
 */
@Slf4j
@Service
public class MediaTagService {

    /**
     * 标签存储（内存缓存，生产环境应使用数据库）
     * key: fileId, value: 标签列表
     */
    private final Map<String, List<MediaTag>> tagStore = new ConcurrentHashMap<>();

    /**
     * 预定义标签库
     */
    private static final Map<String, List<String>> PREDEFINED_TAGS = new LinkedHashMap<>();
    
    static {
        // 图片分类标签
        PREDEFINED_TAGS.put("场景", Arrays.asList(
                "室内", "户外", "城市", "自然", "夜景", "白天", "黄昏", "日出"
        ));
        PREDEFINED_TAGS.put("内容", Arrays.asList(
                "人物", "动物", "建筑", "风景", "食物", "交通工具", "文档", "截图"
        ));
        PREDEFINED_TAGS.put("风格", Arrays.asList(
                "写实", "卡通", "手绘", "极简", "复古", "现代", "科技", "文艺"
        ));
        PREDEFINED_TAGS.put("色调", Arrays.asList(
                "暖色调", "冷色调", "黑白", "高饱和", "低饱和", "明亮", "暗调"
        ));

        // 音频分类标签
        PREDEFINED_TAGS.put("音频类型", Arrays.asList(
                "音乐", "语音", "环境音", "音效", "播客", "有声书", "录音"
        ));
        PREDEFINED_TAGS.put("音乐风格", Arrays.asList(
                "流行", "摇滚", "古典", "爵士", "电子", "民谣", "嘻哈", "轻音乐"
        ));
        PREDEFINED_TAGS.put("语言", Arrays.asList(
                "中文", "英文", "日文", "韩文", "其他语言"
        ));
        PREDEFINED_TAGS.put("场景用途", Arrays.asList(
                "背景音乐", "教学", "会议", "采访", "演讲", "娱乐"
        ));
    }

    /**
     * 为图片自动生成标签
     * 基于文件名、格式、尺寸等元数据推理标签
     * 生产环境应调用视觉AI API进行内容识别
     */
    public List<MediaTag> autoTagImage(String fileId, String filename, String format,
                                        int width, int height, Map<String, Object> metadata) {
        List<MediaTag> tags = new ArrayList<>();

        // 基于格式推断
        if ("png".equalsIgnoreCase(format)) {
            addTag(tags, "PNG格式", "格式", 0.95);
        } else if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            addTag(tags, "JPEG格式", "格式", 0.95);
        }

        // 基于尺寸推断
        if (width > 2000 || height > 2000) {
            addTag(tags, "高清大图", "质量", 0.9);
        } else if (width > 1000 || height > 1000) {
            addTag(tags, "中等分辨率", "质量", 0.9);
        }

        // 基于文件名关键词推断
        String lowerName = filename.toLowerCase();
        if (lowerName.contains("screenshot") || lowerName.contains("截图")) {
            addTag(tags, "截图", "内容", 0.8);
        }
        if (lowerName.contains("photo") || lowerName.contains("照片")) {
            addTag(tags, "照片", "内容", 0.7);
        }

        // 基于EXIF信息推断
        if (metadata != null) {
            if (metadata.containsKey("cameraMake")) {
                addTag(tags, "相机拍摄", "来源", 0.85);
            }
            if (metadata.containsKey("gpsLatitude")) {
                addTag(tags, "含GPS信息", "属性", 0.9);
            }
        }

        // 保存标签
        tagStore.put(fileId, tags);
        log.info("图片自动标签完成: fileId={}, 标签数={}", fileId, tags.size());
        return tags;
    }

    /**
     * 为音频自动生成标签
     */
    public List<MediaTag> autoTagAudio(String fileId, String filename, String format,
                                        double duration, Map<String, Object> metadata) {
        List<MediaTag> tags = new ArrayList<>();

        // 基于格式
        switch (format.toLowerCase()) {
            case "mp3": addTag(tags, "MP3格式", "格式", 0.95); break;
            case "wav": addTag(tags, "WAV无损", "格式", 0.95); break;
            case "flac": addTag(tags, "FLAC无损", "格式", 0.95); break;
            case "aac": addTag(tags, "AAC格式", "格式", 0.9); break;
            default: addTag(tags, format.toUpperCase() + "格式", "格式", 0.8);
        }

        // 基于时长
        if (duration < 60) {
            addTag(tags, "短音频", "时长", 0.9);
        } else if (duration < 300) {
            addTag(tags, "中等时长", "时长", 0.9);
        } else {
            addTag(tags, "长音频", "时长", 0.9);
        }

        // 基于元数据
        if (metadata != null) {
            if (metadata.containsKey("artist")) {
                addTag(tags, "有艺术家信息", "属性", 0.85);
            }
            if (metadata.containsKey("album")) {
                addTag(tags, "有专辑信息", "属性", 0.85);
            }
        }

        tagStore.put(fileId, tags);
        log.info("音频自动标签完成: fileId={}, 标签数={}", fileId, tags.size());
        return tags;
    }

    /**
     * 手动添加标签
     */
    public void addManualTag(String fileId, String tagName, String category) {
        List<MediaTag> tags = tagStore.computeIfAbsent(fileId, k -> new ArrayList<>());
        // 检查是否已存在
        boolean exists = tags.stream().anyMatch(t -> t.getName().equals(tagName));
        if (!exists) {
            MediaTag tag = new MediaTag();
            tag.setName(tagName);
            tag.setCategory(category);
            tag.setConfidence(1.0); // 手动标签置信度为1
            tag.setSource("manual");
            tag.setCreatedTime(LocalDateTime.now());
            tags.add(tag);
        }
    }

    /**
     * 移除标签
     */
    public void removeTag(String fileId, String tagName) {
        List<MediaTag> tags = tagStore.get(fileId);
        if (tags != null) {
            tags.removeIf(t -> t.getName().equals(tagName));
        }
    }

    /**
     * 获取文件的标签
     */
    public List<MediaTag> getTags(String fileId) {
        return tagStore.getOrDefault(fileId, Collections.emptyList());
    }

    /**
     * 按标签搜索文件
     */
    public List<String> searchByTag(String tagName, String category) {
        List<String> matchedFileIds = new ArrayList<>();
        for (Map.Entry<String, List<MediaTag>> entry : tagStore.entrySet()) {
            boolean matched = entry.getValue().stream().anyMatch(tag -> {
                boolean nameMatch = tag.getName().equals(tagName);
                boolean categoryMatch = category == null || tag.getCategory().equals(category);
                return nameMatch && categoryMatch;
            });
            if (matched) {
                matchedFileIds.add(entry.getKey());
            }
        }
        return matchedFileIds;
    }

    /**
     * 获取预定义标签库
     */
    public Map<String, List<String>> getPredefinedTags() {
        return new LinkedHashMap<>(PREDEFINED_TAGS);
    }

    /**
     * 推荐标签（基于已有标签统计）
     */
    public List<String> suggestTags(String fileId, int count) {
        Set<String> existingTags = tagStore.getOrDefault(fileId, Collections.emptyList())
                .stream().map(MediaTag::getName).collect(Collectors.toSet());

        List<String> suggestions = new ArrayList<>();
        for (List<String> tags : PREDEFINED_TAGS.values()) {
            for (String tag : tags) {
                if (!existingTags.contains(tag) && suggestions.size() < count) {
                    suggestions.add(tag);
                }
            }
        }
        return suggestions;
    }

    private void addTag(List<MediaTag> tags, String name, String category, double confidence) {
        MediaTag tag = new MediaTag();
        tag.setName(name);
        tag.setCategory(category);
        tag.setConfidence(confidence);
        tag.setSource("auto");
        tag.setCreatedTime(LocalDateTime.now());
        tags.add(tag);
    }

    /**
     * 媒体标签实体
     */
    public static class MediaTag {
        private String name;
        private String category;
        private double confidence;
        private String source; // auto/manual
        private LocalDateTime createdTime;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    }
}
