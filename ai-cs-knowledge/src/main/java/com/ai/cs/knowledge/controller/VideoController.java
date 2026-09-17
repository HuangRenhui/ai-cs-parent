package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 视频上传与处理控制器（占位实现）
 *
 * <p>「多模态知识 - 上传入库」需要视频上传接口，先在此提供空的接口壳，
 * 让前端上传 → 入库的流程可以完整跑通；具体的格式校验、转码、抽帧、
 * 时长/分辨率元数据提取与缩略图生成等实现后续补齐。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/video")
@Tag(name = "视频管理", description = "视频上传与处理接口（占位实现，待补齐）")
public class VideoController {

    /**
     * 上传视频（占位）
     *
     * <p>TODO 后续实现：格式与大小校验、转码、抽帧封面、时长/分辨率元数据提取。</p>
     *
     * @param file 视频文件
     * @return 资源标识（fileId / originalFilename / fileSize），implemented=false 表示后端尚未真正落地
     */
    @PostMapping("/upload")
    @Operation(summary = "上传视频", description = "上传视频文件并返回资源标识，供多模态知识入库使用（当前为占位实现）")
    public Result<Map<String, Object>> uploadVideo(
            @Parameter(description = "视频文件") @RequestParam("file") MultipartFile file) {
        log.info("[占位] 收到视频上传请求: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", "video-" + System.currentTimeMillis());
        result.put("originalFilename", file.getOriginalFilename());
        result.put("storagePath", null);
        result.put("fileSize", file.getSize());
        result.put("implemented", false);
        return Result.success(result);
    }
}
