package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 图片访问统计服务
 * 记录图片的浏览量、下载量、分享量等数据
 * 支持按时间维度统计热门图片
 */
@Slf4j
@Service
public class ImageStatsService {

    /**
     * 统计数据存储（内存缓存，生产环境应使用Redis/数据库）
     */
    private final Map<String, ImageStats> statsStore = new ConcurrentHashMap<>();

    /**
     * 记录图片浏览
     */
    public void recordView(String fileId, String userId) {
        ImageStats stats = statsStore.computeIfAbsent(fileId, k -> new ImageStats(fileId));
        stats.incrementViews();
        stats.recordAccess(LocalDateTime.now());
    }

    /**
     * 记录图片下载
     */
    public void recordDownload(String fileId, String userId) {
        ImageStats stats = statsStore.computeIfAbsent(fileId, k -> new ImageStats(fileId));
        stats.incrementDownloads();
        stats.recordAccess(LocalDateTime.now());
    }

    /**
     * 记录图片分享
     */
    public void recordShare(String fileId, String userId) {
        ImageStats stats = statsStore.computeIfAbsent(fileId, k -> new ImageStats(fileId));
        stats.incrementShares();
        stats.recordAccess(LocalDateTime.now());
    }

    /**
     * 获取图片统计信息
     */
    public ImageStats getStats(String fileId) {
        return statsStore.getOrDefault(fileId, new ImageStats(fileId));
    }

    /**
     * 获取所有图片统计信息
     */
    public List<ImageStats> getAllStats() {
        return new ArrayList<>(statsStore.values());
    }

    /**
     * 获取热门图片排行（按浏览量）
     */
    public List<ImageStats> getHotImages(int topN, String sortBy) {
        Comparator<ImageStats> comparator;
        switch (sortBy.toLowerCase()) {
            case "downloads":
                comparator = Comparator.comparingLong(ImageStats::getDownloads).reversed();
                break;
            case "shares":
                comparator = Comparator.comparingLong(ImageStats::getShares).reversed();
                break;
            case "views":
            default:
                comparator = Comparator.comparingLong(ImageStats::getViews).reversed();
                break;
        }

        return statsStore.values().stream()
                .sorted(comparator)
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定时间段的统计汇总
     */
    public Map<String, Object> getSummary(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> summary = new HashMap<>();
        long totalViews = 0;
        long totalDownloads = 0;
        long totalShares = 0;
        int totalImages = statsStore.size();

        for (ImageStats stats : statsStore.values()) {
            totalViews += stats.getViews();
            totalDownloads += stats.getDownloads();
            totalShares += stats.getShares();
        }

        summary.put("totalImages", totalImages);
        summary.put("totalViews", totalViews);
        summary.put("totalDownloads", totalDownloads);
        summary.put("totalShares", totalShares);
        summary.put("startTime", startTime);
        summary.put("endTime", endTime);

        return summary;
    }

    /**
     * 重置某图片的统计信息
     */
    public void resetStats(String fileId) {
        statsStore.remove(fileId);
    }

    /**
     * 图片统计数据
     */
    public static class ImageStats {
        /** 图片文件ID */
        private String fileId;
        /** 累计浏览次数 */
        private long views;
        /** 累计下载次数 */
        private long downloads;
        /** 累计分享次数 */
        private long shares;
        /** 首次访问时间 */
        private LocalDateTime firstAccess;
        /** 最近访问时间 */
        private LocalDateTime lastAccess;

        public ImageStats(String fileId) {
            this.fileId = fileId;
            this.views = 0;
            this.downloads = 0;
            this.shares = 0;
            this.firstAccess = LocalDateTime.now();
            this.lastAccess = LocalDateTime.now();
        }

        public void incrementViews() { this.views++; }
        public void incrementDownloads() { this.downloads++; }
        public void incrementShares() { this.shares++; }

        public void recordAccess(LocalDateTime time) {
            if (this.firstAccess == null) {
                this.firstAccess = time;
            }
            this.lastAccess = time;
        }

        // Getters and Setters
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public long getViews() { return views; }
        public void setViews(long views) { this.views = views; }
        public long getDownloads() { return downloads; }
        public void setDownloads(long downloads) { this.downloads = downloads; }
        public long getShares() { return shares; }
        public void setShares(long shares) { this.shares = shares; }
        public LocalDateTime getFirstAccess() { return firstAccess; }
        public void setFirstAccess(LocalDateTime firstAccess) { this.firstAccess = firstAccess; }
        public LocalDateTime getLastAccess() { return lastAccess; }
        public void setLastAccess(LocalDateTime lastAccess) { this.lastAccess = lastAccess; }
    }
}
