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

    private final Map<String, AudioStats> statsStore = new ConcurrentHashMap<>();

    public void recordPlay(String fileId) {
        AudioStats stats = statsStore.computeIfAbsent(fileId, k -> new AudioStats(fileId));
        stats.incrementPlays();
        stats.recordAccess(LocalDateTime.now());
    }

    public void recordDownload(String fileId) {
        AudioStats stats = statsStore.computeIfAbsent(fileId, k -> new AudioStats(fileId));
        stats.incrementDownloads();
        stats.recordAccess(LocalDateTime.now());
    }

    public AudioStats getStats(String fileId) {
        return statsStore.getOrDefault(fileId, new AudioStats(fileId));
    }

    public List<AudioStats> getAllStats() {
        return new ArrayList<>(statsStore.values());
    }

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

    public void resetStats(String fileId) {
        statsStore.remove(fileId);
    }

    public static class AudioStats {
        private String fileId;
        private long plays;
        private long downloads;
        private LocalDateTime firstAccess;
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
