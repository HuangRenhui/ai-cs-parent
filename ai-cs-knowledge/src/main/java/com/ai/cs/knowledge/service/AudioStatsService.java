package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 音频访问统计服务
 * 记录音频播放量、下载量等数据
 */
@Slf4j
@Service
public class AudioStatsService {

    /** 统计数据存储（内存缓存，服务重启后丢失；生产环境应使用Redis/数据库持久化） */
    private final Map<String, AudioStats> statsStore = new ConcurrentHashMap<>();

    /**
     * 记录一次音频播放
     * @param fileId 音频文件ID（不存在时自动创建统计条目）
     */
    public void recordPlay(String fileId) {
        // computeIfAbsent 保证并发安全地获取或创建统计条目
        AudioStats stats = statsStore.computeIfAbsent(fileId, k -> new AudioStats(fileId));
        stats.incrementPlays();
        stats.recordAccess(LocalDateTime.now());
    }

    /**
     * 记录一次音频下载
     * @param fileId 音频文件ID
     */
    public void recordDownload(String fileId) {
        AudioStats stats = statsStore.computeIfAbsent(fileId, k -> new AudioStats(fileId));
        stats.incrementDownloads();
        stats.recordAccess(LocalDateTime.now());
    }

    /**
     * 获取单个音频的统计数据（无记录时返回全0的空统计，不返回null）
     */
    public AudioStats getStats(String fileId) {
        return statsStore.getOrDefault(fileId, new AudioStats(fileId));
    }

    /**
     * 获取全部音频统计数据
     */
    public List<AudioStats> getAllStats() {
        return new ArrayList<>(statsStore.values());
    }

    /**
     * 获取热门音频排行榜
     * @param topN 返回前N条
     * @param sortBy 排序维度：plays=按播放量（默认），downloads=按下载量
     * @return 按指标降序排列的热门音频列表
     */
    public List<AudioStats> getHotAudios(int topN, String sortBy) {
        Comparator<AudioStats> comparator;
        switch (sortBy.toLowerCase()) {
            case "downloads":
                comparator = Comparator.comparingLong(AudioStats::getDownloads).reversed();
                break;
            case "plays":
            default:
                comparator = Comparator.comparingLong(AudioStats::getPlays).reversed();
                break;
        }
        return statsStore.values().stream()
                .sorted(comparator)
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * 获取全局统计汇总（音频总数、总播放量、总下载量）
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        long totalPlays = 0, totalDownloads = 0;
        for (AudioStats stats : statsStore.values()) {
            totalPlays += stats.getPlays();
            totalDownloads += stats.getDownloads();
        }
        summary.put("totalAudios", statsStore.size());
        summary.put("totalPlays", totalPlays);
        summary.put("totalDownloads", totalDownloads);
        return summary;
    }

    /**
     * 重置指定音频的统计数据（直接从内存中移除）
     */
    public void resetStats(String fileId) {
        statsStore.remove(fileId);
    }

    /**
     * 单个音频的统计数据
     */
    public static class AudioStats {
        /** 音频文件ID */
        private String fileId;
        /** 累计播放次数 */
        private long plays;
        /** 累计下载次数 */
        private long downloads;
        /** 首次访问时间 */
        private LocalDateTime firstAccess;
        /** 最近访问时间 */
        private LocalDateTime lastAccess;

        public AudioStats(String fileId) {
            this.fileId = fileId;
            this.plays = 0;
            this.downloads = 0;
            this.firstAccess = LocalDateTime.now();
            this.lastAccess = LocalDateTime.now();
        }

        public void incrementPlays() { this.plays++; }
        public void incrementDownloads() { this.downloads++; }
        public void recordAccess(LocalDateTime time) {
            if (this.firstAccess == null) this.firstAccess = time;
            this.lastAccess = time;
        }

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public long getPlays() { return plays; }
        public void setPlays(long plays) { this.plays = plays; }
        public long getDownloads() { return downloads; }
        public void setDownloads(long downloads) { this.downloads = downloads; }
        public LocalDateTime getFirstAccess() { return firstAccess; }
        public void setFirstAccess(LocalDateTime firstAccess) { this.firstAccess = firstAccess; }
        public LocalDateTime getLastAccess() { return lastAccess; }
        public void setLastAccess(LocalDateTime lastAccess) { this.lastAccess = lastAccess; }
    }
}
