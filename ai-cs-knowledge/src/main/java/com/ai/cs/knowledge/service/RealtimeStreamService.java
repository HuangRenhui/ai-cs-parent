package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * 实时音视频流知识抽取服务
 * 支持从实时音视频流中提取知识并入库
 *
 * 增强功能：实时音视频流知识抽取
 */
@Slf4j
@Service
public class RealtimeStreamService {

    private final VideoKeyFrameService keyFrameService;
    private final VisionLLMClient visionLLMClient;
    private final MultimodalKnowledgeService knowledgeService;

    // 流处理任务管理器
    private final Map<String, StreamProcessTask> activeStreams = new ConcurrentHashMap<>();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    public RealtimeStreamService(VideoKeyFrameService keyFrameService,
                                  VisionLLMClient visionLLMClient,
                                  MultimodalKnowledgeService knowledgeService) {
        this.keyFrameService = keyFrameService;
        this.visionLLMClient = visionLLMClient;
        this.knowledgeService = knowledgeService;
    }

    /**
     * 流处理任务
     */
    public static class StreamProcessTask {
        public String streamId;
        public String streamType; // video, audio, screen
        public String streamSource; // RTSP/RTMP URL 或设备ID
        public String status; // RUNNING, PAUSED, STOPPED, ERROR
        public long startTime;
        public int processedFrames;
        public int extractedKnowledge;
        public List<Map<String, Object>> recentKnowledge;
        public String lastError;

        public StreamProcessTask(String streamId, String streamType, String streamSource) {
            this.streamId = streamId;
            this.streamType = streamType;
            this.streamSource = streamSource;
            this.status = "RUNNING";
            this.startTime = System.currentTimeMillis();
            this.processedFrames = 0;
            this.extractedKnowledge = 0;
            this.recentKnowledge = new ArrayList<>();
        }
    }

    /**
     * 开始处理实时视频流
     * @param streamId 流ID
     * @param streamSource RTSP/RTMP地址或设备路径
     * @param intervalSeconds 知识抽取间隔（秒）
     * @return 流处理任务
     */
    public StreamProcessTask startVideoStreamProcessing(String streamId, String streamSource,
                                                         int intervalSeconds) {
        if (activeStreams.containsKey(streamId)) {
            log.warn("流 {} 已在处理中", streamId);
            return activeStreams.get(streamId);
        }

        StreamProcessTask task = new StreamProcessTask(streamId, "video", streamSource);
        activeStreams.put(streamId, task);

        streamExecutor.submit(() -> {
            while ("RUNNING".equals(task.status)) {
                try {
                    // 1. 从流中截取当前帧
                    String framePath = captureStreamFrame(streamSource, streamId);
                    task.processedFrames++;

                    // 2. 多模态大模型分析帧内容
                    if (framePath != null) {
                        String analysis = visionLLMClient.analyzeImage(framePath,
                                "请分析这一帧的内容，提取关键信息：场景、对象、人物、动作、文字等。");

                        // 3. 提取知识点
                        Map<String, Object> knowledge = new HashMap<>();
                        knowledge.put("timestamp", System.currentTimeMillis());
                        knowledge.put("frameIndex", task.processedFrames);
                        knowledge.put("analysis", analysis);
                        knowledge.put("source", streamSource);

                        // 4. 入库
                        try {
                            knowledgeService.indexImageKnowledge(
                                    streamId + "_" + task.processedFrames,
                                    framePath,
                                    "实时流知识_" + streamId + "_" + task.processedFrames,
                                    analysis,
                                    analysis,
                                    extractKeywords(analysis),
                                    "实时流," + streamId
                            );
                            task.extractedKnowledge++;
                        } catch (Exception e) {
                            log.warn("流知识入库失败: {}", e.getMessage());
                        }

                        synchronized (task.recentKnowledge) {
                            task.recentKnowledge.add(0, knowledge);
                            if (task.recentKnowledge.size() > 100) {
                                task.recentKnowledge.remove(task.recentKnowledge.size() - 1);
                            }
                        }
                    }

                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("流处理异常: {}", streamId, e);
                    task.lastError = e.getMessage();
                    task.status = "ERROR";
                }
            }
        });

        log.info("开始处理实时流: streamId={}, source={}", streamId, streamSource);
        return task;
    }

    /**
     * 停止流处理
     */
    public boolean stopStreamProcessing(String streamId) {
        StreamProcessTask task = activeStreams.get(streamId);
        if (task != null) {
            task.status = "STOPPED";
            activeStreams.remove(streamId);
            log.info("停止流处理: streamId={}, 共处理{}帧, 提取{}条知识",
                    streamId, task.processedFrames, task.extractedKnowledge);
            return true;
        }
        return false;
    }

    /**
     * 暂停流处理
     */
    public boolean pauseStreamProcessing(String streamId) {
        StreamProcessTask task = activeStreams.get(streamId);
        if (task != null && "RUNNING".equals(task.status)) {
            task.status = "PAUSED";
            return true;
        }
        return false;
    }

    /**
     * 恢复流处理
     */
    public boolean resumeStreamProcessing(String streamId) {
        StreamProcessTask task = activeStreams.get(streamId);
        if (task != null && "PAUSED".equals(task.status)) {
            task.status = "RUNNING";
            return true;
        }
        return false;
    }

    /**
     * 获取流处理状态
     */
    public StreamProcessTask getStreamStatus(String streamId) {
        return activeStreams.get(streamId);
    }

    /**
     * 获取所有活跃流
     */
    public List<StreamProcessTask> getAllActiveStreams() {
        return new ArrayList<>(activeStreams.values());
    }

    /**
     * 获取流中最近提取的知识
     */
    public List<Map<String, Object>> getRecentKnowledge(String streamId, int count) {
        StreamProcessTask task = activeStreams.get(streamId);
        if (task == null) return Collections.emptyList();

        synchronized (task.recentKnowledge) {
            int end = Math.min(count, task.recentKnowledge.size());
            return new ArrayList<>(task.recentKnowledge.subList(0, end));
        }
    }

    /**
     * 实时音频流处理（框架接口）
     */
    public StreamProcessTask startAudioStreamProcessing(String streamId, String streamSource,
                                                         int intervalSeconds) {
        StreamProcessTask task = new StreamProcessTask(streamId, "audio", streamSource);
        activeStreams.put(streamId, task);

        streamExecutor.submit(() -> {
            while ("RUNNING".equals(task.status)) {
                try {
                    // 音频流处理框架
                    // 1. 截取音频片段
                    // 2. ASR语音转文字
                    // 3. 实体/关键词提取
                    // 4. 知识入库

                    task.processedFrames++;
                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("音频流处理异常: {}", streamId, e);
                    task.lastError = e.getMessage();
                }
            }
        });

        log.info("开始处理音频流: streamId={}", streamId);
        return task;
    }

    /**
     * 流知识汇总报告
     */
    public Map<String, Object> getStreamSummary(String streamId) {
        StreamProcessTask task = activeStreams.get(streamId);
        if (task == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "NOT_FOUND");
            return result;
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("streamId", task.streamId);
        summary.put("type", task.streamType);
        summary.put("status", task.status);
        summary.put("duration", System.currentTimeMillis() - task.startTime);
        summary.put("processedFrames", task.processedFrames);
        summary.put("extractedKnowledge", task.extractedKnowledge);
        summary.put("extractionRate", task.processedFrames > 0 ?
                (double) task.extractedKnowledge / task.processedFrames : 0);
        return summary;
    }

    // ========== 辅助方法 ==========

    /**
     * 从流中截取帧（框架实现）
     */
    private String captureStreamFrame(String streamSource, String streamId) {
        // 实际生产环境使用FFmpeg从RTSP/RTMP流截帧
        // ffmpeg -rtsp_transport tcp -i <rtsp_url> -vframes 1 -q:v 2 frame.jpg
        log.debug("截取流帧: source={}, streamId={}", streamSource, streamId);
        return null; // 框架返回null，实际部署时返回帧文件路径
    }

    /**
     * 从分析文本中提取关键词
     */
    private String extractKeywords(String text) {
        if (text == null || text.isEmpty()) return "";
        // 简单关键词提取：取长度>=2的词
        Set<String> words = new LinkedHashSet<>();
        for (String word : text.split("[，,。.!！?？\\s]+")) {
            if (word.length() >= 2 && word.length() <= 10) {
                words.add(word);
            }
        }
        return String.join(",", words.stream().limit(20).toList());
    }
}
