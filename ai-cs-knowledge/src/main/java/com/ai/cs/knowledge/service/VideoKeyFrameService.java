package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.MultimodalKnowledgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 视频关键帧服务（占位）
 *
 * <p>TODO 后续实现：
 * <ul>
 *   <li>{@link #extractKeyFrames}：按间隔抽帧（受 maxFrames 上限约束）并保存为图片，返回帧文件路径列表；</li>
 *   <li>{@link #detectSceneChanges}：基于帧间差异检测镜头切换，返回变化点时间戳列表；</li>
 *   <li>{@link #fullVideoAnalysis}：抽帧 + 视觉模型理解（{@code VisionLLMClient}）+
 *       写入多模态知识（{@code MultimodalKnowledgeService}）的完整链路；</li>
 *   <li>{@link #generateVideoSummary}：汇总帧理解结果生成视频摘要；</li>
 *   <li>{@link #analyzeVideoTimeline}：生成带时间戳的视频内容时间线。</li>
 * </ul>
 * </p>
 *
 * <p>当前不抽帧、不调用视觉模型：查询类方法返回空列表 / 空 Map，摘要返回未实现提示。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoKeyFrameService {

    private final MultimodalKnowledgeProperties properties;
    private final VisionLLMClient visionLLMClient;
    private final MultimodalKnowledgeService knowledgeService;

    /**
     * 抽取关键帧（占位：返回空列表）
     *
     * @param videoPath       视频路径
     * @param intervalSeconds 抽帧间隔（秒）
     * @param maxFrames       最大帧数
     * @return 空列表
     */
    public List<String> extractKeyFrames(String videoPath, int intervalSeconds, int maxFrames) {
        log.info("[占位] 视频抽帧未实现 videoPath={} interval={} maxFrames={}", videoPath, intervalSeconds, maxFrames);
        return List.of();
    }

    /**
     * 检测镜头切换（占位：返回空列表）
     */
    public List<Double> detectSceneChanges(String videoPath) {
        log.info("[占位] 镜头切换检测未实现 videoPath={}", videoPath);
        return List.of();
    }

    /**
     * 视频全量分析（占位：返回空 Map）
     */
    public Map<String, Object> fullVideoAnalysis(String videoPath, String title) {
        log.info("[占位] 视频全量分析未实现 videoPath={}", videoPath);
        return Map.of();
    }

    /**
     * 生成视频摘要（占位：返回未实现提示）
     */
    public String generateVideoSummary(String videoPath) {
        log.info("[占位] 视频摘要生成未实现 videoPath={}", videoPath);
        return "视频摘要为占位实现，后端未接入抽帧与视觉模型";
    }

    /**
     * 视频时间线分析（占位：返回空 Map）
     */
    public Map<String, Object> analyzeVideoTimeline(String videoPath) {
        log.info("[占位] 视频时间线分析未实现 videoPath={}", videoPath);
        return Map.of();
    }
}
