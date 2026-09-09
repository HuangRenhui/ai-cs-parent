package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

/**
 * 音频增强功能控制器
 * 提供去重检测、版本管理、访问统计、批量上传、语音识别、内容审核、智能标签、真实波形图等增强功能
 */
@Slf4j
@RestController
@RequestMapping("/api/audio/enhance")
@Tag(name = "音频增强功能", description = "音频去重、版本管理、统计、批量处理等增强接口")
public class AudioEnhanceController {

    private final AudioDeduplicationService deduplicationService;
    private final AudioVersionService versionService;
    private final AudioStatsService statsService;
    private final AudioWaveformService waveformService;
    private final MediaOcrService ocrService;
    private final MediaModerationService moderationService;
    private final MediaTagService tagService;
    private final BatchProcessService batchProcessService;
    private final AudioProcessService audioProcessService;

    public AudioEnhanceController(AudioDeduplicationService deduplicationService,
                                   AudioVersionService versionService,
                                   AudioStatsService statsService,
                                   AudioWaveformService waveformService,
                                   MediaOcrService ocrService,
                                   MediaModerationService moderationService,
                                   MediaTagService tagService,
                                   BatchProcessService batchProcessService,
                                   AudioProcessService audioProcessService) {
        this.deduplicationService = deduplicationService;
        this.versionService = versionService;
        this.statsService = statsService;
        this.waveformService = waveformService;
        this.ocrService = ocrService;
        this.moderationService = moderationService;
        this.tagService = tagService;
        this.batchProcessService = batchProcessService;
        this.audioProcessService = audioProcessService;
    }

    // ========== 批量上传处理 ==========

    /**
     * 批量上传音频
     */
    @PostMapping("/batch/upload")
    @Operation(summary = "批量上传音频", description = "一次上传多个音频文件并批量处理")
    public Result<BatchProcessService.BatchResult> batchUpload(
            @Parameter(description = "音频文件列表") @RequestParam("files") MultipartFile[] files) {
        try {
            // 委托批量处理服务逐文件处理，单文件失败不影响整批
            BatchProcessService.BatchResult result = batchProcessService.processBatch(files, file -> {
                BatchProcessService.FileResult fr = new BatchProcessService.FileResult();
                fr.setOriginalFilename(file.getOriginalFilename());
                var metadata = audioProcessService.uploadAndProcess(file);
                fr.setFileId(metadata.getFileId());
                // 提取关键元数据返回给前端展示
                Map<String, Object> meta = new HashMap<>();
                meta.put("format", metadata.getFormat());
                meta.put("duration", metadata.getDuration());
                meta.put("bitrate", metadata.getBitrate());
                meta.put("sampleRate", metadata.getSampleRate());
                meta.put("compressed", metadata.isCompressed());
                meta.put("vectorized", metadata.isVectorized());
                fr.setMetadata(meta);
                return fr;
            });
            return Result.success(result);
        } catch (Exception e) {
            log.error("批量上传音频失败", e);
            return Result.fail("批量上传失败: " + e.getMessage());
        }
    }

    /**
     * 查询批量处理进度
     */
    @GetMapping("/batch/progress/{batchId}")
    @Operation(summary = "查询批量处理进度", description = "查询批量上传处理的实时进度")
    public Result<BatchProcessService.BatchProgress> batchProgress(@PathVariable String batchId) {
        BatchProcessService.BatchProgress progress = batchProcessService.getProgress(batchId);
        if (progress == null) {
            return Result.fail("批次不存在");
        }
        return Result.success(progress);
    }

    // ========== 音频去重检测 ==========

    /**
     * 音频去重检测
     * 同时计算MD5（精确重复）与音频指纹（内容相似）两种特征进行判定
     */
    @PostMapping("/dedup/check")
    @Operation(summary = "音频去重检测", description = "基于音频指纹检测音频是否重复")
    public Result<AudioDeduplicationService.DeduplicationResult> checkDuplicate(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file) {
        try {
            // 指纹提取需要本地文件，先落盘为临时文件
            File tempFile = saveTempFile(file);
            // MD5用于检测字节级完全相同的文件，音频指纹用于检测内容相似的音频
            String md5Hash = deduplicationService.computeMd5Hash(file.getBytes());
            double[] fingerprint = deduplicationService.generateFingerprint(tempFile, 64);
            String fileId = UUID.randomUUID().toString();

            AudioDeduplicationService.DeduplicationResult result =
                    deduplicationService.checkDuplicate(md5Hash, fingerprint, fileId, file.getOriginalFilename());
            tempFile.delete();
            return Result.success(result);
        } catch (Exception e) {
            log.error("音频去重检测失败", e);
            return Result.fail("去重检测失败: " + e.getMessage());
        }
    }

    /**
     * 查找相似音频
     */
    @GetMapping("/dedup/similar")
    @Operation(summary = "查找相似音频", description = "查找与指定音频指纹相似的音频")
    public Result<List<AudioDeduplicationService.SimilarResult>> findSimilar(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "5") int topK) {
        try {
            // 提取音频指纹后立即删除临时文件，再用指纹做相似检索
            File tempFile = saveTempFile(file);
            double[] fingerprint = deduplicationService.generateFingerprint(tempFile, 64);
            tempFile.delete();
            return Result.success(deduplicationService.findSimilar(fingerprint, topK));
        } catch (Exception e) {
            log.error("查找相似音频失败", e);
            return Result.fail("查找相似音频失败: " + e.getMessage());
        }
    }

    // ========== 版本管理 ==========

    /**
     * 获取音频版本列表
     */
    @GetMapping("/version/{fileId}")
    @Operation(summary = "获取音频版本列表", description = "获取音频的所有历史版本")
    public Result<List<AudioVersionService.AudioVersion>> getVersions(@PathVariable String fileId) {
        return Result.success(versionService.getVersions(fileId));
    }

    /**
     * 切换音频版本
     */
    @PostMapping("/version/{fileId}/switch")
    @Operation(summary = "切换音频版本", description = "切换到指定的历史版本")
    public Result<AudioVersionService.AudioVersion> switchVersion(
            @PathVariable String fileId, @RequestParam int versionNumber) {
        try {
            return Result.success(versionService.switchToVersion(fileId, versionNumber));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 回退音频版本
     */
    @PostMapping("/version/{fileId}/rollback")
    @Operation(summary = "回退音频版本", description = "回退到上一个版本")
    public Result<AudioVersionService.AudioVersion> rollbackVersion(@PathVariable String fileId) {
        try {
            return Result.success(versionService.rollback(fileId));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    // ========== 访问统计 ==========

    /**
     * 获取音频统计
     */
    @GetMapping("/stats/{fileId}")
    @Operation(summary = "获取音频统计", description = "获取音频的播放量、下载量等统计数据")
    public Result<AudioStatsService.AudioStats> getStats(@PathVariable String fileId) {
        return Result.success(statsService.getStats(fileId));
    }

    /**
     * 热门音频排行
     */
    @GetMapping("/stats/hot")
    @Operation(summary = "热门音频排行", description = "获取播放量或下载量最高的音频排行")
    public Result<List<AudioStatsService.AudioStats>> getHotAudios(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "plays") String sortBy) {
        return Result.success(statsService.getHotAudios(topN, sortBy));
    }

    /**
     * 音频统计汇总
     */
    @GetMapping("/stats/summary")
    @Operation(summary = "音频统计汇总", description = "获取所有音频的统计数据汇总")
    public Result<Map<String, Object>> getStatsSummary() {
        return Result.success(statsService.getSummary());
    }

    /**
     * 记录音频播放
     */
    @PostMapping("/stats/{fileId}/play")
    @Operation(summary = "记录音频播放", description = "记录一次音频播放事件")
    public Result<Void> recordPlay(@PathVariable String fileId) {
        statsService.recordPlay(fileId);
        return Result.success();
    }

    /**
     * 记录音频下载
     */
    @PostMapping("/stats/{fileId}/download")
    @Operation(summary = "记录音频下载", description = "记录一次音频下载事件")
    public Result<Void> recordDownload(@PathVariable String fileId) {
        statsService.recordDownload(fileId);
        return Result.success();
    }

    // ========== 真实波形图 ==========

    /**
     * 生成真实波形图
     */
    @PostMapping("/waveform/{fileId}")
    @Operation(summary = "生成真实波形图", description = "使用FFmpeg生成精确的音频波形图")
    public Result<Map<String, String>> generateRealWaveform(
            @PathVariable String fileId,
            @Parameter(description = "是否生成立体声波形图") @RequestParam(defaultValue = "false") boolean stereo) {
        try {
            // 按fileId前缀在音频存储目录中查找文件（扩展名不定）
            File audioDir = new File("./uploads/audios");
            File[] files = audioDir.listFiles((d, name) -> name.startsWith(fileId));
            if (files == null || files.length == 0) {
                return Result.fail("音频文件不存在");
            }

            // 立体声波形图走FFmpeg双通道渲染，单声道走Java采样绘制
            String waveformPath;
            if (stereo) {
                waveformPath = waveformService.generateStereoWaveform(files[0], fileId);
            } else {
                waveformPath = waveformService.generateWaveformWithJava(files[0], fileId);
            }

            Map<String, String> result = new HashMap<>();
            result.put("fileId", fileId);
            result.put("waveformPath", waveformPath);
            result.put("stereo", String.valueOf(stereo));
            return Result.success(result);
        } catch (Exception e) {
            log.error("生成波形图失败", e);
            return Result.fail("生成波形图失败: " + e.getMessage());
        }
    }

    // ========== 语音识别(ASR) ==========

    /**
     * 音频语音识别
     */
    @PostMapping("/asr")
    @Operation(summary = "音频语音识别", description = "将音频中的语音转为文字（需要配置Whisper服务）")
    public Result<Map<String, Object>> performAsr(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file) {
        try {
            // ASR服务需要本地文件，先落盘为临时文件，识别完成后立即删除
            File tempFile = saveTempFile(file);
            String transcription = ocrService.performAudioAsr(tempFile);
            tempFile.delete();

            Map<String, Object> result = new HashMap<>();
            result.put("transcription", transcription);
            result.put("note", "需要配置Whisper服务地址以获取实际转录结果");
            return Result.success(result);
        } catch (Exception e) {
            log.error("语音识别失败", e);
            return Result.fail("语音识别失败: " + e.getMessage());
        }
    }

    // ========== AI内容审核 ==========

    /**
     * 音频内容审核
     */
    @PostMapping("/moderate")
    @Operation(summary = "音频内容审核", description = "AI审核音频内容（敏感内容检测）")
    public Result<MediaModerationService.ModerationResult> moderateAudio(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file) {
        try {
            // 审核服务需要本地文件，先落盘为临时文件，审核完成后立即删除
            File tempFile = saveTempFile(file);
            MediaModerationService.ModerationResult result = moderationService.moderateAudio(tempFile);
            tempFile.delete();
            return Result.success(result);
        } catch (Exception e) {
            log.error("音频审核失败", e);
            return Result.fail("内容审核失败: " + e.getMessage());
        }
    }

    // ========== 智能标签 ==========

    /**
     * 自动生成标签
     */
    @PostMapping("/tags/auto")
    @Operation(summary = "自动生成标签", description = "基于音频元数据自动生成标签")
    public Result<List<MediaTagService.MediaTag>> autoTag(
            @RequestParam String fileId,
            @RequestParam String filename,
            @RequestParam(defaultValue = "mp3") String format,
            @RequestParam(defaultValue = "0") double duration) {
        return Result.success(tagService.autoTagAudio(fileId, filename, format, duration, null));
    }

    /**
     * 获取音频标签
     */
    @GetMapping("/tags/{fileId}")
    @Operation(summary = "获取音频标签", description = "获取音频的所有标签")
    public Result<List<MediaTagService.MediaTag>> getTags(@PathVariable String fileId) {
        return Result.success(tagService.getTags(fileId));
    }

    /**
     * 添加音频标签
     */
    @PostMapping("/tags/{fileId}")
    @Operation(summary = "添加音频标签", description = "手动为音频添加标签")
    public Result<Void> addTag(
            @PathVariable String fileId,
            @RequestParam String tagName,
            @RequestParam(defaultValue = "自定义") String category) {
        tagService.addManualTag(fileId, tagName, category);
        return Result.success();
    }

    /**
     * 移除音频标签
     */
    @DeleteMapping("/tags/{fileId}")
    @Operation(summary = "移除音频标签", description = "移除音频的指定标签")
    public Result<Void> removeTag(@PathVariable String fileId, @RequestParam String tagName) {
        tagService.removeTag(fileId, tagName);
        return Result.success();
    }

    /**
     * 按标签搜索音频
     */
    @GetMapping("/tags/search")
    @Operation(summary = "按标签搜索音频", description = "根据标签名称搜索匹配的音频")
    public Result<List<String>> searchByTag(
            @RequestParam String tagName,
            @RequestParam(required = false) String category) {
        return Result.success(tagService.searchByTag(tagName, category));
    }

    // ========== 工具方法 ==========

    /**
     * 将上传文件保存为临时文件
     * 文件名加时间戳前缀避免并发上传同名文件互相覆盖，调用方使用完需自行删除
     */
    private File saveTempFile(MultipartFile file) throws Exception {
        String tempPath = System.getProperty("java.io.tmpdir") + "/" +
                System.currentTimeMillis() + "_" + file.getOriginalFilename();
        file.transferTo(new File(tempPath));
        return new File(tempPath);
    }
}
