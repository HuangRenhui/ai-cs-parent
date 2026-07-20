package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频版本管理服务
 * 支持同一音频文件的多版本管理和回退
 */
@Slf4j
@Service
public class AudioVersionService {

    private final Map<String, List<AudioVersion>> versionStore = new ConcurrentHashMap<>();

    /**
     * 创建新版本
     */
    public AudioVersion createVersion(String fileId, String newFilePath, String changeDescription) {
        List<AudioVersion> versions = versionStore.computeIfAbsent(fileId, k -> new ArrayList<>());

        int versionNumber = versions.size() + 1;
        AudioVersion version = new AudioVersion();
        version.setVersionNumber(versionNumber);
        version.setFileId(fileId);
        version.setVersionFileId(UUID.randomUUID().toString());
        version.setFilePath(newFilePath);
        version.setChangeDescription(changeDescription);
        version.setCreatedTime(LocalDateTime.now());
        version.setActive(versionNumber == 1);

        versions.add(version);
        log.info("创建音频版本: fileId={}, version={}", fileId, versionNumber);
        return version;
    }

    /**
     * 获取所有版本
     */
    public List<AudioVersion> getVersions(String fileId) {
        return versionStore.getOrDefault(fileId, Collections.emptyList());
    }

    /**
     * 获取指定版本
     */
    public AudioVersion getVersion(String fileId, int versionNumber) {
        List<AudioVersion> versions = versionStore.get(fileId);
        if (versions == null) return null;
        return versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst().orElse(null);
    }

    /**
     * 获取当前活跃版本
     */
    public AudioVersion getActiveVersion(String fileId) {
        List<AudioVersion> versions = versionStore.get(fileId);
        if (versions == null) return null;
        return versions.stream()
                .filter(AudioVersion::isActive)
                .findFirst().orElse(null);
    }

    /**
     * 切换到指定版本
     */
    public AudioVersion switchToVersion(String fileId, int versionNumber) {
        List<AudioVersion> versions = versionStore.get(fileId);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("版本不存在: " + fileId);
        }
        AudioVersion target = versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("版本号不存在: " + versionNumber));

        versions.forEach(v -> v.setActive(false));
        target.setActive(true);
        log.info("音频版本切换: fileId={}, to v{}", fileId, versionNumber);
        return target;
    }

    /**
     * 回退到上一个版本
     */
    public AudioVersion rollback(String fileId) {
        AudioVersion active = getActiveVersion(fileId);
        if (active == null || active.getVersionNumber() <= 1) {
            throw new IllegalArgumentException("没有可回退的版本");
        }
        return switchToVersion(fileId, active.getVersionNumber() - 1);
    }

    /**
     * 删除版本
     */
    public void deleteVersion(String fileId, int versionNumber) {
        List<AudioVersion> versions = versionStore.get(fileId);
        if (versions == null) return;
        AudioVersion version = versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber && !v.isActive())
                .findFirst().orElse(null);
        if (version != null) {
            versions.remove(version);
            log.info("删除音频版本: fileId={}, version={}", fileId, versionNumber);
        }
    }

    public static class AudioVersion {
        private int versionNumber;
        private String fileId;
        private String versionFileId;
        private String filePath;
        private String changeDescription;
        private LocalDateTime createdTime;
        private boolean active;

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
}
