package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.AudioMetadata;
import com.ai.cs.knowledge.service.AudioProcessService;
import com.ai.cs.knowledge.service.AudioVectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 音频上传与处理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/audio")
@Tag(name = "音频管理", description = "音频上传、处理、格式转换等接口")
public class AudioController {

    private final AudioProcessService audioProcessService;
    private final AudioVectorService audioVectorService;

    public AudioController(AudioProcessService audioProcessService,
                           AudioVectorService audioVectorService) {
        this.audioProcessService = audioProcessService;
        this.audioVectorService = audioVectorService;
    }

    /**
     * 上传音频并自动处理
     */
    @PostMapping("/upload")
    @Operation(summary = "上传音频", description = "上传音频文件并自动进行压缩、提取元数据、生成波形图等处理")
    public Result<Map<String, Object>> uploadAudio(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file) {
        try {
            log.info("开始上传音频: {}, 大小: {} bytes", 
                    file.getOriginalFilename(), file.getSize());
            
            AudioMetadata metadata = audioProcessService.uploadAndProcess(file);
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileId", metadata.getFileId());
            result.put("originalFilename", metadata.getOriginalFilename());
            result.put("storagePath", metadata.getStoragePath());
            result.put("fileSize", metadata.getFileSize());
            result.put("compressedSize", metadata.getCompressedSize());
            result.put("format", metadata.getFormat());
            result.put("duration", metadata.getDuration());
            result.put("bitrate", metadata.getBitrate());
            result.put("sampleRate", metadata.getSampleRate());
            result.put("channels", metadata.getChannels());
            result.put("artist", metadata.getArtist());
            result.put("title", metadata.getTitle());
            result.put("album", metadata.getAlbum());
            result.put("waveformPath", metadata.getWaveformPath());
            result.put("compressed", metadata.isCompressed());
            result.put("compressionRatio", metadata.getCompressionRatio());
            result.put("uploadTime", metadata.getUploadTime());
            result.put("vectorized", metadata.isVectorized());
            result.put("vectorId", metadata.getVectorId());
            result.put("vectorCollection", metadata.getVectorCollection());
            result.put("vectorDescription", metadata.getVectorDescription());
            
            log.info("音频上传处理完成: {}", metadata.getFileId());
            return Result.success(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("音频上传失败 - 参数错误: {}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("音频上传处理异常", e);
            return Result.fail("音频上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取音频元数据
     */
    @GetMapping("/metadata/{fileId}")
    @Operation(summary = "获取音频元数据", description = "根据文件ID获取音频的详细信息")
    public Result<AudioMetadata> getAudioMetadata(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        try {
            // 这里应该从数据库或缓存中获取元数据
            // 简化实现：返回基本信息
            AudioMetadata metadata = new AudioMetadata();
            metadata.setFileId(fileId);
            return Result.success(metadata);
        } catch (Exception e) {
            log.error("获取音频元数据失败", e);
            return Result.fail("获取元数据失败: " + e.getMessage());
        }
    }

    /**
     * 下载音频
     */
    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载音频", description = "根据文件ID下载原始音频")
    public ResponseEntity<Resource> downloadAudio(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        try {
            // 构建文件路径（实际应从数据库查询）
            String basePath = "./uploads/audios";
            
            // 查找匹配的文件
            File[] files = new File(basePath).listFiles((dir, name) -> name.startsWith(fileId));
            if (files == null || files.length == 0) {
                return ResponseEntity.notFound().build();
            }
            
            File targetFile = files[0];
            Resource resource = new FileSystemResource(targetFile);
            
            String contentType = Files.probeContentType(targetFile.toPath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + targetFile.getName() + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("下载音频失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 音频格式转换
     */
    @PostMapping("/convert")
    @Operation(summary = "音频格式转换", description = "将音频转换为指定格式")
    public Result<Map<String, String>> convertFormat(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "目标格式(mp3/wav/aac/flac)") @RequestParam String targetFormat) {
        try {
            // 先保存临时文件
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + 
                    System.currentTimeMillis() + "_" + file.getOriginalFilename();
            file.transferTo(new File(tempPath));
            
            // 执行格式转换
            String convertedPath = audioProcessService.convertFormat(
                    new File(tempPath), targetFormat);
            
            // 删除临时文件
            new File(tempPath).delete();
            
            Map<String, String> result = new HashMap<>();
            result.put("convertedPath", convertedPath);
            result.put("targetFormat", targetFormat);
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("音频格式转换失败", e);
            return Result.fail("格式转换失败: " + e.getMessage());
        }
    }

    /**
     * 调整音频比特率
     */
    @PostMapping("/adjust-bitrate")
    @Operation(summary = "调整音频比特率", description = "调整音频到指定比特率")
    public Result<Map<String, String>> adjustBitrate(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "目标比特率(kbps)") @RequestParam int bitrate) {
        try {
            // 先保存临时文件
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + 
                    System.currentTimeMillis() + "_" + file.getOriginalFilename();
            file.transferTo(new File(tempPath));
            
            // 执行比特率调整
            String outputPath = audioProcessService.adjustBitrate(
                    new File(tempPath), bitrate);
            
            // 删除临时文件
            new File(tempPath).delete();
            
            Map<String, String> result = new HashMap<>();
            result.put("outputPath", outputPath);
            result.put("bitrate", String.valueOf(bitrate));
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("音频比特率调整失败", e);
            return Result.fail("比特率调整失败: " + e.getMessage());
        }
    }

    /**
     * 获取音频时长
     */
    @PostMapping("/duration")
    @Operation(summary = "获取音频时长", description = "获取音频文件的播放时长")
    public Result<Map<String, Object>> getDuration(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file) {
        try {
            // 先保存临时文件
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + 
                    System.currentTimeMillis() + "_" + file.getOriginalFilename();
            file.transferTo(new File(tempPath));
            
            // 提取元数据获取时长
            AudioMetadata metadata = audioProcessService.extractMetadata(new File(tempPath));
            
            // 删除临时文件
            new File(tempPath).delete();
            
            Map<String, Object> result = new HashMap<>();
            result.put("duration", metadata.getDuration());
            result.put("durationFormatted", formatDuration(metadata.getDuration()));
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("获取音频时长失败", e);
            return Result.fail("获取时长失败: " + e.getMessage());
        }
    }

    /**
     * 格式化时长显示
     */
    private String formatDuration(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }

    /**
     * 删除音频
     */
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除音频", description = "根据文件ID删除音频及其波形图")
    public Result<Void> deleteAudio(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        try {
            // 删除原音频和波形图
            String[] paths = {
                    "./uploads/audios/" + fileId + ".*",
                    "./uploads/audios/" + fileId + "_waveform.png"
            };
            
            for (String pathPattern : paths) {
                String dir = pathPattern.substring(0, pathPattern.lastIndexOf("/"));
                String prefix = fileId;
                
                File directory = new File(dir);
                if (directory.exists()) {
                    File[] files = directory.listFiles((d, name) -> name.startsWith(prefix));
                    if (files != null) {
                        for (File f : files) {
                            audioProcessService.deleteAudio(f.getAbsolutePath());
                        }
                    }
                }
            }
            
            return Result.success(null);
            
        } catch (Exception e) {
            log.error("删除音频失败", e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    // ========== 音频向量化与语义搜索接口 ==========

    /**
     * 手动向量化单个音频
     */
    @PostMapping("/vectorize")
    @Operation(summary = "手动向量化音频", description = "根据音频元数据手动执行向量化并存入向量库")
    public Result<Map<String, Object>> vectorizeAudio(
            @Parameter(description = "文件ID") @RequestParam String fileId,
            @Parameter(description = "原始文件名") @RequestParam String originalFilename,
            @Parameter(description = "存储路径") @RequestParam String storagePath,
            @Parameter(description = "音频格式") @RequestParam(defaultValue = "mp3") String format,
            @Parameter(description = "时长(秒)") @RequestParam(defaultValue = "0") double duration,
            @Parameter(description = "标题") @RequestParam(required = false) String title,
            @Parameter(description = "艺术家") @RequestParam(required = false) String artist,
            @Parameter(description = "专辑") @RequestParam(required = false) String album,
            @Parameter(description = "流派") @RequestParam(required = false) String genre,
            @Parameter(description = "转录文本") @RequestParam(required = false) String transcription) {
        try {
            AudioMetadata metadata = new AudioMetadata();
            metadata.setFileId(fileId);
            metadata.setOriginalFilename(originalFilename);
            metadata.setStoragePath(storagePath);
            metadata.setFormat(format);
            metadata.setDuration(duration);
            metadata.setTitle(title);
            metadata.setArtist(artist);
            metadata.setAlbum(album);
            metadata.setGenre(genre);
            metadata.setTranscription(transcription);
            metadata.setUploadTime(java.time.LocalDateTime.now());

            String vectorId = audioVectorService.vectorize(metadata);

            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileId);
            result.put("vectorId", vectorId);
            result.put("vectorized", true);
            result.put("vectorCollection", metadata.getVectorCollection());
            result.put("vectorDescription", metadata.getVectorDescription());

            return Result.success(result);

        } catch (Exception e) {
            log.error("音频向量化失败", e);
            return Result.fail("向量化失败: " + e.getMessage());
        }
    }

    /**
     * 更新音频转录并重新向量化
     */
    @PostMapping("/transcribe-and-vectorize")
    @Operation(summary = "转录并向量化音频", description = "更新音频转录文本并重新向量化，增强语义检索效果")
    public Result<Map<String, Object>> transcribeAndVectorize(
            @Parameter(description = "文件ID") @RequestParam String fileId,
            @Parameter(description = "原始文件名") @RequestParam String originalFilename,
            @Parameter(description = "存储路径") @RequestParam String storagePath,
            @Parameter(description = "音频格式") @RequestParam(defaultValue = "mp3") String format,
            @Parameter(description = "时长(秒)") @RequestParam(defaultValue = "0") double duration,
            @Parameter(description = "转录文本") @RequestParam String transcription,
            @Parameter(description = "已有向量ID(可选)") @RequestParam(required = false) String existingVectorId) {
        try {
            AudioMetadata metadata = new AudioMetadata();
            metadata.setFileId(fileId);
            metadata.setOriginalFilename(originalFilename);
            metadata.setStoragePath(storagePath);
            metadata.setFormat(format);
            metadata.setDuration(duration);
            metadata.setTranscription(transcription);
            metadata.setUploadTime(java.time.LocalDateTime.now());

            // 如果有旧向量ID，设置后会自动删除旧向量
            if (existingVectorId != null && !existingVectorId.isEmpty()) {
                metadata.setVectorId(existingVectorId);
            }

            String vectorId = audioVectorService.reVectorizeWithTranscription(metadata);

            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileId);
            result.put("vectorId", vectorId);
            result.put("vectorized", true);
            result.put("transcription", transcription);
            result.put("vectorCollection", metadata.getVectorCollection());
            result.put("vectorDescription", metadata.getVectorDescription());

            return Result.success(result);

        } catch (Exception e) {
            log.error("转录并向量化失败", e);
            return Result.fail("转录并向量化失败: " + e.getMessage());
        }
    }

    /**
     * 音频语义搜索
     */
    @GetMapping("/search")
    @Operation(summary = "音频语义搜索", description = "基于自然语言描述搜索相似音频")
    public Result<Map<String, Object>> searchAudios(
            @Parameter(description = "搜索查询文本") @RequestParam String query,
            @Parameter(description = "最大返回结果数") @RequestParam(defaultValue = "5") int maxResults) {
        try {
            java.util.List<Map<String, Object>> results = audioVectorService.search(query, maxResults);

            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("totalResults", results.size());
            response.put("results", results);

            return Result.success(response);

        } catch (Exception e) {
            log.error("音频语义搜索失败", e);
            return Result.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 删除音频向量
     */
    @DeleteMapping("/vector/{vectorId}")
    @Operation(summary = "删除音频向量", description = "根据向量ID从向量库中删除音频向量")
    public Result<Void> deleteAudioVector(
            @Parameter(description = "向量记录ID") @PathVariable String vectorId) {
        try {
            audioVectorService.deleteByVectorId(vectorId);
            return Result.success(null);
        } catch (Exception e) {
            log.error("删除音频向量失败", e);
            return Result.fail("删除向量失败: " + e.getMessage());
        }
    }
}
