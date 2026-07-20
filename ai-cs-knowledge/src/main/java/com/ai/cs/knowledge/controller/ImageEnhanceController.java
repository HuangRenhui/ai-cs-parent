package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 图片增强功能控制器
 * 提供去重检测、版本管理、访问统计、防盗链、批量上传、OCR识别、内容审核、智能标签等增强功能
 */
@Slf4j
@RestController
@RequestMapping("/api/image/enhance")
@Tag(name = "图片增强功能", description = "图片去重、版本管理、统计、防盗链、批量处理等增强接口")
public class ImageEnhanceController {

    private final ImageDeduplicationService deduplicationService;
    private final ImageVersionService versionService;
    private final ImageStatsService statsService;
    private final ImageHotlinkService hotlinkService;
    private final ImageStorageService storageService;
    private final MediaOcrService ocrService;
    private final MediaModerationService moderationService;
    private final MediaTagService tagService;
    private final BatchProcessService batchProcessService;
    private final ImageProcessService imageProcessService;

    public ImageEnhanceController(ImageDeduplicationService deduplicationService,
                                   ImageVersionService versionService,
                                   ImageStatsService statsService,
                                   ImageHotlinkService hotlinkService,
                                   ImageStorageService storageService,
                                   MediaOcrService ocrService,
                                   MediaModerationService moderationService,
                                   MediaTagService tagService,
                                   BatchProcessService batchProcessService,
                                   ImageProcessService imageProcessService) {
        this.deduplicationService = deduplicationService;
        this.versionService = versionService;
        this.statsService = statsService;
        this.hotlinkService = hotlinkService;
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.moderationService = moderationService;
        this.tagService = tagService;
        this.batchProcessService = batchProcessService;
        this.imageProcessService = imageProcessService;
    }

    // ========== 批量上传处理 ==========

    @PostMapping("/batch/upload")
    @Operation(summary = "批量上传图片", description = "一次上传多张图片并批量处理")
    public Result<BatchProcessService.BatchResult> batchUpload(
            @Parameter(description = "图片文件列表") @RequestParam("files") MultipartFile[] files) {
        try {
            BatchProcessService.BatchResult result = batchProcessService.processBatch(files, file -> {
                BatchProcessService.FileResult fr = new BatchProcessService.FileResult();
                fr.setOriginalFilename(file.getOriginalFilename());
                // 调用现有上传处理逻辑
                var metadata = imageProcessService.uploadAndProcess(file);
                fr.setFileId(metadata.getFileId());
                Map<String, Object> meta = new HashMap<>();
                meta.put("format", metadata.getFormat());
                meta.put("width", metadata.getWidth());
                meta.put("height", metadata.getHeight());
                meta.put("fileSize", metadata.getFileSize());
                meta.put("compressed", metadata.isCompressed());
                meta.put("vectorized", metadata.isVectorized());
                fr.setMetadata(meta);
                return fr;
            });
            return Result.success(result);
        } catch (Exception e) {
            log.error("批量上传失败", e);
            return Result.fail("批量上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/batch/progress/{batchId}")
    @Operation(summary = "查询批量处理进度", description = "查询批量上传处理的实时进度")
    public Result<BatchProcessService.BatchProgress> batchProgress(
            @PathVariable String batchId) {
        BatchProcessService.BatchProgress progress = batchProcessService.getProgress(batchId);
        if (progress == null) {
            return Result.fail("批次不存在");
        }
        return Result.success(progress);
    }

    // ========== 图片去重检测 ==========

    @PostMapping("/dedup/check")
    @Operation(summary = "图片去重检测", description = "基于感知哈希检测图片是否重复")
    public Result<ImageDeduplicationService.DeduplicationResult> checkDuplicate(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(fileBytes));
            if (image == null) {
                return Result.fail("无法解析图片");
            }

            String md5Hash = deduplicationService.computeMd5Hash(fileBytes);
            String perceptualHash = deduplicationService.computePerceptualHash(image);
            String fileId = UUID.randomUUID().toString();

            // 更新哈希索引的文件名
            deduplicationService.updateHashIndex(fileId, file.getOriginalFilename());

            ImageDeduplicationService.DeduplicationResult result =
                    deduplicationService.checkDuplicate(md5Hash, perceptualHash, fileId);

            return Result.success(result);
        } catch (Exception e) {
            log.error("去重检测失败", e);
            return Result.fail("去重检测失败: " + e.getMessage());
        }
    }

    @GetMapping("/dedup/similar")
    @Operation(summary = "查找相似图片", description = "查找与指定图片视觉上相似的图片")
    public Result<List<ImageDeduplicationService.SimilarImageResult>> findSimilar(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "5") int topK) {
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(file.getBytes()));
            if (image == null) {
                return Result.fail("无法解析图片");
            }
            String perceptualHash = deduplicationService.computePerceptualHash(image);
            List<ImageDeduplicationService.SimilarImageResult> results =
                    deduplicationService.findSimilar(perceptualHash, topK);
            return Result.success(results);
        } catch (Exception e) {
            log.error("查找相似图片失败", e);
            return Result.fail("查找相似图片失败: " + e.getMessage());
        }
    }

    // ========== 版本管理 ==========

    @GetMapping("/version/{fileId}")
    @Operation(summary = "获取图片版本列表", description = "获取图片的所有历史版本")
    public Result<List<ImageVersionService.ImageVersion>> getVersions(
            @PathVariable String fileId) {
        return Result.success(versionService.getVersions(fileId));
    }

    @PostMapping("/version/{fileId}/switch")
    @Operation(summary = "切换图片版本", description = "切换到指定的历史版本")
    public Result<ImageVersionService.ImageVersion> switchVersion(
            @PathVariable String fileId,
            @RequestParam int versionNumber) {
        try {
            ImageVersionService.ImageVersion version = versionService.switchToVersion(fileId, versionNumber);
            return Result.success(version);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/version/{fileId}/rollback")
    @Operation(summary = "回退图片版本", description = "回退到上一个版本")
    public Result<ImageVersionService.ImageVersion> rollbackVersion(
            @PathVariable String fileId) {
        try {
            ImageVersionService.ImageVersion version = versionService.rollback(fileId);
            return Result.success(version);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/version/{fileId}/diff")
    @Operation(summary = "对比版本差异", description = "比较两个版本的差异")
    public Result<ImageVersionService.VersionDiff> compareVersions(
            @PathVariable String fileId,
            @RequestParam int version1,
            @RequestParam int version2) {
        try {
            ImageVersionService.VersionDiff diff = versionService.compareVersions(fileId, version1, version2);
            return Result.success(diff);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    // ========== 访问统计 ==========

    @GetMapping("/stats/{fileId}")
    @Operation(summary = "获取图片统计", description = "获取图片的浏览量、下载量等统计数据")
    public Result<ImageStatsService.ImageStats> getStats(@PathVariable String fileId) {
        return Result.success(statsService.getStats(fileId));
    }

    @GetMapping("/stats/hot")
    @Operation(summary = "热门图片排行", description = "获取浏览量或下载量最高的图片排行")
    public Result<List<ImageStatsService.ImageStats>> getHotImages(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "views") String sortBy) {
        return Result.success(statsService.getHotImages(topN, sortBy));
    }

    @GetMapping("/stats/summary")
    @Operation(summary = "图片统计汇总", description = "获取所有图片的统计数据汇总")
    public Result<Map<String, Object>> getStatsSummary() {
        return Result.success(statsService.getSummary(null, null));
    }

    @PostMapping("/stats/{fileId}/view")
    @Operation(summary = "记录图片浏览", description = "记录一次图片浏览事件")
    public Result<Void> recordView(@PathVariable String fileId) {
        statsService.recordView(fileId, null);
        return Result.success();
    }

    @PostMapping("/stats/{fileId}/download")
    @Operation(summary = "记录图片下载", description = "记录一次图片下载事件")
    public Result<Void> recordDownload(@PathVariable String fileId) {
        statsService.recordDownload(fileId, null);
        return Result.success();
    }

    // ========== 防盗链 ==========

    @PostMapping("/hotlink/token")
    @Operation(summary = "生成访问Token", description = "为图片生成防盗链访问Token")
    public Result<Map<String, String>> generateToken(
            @RequestParam String fileId,
            @RequestParam(defaultValue = "3600") long expireSeconds) {
        String token = hotlinkService.generateToken(fileId, expireSeconds);
        Map<String, String> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("token", token);
        result.put("expireSeconds", String.valueOf(expireSeconds));
        return Result.success(result);
    }

    @PostMapping("/hotlink/signed-url")
    @Operation(summary = "生成签名URL", description = "生成带签名的防盗链URL")
    public Result<Map<String, String>> generateSignedUrl(
            @RequestParam String fileId,
            @RequestParam(defaultValue = "3600") long expireSeconds) {
        String signedUrl = hotlinkService.generateSignedUrl("/api/image/download/" + fileId, fileId, expireSeconds);
        Map<String, String> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("signedUrl", signedUrl);
        result.put("expireSeconds", String.valueOf(expireSeconds));
        return Result.success(result);
    }

    @GetMapping("/hotlink/whitelist")
    @Operation(summary = "获取Referer白名单", description = "获取防盗链Referer白名单列表")
    public Result<Set<String>> getWhitelist() {
        return Result.success(hotlinkService.getRefererWhitelist());
    }

    @PostMapping("/hotlink/whitelist")
    @Operation(summary = "添加Referer白名单", description = "添加域名到Referer白名单")
    public Result<Void> addWhitelist(@RequestParam String domain) {
        hotlinkService.addRefererWhitelist(domain);
        return Result.success();
    }

    @DeleteMapping("/hotlink/whitelist")
    @Operation(summary = "移除Referer白名单", description = "从Referer白名单中移除域名")
    public Result<Void> removeWhitelist(@RequestParam String domain) {
        hotlinkService.removeRefererWhitelist(domain);
        return Result.success();
    }

    // ========== OCR文字识别 ==========

    @PostMapping("/ocr")
    @Operation(summary = "图片OCR识别", description = "提取图片中的文字内容")
    public Result<MediaOcrService.OcrResult> performOcr(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        try {
            File tempFile = saveTempFile(file);
            MediaOcrService.OcrResult result = ocrService.performImageOcr(tempFile);
            tempFile.delete();
            return Result.success(result);
        } catch (Exception e) {
            log.error("OCR识别失败", e);
            return Result.fail("OCR识别失败: " + e.getMessage());
        }
    }

    // ========== AI内容审核 ==========

    @PostMapping("/moderate")
    @Operation(summary = "图片内容审核", description = "AI审核图片内容（涉黄涉暴检测）")
    public Result<MediaModerationService.ModerationResult> moderateImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        try {
            File tempFile = saveTempFile(file);
            MediaModerationService.ModerationResult result = moderationService.moderateImage(tempFile);
            tempFile.delete();
            return Result.success(result);
        } catch (Exception e) {
            log.error("内容审核失败", e);
            return Result.fail("内容审核失败: " + e.getMessage());
        }
    }

    // ========== 智能标签 ==========

    @PostMapping("/tags/auto")
    @Operation(summary = "自动生成标签", description = "基于图片元数据自动生成标签")
    public Result<List<MediaTagService.MediaTag>> autoTag(
            @Parameter(description = "文件ID") @RequestParam String fileId,
            @Parameter(description = "文件名") @RequestParam String filename,
            @Parameter(description = "格式") @RequestParam(defaultValue = "jpg") String format,
            @Parameter(description = "宽度") @RequestParam(defaultValue = "0") int width,
            @Parameter(description = "高度") @RequestParam(defaultValue = "0") int height) {
        List<MediaTagService.MediaTag> tags = tagService.autoTagImage(fileId, filename, format, width, height, null);
        return Result.success(tags);
    }

    @GetMapping("/tags/{fileId}")
    @Operation(summary = "获取图片标签", description = "获取图片的所有标签")
    public Result<List<MediaTagService.MediaTag>> getTags(@PathVariable String fileId) {
        return Result.success(tagService.getTags(fileId));
    }

    @PostMapping("/tags/{fileId}")
    @Operation(summary = "添加图片标签", description = "手动为图片添加标签")
    public Result<Void> addTag(
            @PathVariable String fileId,
            @RequestParam String tagName,
            @RequestParam(defaultValue = "自定义") String category) {
        tagService.addManualTag(fileId, tagName, category);
        return Result.success();
    }

    @DeleteMapping("/tags/{fileId}")
    @Operation(summary = "移除图片标签", description = "移除图片的指定标签")
    public Result<Void> removeTag(
            @PathVariable String fileId,
            @RequestParam String tagName) {
        tagService.removeTag(fileId, tagName);
        return Result.success();
    }

    @GetMapping("/tags/search")
    @Operation(summary = "按标签搜索图片", description = "根据标签名称搜索匹配的图片")
    public Result<List<String>> searchByTag(
            @RequestParam String tagName,
            @RequestParam(required = false) String category) {
        return Result.success(tagService.searchByTag(tagName, category));
    }

    @GetMapping("/tags/predefined")
    @Operation(summary = "获取预定义标签库", description = "获取系统预定义的标签分类和选项")
    public Result<Map<String, List<String>>> getPredefinedTags() {
        return Result.success(tagService.getPredefinedTags());
    }

    @GetMapping("/tags/suggest/{fileId}")
    @Operation(summary = "推荐标签", description = "基于已有标签推荐新的标签")
    public Result<List<String>> suggestTags(
            @PathVariable String fileId,
            @RequestParam(defaultValue = "5") int count) {
        return Result.success(tagService.suggestTags(fileId, count));
    }

    // ========== 对象存储 ==========

    @GetMapping("/storage/url/{fileId}")
    @Operation(summary = "获取存储访问URL", description = "获取图片的存储访问URL（含CDN加速）")
    public Result<Map<String, String>> getStorageUrl(@PathVariable String fileId) {
        Map<String, String> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("accessUrl", storageService.getAccessUrl(fileId));
        result.put("cdnUrl", storageService.getCdnUrl(fileId));
        return Result.success(result);
    }

    // ========== 工具方法 ==========

    private File saveTempFile(MultipartFile file) throws Exception {
        String tempPath = System.getProperty("java.io.tmpdir") + "/" +
                System.currentTimeMillis() + "_" + file.getOriginalFilename();
        file.transferTo(new File(tempPath));
        return new File(tempPath);
    }
}
