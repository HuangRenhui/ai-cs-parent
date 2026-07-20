package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.UploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 文件上传服务
 * 负责处理知识库文档的上传、临时存储和清理
 */
@Slf4j
@Service
public class FileUploadService {

    private final UploadProperties uploadProperties;

    public FileUploadService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
        // 创建临时文件夹
        File dir = new File(uploadProperties.getTempPath());
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 保存上传文件到临时目录，返回本地路径
     * @param file 上传的文件
     * @return 文件的绝对路径
     * @throws IOException IO异常
     */
    public String saveTempFile(MultipartFile file) throws IOException {
        // 生成唯一文件名，防止重名覆盖
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            throw new IOException("文件名不能为空");
        }
        
        String suffix = originalName.substring(originalName.lastIndexOf("."));
        String newFileName = UUID.randomUUID() + suffix;

        File destFile = new File(uploadProperties.getTempPath() + File.separator + newFileName);
        file.transferTo(destFile);
        return destFile.getAbsolutePath();
    }

    /**
     * 删除临时文件（用完立刻清理，防止磁盘堆积）
     * @param filePath 文件路径
     */
    public void deleteTempFile(String filePath) {
        try {
            FileUtils.forceDelete(new File(filePath));
        } catch (IOException e) {
            // 记录警告但不抛出异常，避免影响主流程
            log.warn("删除临时文件失败: {}, 错误: {}", filePath, e.getMessage());
        }
    }
}
