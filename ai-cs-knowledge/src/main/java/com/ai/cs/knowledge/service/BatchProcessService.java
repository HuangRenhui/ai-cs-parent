package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量处理服务
 * 支持图片和音频的批量上传和处理
 * 提供并发处理能力和进度追踪
 */
@Slf4j
@Service
public class BatchProcessService {

    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    /**
     * 批量处理结果
     */
    public static class BatchResult {
        private String batchId;
        private int totalFiles;
        private int successCount;
        private int failCount;
        private List<FileResult> results;
        private long duration;

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailCount() { return failCount; }
        public void setFailCount(int failCount) { this.failCount = failCount; }
        public List<FileResult> getResults() { return results; }
        public void setResults(List<FileResult> results) { this.results = results; }
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
    }

    /**
     * 单个文件处理结果
     */
    public static class FileResult {
        private String originalFilename;
        private String fileId;
        private boolean success;
        private String errorMessage;
        private Map<String, Object> metadata;

        public String getOriginalFilename() { return originalFilename; }
        public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * 批量处理进度
     */
    public static class BatchProgress {
        private String batchId;
        private int total;
        private int processed;
        private int success;
        private int failed;
        private boolean completed;
        private double percentage;

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getProcessed() { return processed; }
        public void setProcessed(int processed) { this.processed = processed; }
        public int getSuccess() { return success; }
        public void setSuccess(int success) { this.success = success; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public double getPercentage() { return percentage; }
        public void setPercentage(double percentage) { this.percentage = percentage; }
    }

    /**
     * 处理任务接口
     */
    public interface FileProcessor {
        FileResult process(MultipartFile file) throws Exception;
    }

    /** 进度存储 */
    private final Map<String, BatchProgress> progressStore = new ConcurrentHashMap<>();

    /**
     * 并发批量处理文件
     */
    public BatchResult processBatch(MultipartFile[] files, FileProcessor processor) {
        String batchId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        BatchResult result = new BatchResult();
        result.setBatchId(batchId);
        result.setTotalFiles(files.length);

        BatchProgress progress = new BatchProgress();
        progress.setBatchId(batchId);
        progress.setTotal(files.length);
        progressStore.put(batchId, progress);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        List<FileResult> results = Collections.synchronizedList(new ArrayList<>());

        // 创建并发任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (MultipartFile file : files) {
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                FileResult fr = new FileResult();
                fr.setOriginalFilename(file.getOriginalFilename());
                try {
                    fr = processor.process(file);
                    fr.setSuccess(true);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fr.setSuccess(false);
                    fr.setErrorMessage(e.getMessage());
                    failCount.incrementAndGet();
                    log.warn("批量处理文件失败: {}, 错误: {}", file.getOriginalFilename(), e.getMessage());
                }
                results.add(fr);

                // 更新进度
                int processed = processedCount.incrementAndGet();
                progress.setProcessed(processed);
                progress.setSuccess(successCount.get());
                progress.setFailed(failCount.get());
                progress.setPercentage((double) processed / files.length * 100);

                return null;
            }, executorService);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        progress.setCompleted(true);
        progress.setPercentage(100.0);

        result.setSuccessCount(successCount.get());
        result.setFailCount(failCount.get());
        result.setResults(results);
        result.setDuration(System.currentTimeMillis() - startTime);

        log.info("批量处理完成: batchId={}, 成功={}, 失败={}, 耗时={}ms",
                batchId, successCount.get(), failCount.get(), result.getDuration());

        return result;
    }

    /**
     * 获取批量处理进度
     */
    public BatchProgress getProgress(String batchId) {
        return progressStore.get(batchId);
    }

    /**
     * 清理进度记录
     */
    public void cleanupProgress(String batchId) {
        progressStore.remove(batchId);
    }
}
