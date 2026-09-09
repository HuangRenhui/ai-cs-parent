package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.ImageProperties;
import com.ai.cs.knowledge.entity.ImageMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 图片版本管理服务
 * 支持同一图片的多版本管理和回退功能
 * 类似Git的版本管理理念，每次修改生成一个新版本
 */
@Slf4j
@Service
public class ImageVersionService {

    private final ImageProperties imageProperties;

    /**
     * 版本记录存储（内存缓存，生产环境应使用数据库）
     * key: originalFileId, value: 版本列表
     */
    private final Map<String, List<ImageVersion>> versionStore = new ConcurrentHashMap<>();

    public ImageVersionService(ImageProperties imageProperties) {
        this.imageProperties = imageProperties;
    }

    /**
     * 创建新版本（修改图片时调用）
     * @param originalFileId 原始文件ID
     * @param newFilePath 新版本文件路径
     * @param changeDescription 变更描述
     * @return 新版本号
     */
    public ImageVersion createVersion(String originalFileId, String newFilePath, 
                                       String changeDescription) {
        List<ImageVersion> versions = versionStore.computeIfAbsent(
                originalFileId, k -> new ArrayList<>());

        int versionNumber = versions.size() + 1;
        ImageVersion version = new ImageVersion();
        version.setVersionNumber(versionNumber);
        version.setFileId(originalFileId);
        version.setVersionFileId(UUID.randomUUID().toString());
        version.setFilePath(newFilePath);
        version.setChangeDescription(changeDescription);
        version.setCreatedTime(LocalDateTime.now());
        version.setActive(versionNumber == 1); // 第一个版本默认为活跃版本

        versions.add(version);
        log.info("创建图片版本: fileId={}, version={}, 描述={}", 
                originalFileId, versionNumber, changeDescription);
        return version;
    }

    /**
     * 获取所有版本
     */
    public List<ImageVersion> getVersions(String fileId) {
        return versionStore.getOrDefault(fileId, Collections.emptyList());
    }

    /**
     * 获取指定版本
     */
    public ImageVersion getVersion(String fileId, int versionNumber) {
        List<ImageVersion> versions = versionStore.get(fileId);
        if (versions == null) {
            return null;
        }
        return versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取当前活跃版本
     */
    public ImageVersion getActiveVersion(String fileId) {
        List<ImageVersion> versions = versionStore.get(fileId);
        if (versions == null) {
            return null;
        }
        return versions.stream()
                .filter(ImageVersion::isActive)
                .findFirst()
                .orElse(null);
    }

    /**
     * 切换到指定版本
     */
    public ImageVersion switchToVersion(String fileId, int versionNumber) {
        List<ImageVersion> versions = versionStore.get(fileId);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("版本不存在: " + fileId);
        }

        ImageVersion targetVersion = versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("版本号不存在: " + versionNumber));

        // 取消所有版本的活跃状态
        versions.forEach(v -> v.setActive(false));
        // 激活目标版本
        targetVersion.setActive(true);

        log.info("图片版本切换: fileId={}, 从 v{} 切换到 v{}", 
                fileId, 
                versions.stream().filter(v -> !v.isActive()).findFirst()
                        .map(v -> String.valueOf(v.getVersionNumber())).orElse("?"),
                versionNumber);
        return targetVersion;
    }

    /**
     * 回退到上一个版本
     */
    public ImageVersion rollback(String fileId) {
        List<ImageVersion> versions = versionStore.get(fileId);
        if (versions == null || versions.size() < 2) {
            throw new IllegalArgumentException("没有可回退的版本: " + fileId);
        }

        ImageVersion currentActive = getActiveVersion(fileId);
        if (currentActive == null || currentActive.getVersionNumber() <= 1) {
            throw new IllegalArgumentException("已是初始版本，无法回退: " + fileId);
        }

        return switchToVersion(fileId, currentActive.getVersionNumber() - 1);
    }

    /**
     * 比较两个版本的差异
     */
    public VersionDiff compareVersions(String fileId, int version1, int version2) {
        ImageVersion v1 = getVersion(fileId, version1);
        ImageVersion v2 = getVersion(fileId, version2);

        if (v1 == null || v2 == null) {
            throw new IllegalArgumentException("版本不存在");
        }

        VersionDiff diff = new VersionDiff();
        diff.setFileId(fileId);
        diff.setVersion1(version1);
        diff.setVersion2(version2);
        diff.setCreatedTime1(v1.getCreatedTime());
        diff.setCreatedTime2(v2.getCreatedTime());
        diff.setDescription1(v1.getChangeDescription());
        diff.setDescription2(v2.getChangeDescription());

        // 比较文件大小
        File f1 = new File(v1.getFilePath());
        File f2 = new File(v2.getFilePath());
        if (f1.exists() && f2.exists()) {
            diff.setSizeDiff(f2.length() - f1.length());
        }

        return diff;
    }

    /**
     * 删除版本
     */
    public void deleteVersion(String fileId, int versionNumber) {
        List<ImageVersion> versions = versionStore.get(fileId);
        if (versions == null) {
            return;
        }
        
        ImageVersion version = versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElse(null);

        if (version != null && !version.isActive()) {
            // 删除版本文件
            try {
                File versionFile = new File(version.getFilePath());
                if (versionFile.exists()) {
                    versionFile.delete();
                }
            } catch (Exception e) {
                log.warn("删除版本文件失败: {}", version.getFilePath());
            }
            versions.remove(version);
            log.info("删除图片版本: fileId={}, version={}", fileId, versionNumber);
        }
    }

    /**
     * 清理版本历史（保留最近N个版本）
     */
    public int cleanupVersions(String fileId, int keepCount) {
        List<ImageVersion> versions = versionStore.get(fileId);
        if (versions == null || versions.size() <= keepCount) {
            return 0;
        }

        int removed = 0;
        // 按版本号降序，删除最早的版本（跳过活跃版本）
        versions.sort(Comparator.comparingInt(ImageVersion::getVersionNumber).reversed());
        for (int i = keepCount; i < versions.size(); i++) {
            ImageVersion v = versions.get(i);
            if (!v.isActive()) {
                deleteVersion(fileId, v.getVersionNumber());
                removed++;
            }
        }

        log.info("版本清理完成: fileId={}, 保留{}个版本, 删除{}个版本", fileId, keepCount, removed);
        return removed;
    }

    /**
     * 图片版本实体
     */
    public static class ImageVersion {
        /** 版本号（从1开始递增） */
        private int versionNumber;
        /** 原始图片文件ID */
        private String fileId;
        /** 本版本的唯一文件ID */
        private String versionFileId;
        /** 本版本文件的存储路径 */
        private String filePath;
        /** 本次变更描述 */
        private String changeDescription;
        /** 版本创建时间 */
        private LocalDateTime createdTime;
        /** 是否为当前生效版本（同一fileId下只有一个活跃版本） */
        private boolean active;

        // Getters and Setters
        public int getVersionNumber() { return versionNumber; }
        public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getVersionFileId() { return versionFileId; }
        public void setVersionFileId(String versionFileId) { this.versionFileId = versionFileId; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getChangeDescription() { return changeDescription; }
        public void setChangeDescription(String changeDescription) { this.changeDescription = changeDescription; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    /**
     * 版本差异对比结果
     */
    public static class VersionDiff {
        /** 原始图片文件ID */
        private String fileId;
        /** 对比的版本号1 */
        private int version1;
        /** 对比的版本号2 */
        private int version2;
        /** 版本1创建时间 */
        private LocalDateTime createdTime1;
        /** 版本2创建时间 */
        private LocalDateTime createdTime2;
        /** 版本1变更描述 */
        private String description1;
        /** 版本2变更描述 */
        private String description2;
        /** 文件大小差值（版本2 - 版本1，单位字节） */
        private long sizeDiff;

        // Getters and Setters
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public int getVersion1() { return version1; }
        public void setVersion1(int version1) { this.version1 = version1; }
        public int getVersion2() { return version2; }
        public void setVersion2(int version2) { this.version2 = version2; }
        public LocalDateTime getCreatedTime1() { return createdTime1; }
        public void setCreatedTime1(LocalDateTime createdTime1) { this.createdTime1 = createdTime1; }
        public LocalDateTime getCreatedTime2() { return createdTime2; }
        public void setCreatedTime2(LocalDateTime createdTime2) { this.createdTime2 = createdTime2; }
        public String getDescription1() { return description1; }
        public void setDescription1(String description1) { this.description1 = description1; }
        public String getDescription2() { return description2; }
        public void setDescription2(String description2) { this.description2 = description2; }
        public long getSizeDiff() { return sizeDiff; }
        public void setSizeDiff(long sizeDiff) { this.sizeDiff = sizeDiff; }
    }
}
