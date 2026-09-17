package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时音视频流知识抽取服务（占位）
 *
 * <p>TODO 后续实现：用 FFmpeg 从 RTSP/RTMP 流按间隔截帧 → {@code VisionLLMClient}
 * 逐帧理解 → 关键词提取 → 经 {@code MultimodalKnowledgeService.indexImageKnowledge}
 * 入库；并支持音频流的 ASR → 实体提取 → 入库，以及流任务生命周期与汇总统计。</p>
 *
 * <p>当前不截帧、不调模型、不入库：{@link #startVideoStreamProcessing} /
 * {@link #startAudioStreamProcessing} 只登记一个状态为 {@code ERROR} 的任务
 * （{@code lastError} 说明占位原因），**不启动任何后台线程**；
 * 状态查询类方法作用于该任务表，无任务时返回空结构。</p>
 *
 * <p>{@link StreamProcessTask} 为结果载体。</p>
 */
@Slf4j
@Service
public class RealtimeStreamService {

    /** 占位原因（写入任务的 lastError，便于联调时看到「为什么没有数据」） */
    private static final String NOT_IMPLEMENTED = "实时流知识抽取为占位实现，后端未接入流截帧与视觉模型";

    private final VideoKeyFrameService keyFrameService;
    private final VisionLLMClient visionLLMClient;
    private final MultimodalKnowledgeService knowledgeService;

    // 流处理任务管理器（占位期间仅用于登记占位任务，供状态查询回读）
    private final Map<String, StreamProcessTask> activeStreams = new ConcurrentHashMap<>();

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
     * 开始处理实时视频流（占位：登记 ERROR 任务，不启动后台线程）
     *
     * @param streamId 流ID
     * @param streamSource RTSP/RTMP地址或设备路径
     * @param intervalSeconds 知识抽取间隔（秒）
     * @return 状态为 ERROR 的占位任务（lastError 说明占位原因）
     */
    public StreamProcessTask startVideoStreamProcessing(String streamId, String streamSource,
                                                         int intervalSeconds) {
        log.warn("[占位] 实时视频流知识抽取未实现 streamId={} source={} interval={}",
                streamId, streamSource, intervalSeconds);

        StreamProcessTask task = new StreamProcessTask(streamId, "video", streamSource);
        task.status = "ERROR";
        task.lastError = NOT_IMPLEMENTED;
        activeStreams.put(streamId, task);
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
     * 获取流中最近提取的知识（占位期间无抽取结果）
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
     * 实时音频流处理（占位：登记 ERROR 任务，不启动后台线程）
     *
     * @return 状态为 ERROR 的占位任务（lastError 说明占位原因）
     */
    public StreamProcessTask startAudioStreamProcessing(String streamId, String streamSource,
                                                         int intervalSeconds) {
        log.warn("[占位] 实时音频流知识抽取未实现 streamId={} source={} interval={}",
                streamId, streamSource, intervalSeconds);

        StreamProcessTask task = new StreamProcessTask(streamId, "audio", streamSource);
        task.status = "ERROR";
        task.lastError = NOT_IMPLEMENTED;
        activeStreams.put(streamId, task);
        return task;
    }

    /**
     * 流知识汇总报告
     */
    public Map<String, Object> getStreamSummary(String streamId) {
        StreamProcessTask task = activeStreams.get(streamId);
        if (task == null) {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("status", "NOT_FOUND");
            return result;
        }

        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("streamId", task.streamId);
        summary.put("type", task.streamType);
        summary.put("status", task.status);
        summary.put("duration", System.currentTimeMillis() - task.startTime);
        summary.put("processedFrames", task.processedFrames);
        summary.put("extractedKnowledge", task.extractedKnowledge);
        summary.put("extractionRate", task.processedFrames > 0 ?
                (double) task.extractedKnowledge / task.processedFrames : 0);
        summary.put("lastError", task.lastError);
        return summary;
    }
}
