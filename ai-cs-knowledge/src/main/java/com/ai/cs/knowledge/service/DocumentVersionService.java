package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.DocumentVersion;
import com.ai.cs.knowledge.mapper.DocumentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;

/**
 * 文档版本管理服务
 * 提供文档版本创建、查询、回退、对比等功能
 * 
 * @author huangrenhui
 * @date 2026/6/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersionService extends ServiceImpl<DocumentVersionMapper, DocumentVersion> {
    
    private final DocumentVersionMapper documentVersionMapper;
    
    /**
     * 创建新版本
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @param filePath 文件路径
     * @param fileType 文件类型
     * @param milvusCollectionId Milvus集合ID
     * @param segmentCount 片段数量
     * @param versionDescription 版本描述
     * @param uploaderId 上传人ID
     * @param uploaderName 上传人姓名
     * @return 新版本记录
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersion createVersion(String documentId, String documentName, String filePath, 
                                        String fileType, String milvusCollectionId, Integer segmentCount,
                                        String versionDescription, Long uploaderId, String uploaderName) {
        try {
            // 计算文件MD5
            String fileMd5 = calculateFileMD5(filePath);
            java.io.File file = new java.io.File(filePath);
            long fileSize = file.length();
            
            // 获取当前最大版本号
            Integer maxVersion = documentVersionMapper.selectMaxVersion(documentId);
            int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;
            
            // 创建新版本记录
            DocumentVersion version = new DocumentVersion();
            version.setDocumentId(documentId);
            version.setDocumentName(documentName);
            version.setVersion(newVersion);
            version.setFilePath(filePath);
            version.setFileSize(fileSize);
            version.setFileType(fileType);
            version.setFileMd5(fileMd5);
            version.setMilvusCollectionId(milvusCollectionId);
            version.setSegmentCount(segmentCount);
            version.setVersionDescription(versionDescription);
            version.setIsCurrent(1); // 新版本默认为当前版本
            version.setStatus(1); // 默认已发布
            version.setUploaderId(uploaderId);
            version.setUploaderName(uploaderName);
            
            // 将之前的当前版本标记为非当前
            documentVersionMapper.update(null, new LambdaUpdateWrapper<DocumentVersion>()
                    .eq(DocumentVersion::getDocumentId, documentId)
                    .eq(DocumentVersion::getIsCurrent, 1)
                    .set(DocumentVersion::getIsCurrent, 0));
            
            // 保存新版本
            documentVersionMapper.insert(version);
            
            log.info("创建文档版本成功: documentId={}, version={}", documentId, newVersion);
            return version;
        } catch (Exception e) {
            log.error("创建文档版本失败: documentId={}", documentId, e);
            throw new RuntimeException("创建文档版本失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取文档的所有版本
     * @param documentId 文档ID
     * @return 版本列表
     */
    public List<DocumentVersion> getVersionsByDocumentId(String documentId) {
        return documentVersionMapper.selectVersionsByDocumentId(documentId);
    }
    
    /**
     * 获取文档的当前版本
     * @param documentId 文档ID
     * @return 当前版本
     */
    public DocumentVersion getCurrentVersion(String documentId) {
        return documentVersionMapper.selectCurrentVersion(documentId);
    }
    
    /**
     * 回退到指定版本
     * @param documentId 文档ID
     * @param targetVersion 目标版本号
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public String rollbackToVersion(String documentId, Integer targetVersion) {
        try {
            // 查询目标版本
            DocumentVersion targetVersionRecord = documentVersionMapper.selectOne(
                    new LambdaQueryWrapper<DocumentVersion>()
                            .eq(DocumentVersion::getDocumentId, documentId)
                            .eq(DocumentVersion::getVersion, targetVersion)
                            .eq(DocumentVersion::getDelFlag, 0)
            );
            
            if (targetVersionRecord == null) {
                throw new RuntimeException("目标版本不存在");
            }
            
            // 将所有版本的is_current设为0
            documentVersionMapper.update(null, new LambdaUpdateWrapper<DocumentVersion>()
                    .eq(DocumentVersion::getDocumentId, documentId)
                    .set(DocumentVersion::getIsCurrent, 0));
            
            // 将目标版本设为当前版本
            targetVersionRecord.setIsCurrent(1);
            documentVersionMapper.updateById(targetVersionRecord);
            
            log.info("文档版本回退成功: documentId={}, targetVersion={}", documentId, targetVersion);
            return "版本回退成功，当前版本: " + targetVersion;
        } catch (Exception e) {
            log.error("文档版本回退失败: documentId={}, targetVersion={}", documentId, targetVersion, e);
            throw new RuntimeException("版本回退失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除指定版本
     * @param id 版本ID
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public String deleteVersion(Long id) {
        try {
            DocumentVersion version = documentVersionMapper.selectById(id);
            if (version == null) {
                throw new RuntimeException("版本不存在");
            }
            
            // 如果删除的是当前版本，需要先设置其他版本为当前版本
            if (version.getIsCurrent() == 1) {
                // 查找该文档的其他版本
                List<DocumentVersion> otherVersions = documentVersionMapper.selectList(
                        new LambdaQueryWrapper<DocumentVersion>()
                                .eq(DocumentVersion::getDocumentId, version.getDocumentId())
                                .ne(DocumentVersion::getId, id)
                                .eq(DocumentVersion::getDelFlag, 0)
                                .orderByDesc(DocumentVersion::getVersion)
                                .last("LIMIT 1")
                );
                
                if (!otherVersions.isEmpty()) {
                    // 设置最新版本为当前版本
                    DocumentVersion newCurrent = otherVersions.get(0);
                    newCurrent.setIsCurrent(1);
                    documentVersionMapper.updateById(newCurrent);
                }
            }
            
            // 逻辑删除
            documentVersionMapper.deleteById(id);
            
            log.info("删除文档版本成功: id={}", id);
            return "版本删除成功";
        } catch (Exception e) {
            log.error("删除文档版本失败: id={}", id, e);
            throw new RuntimeException("删除版本失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 比较两个版本
     * @param version1Id 版本1 ID
     * @param version2Id 版本2 ID
     * @return 比较结果
     */
    public String compareVersions(Long version1Id, Long version2Id) {
        DocumentVersion version1 = documentVersionMapper.selectById(version1Id);
        DocumentVersion version2 = documentVersionMapper.selectById(version2Id);
        
        if (version1 == null || version2 == null) {
            throw new RuntimeException("版本不存在");
        }
        
        StringBuilder result = new StringBuilder();
        result.append("版本对比结果:\n");
        result.append(String.format("版本1: V%d, 文件大小: %d bytes, MD5: %s\n", 
                version1.getVersion(), version1.getFileSize(), version1.getFileMd5()));
        result.append(String.format("版本2: V%d, 文件大小: %d bytes, MD5: %s\n", 
                version2.getVersion(), version2.getFileSize(), version2.getFileMd5()));
        
        // 比较MD5
        if (version1.getFileMd5().equals(version2.getFileMd5())) {
            result.append("内容相同: 是\n");
        } else {
            result.append("内容相同: 否\n");
        }
        
        // 比较文件大小
        long sizeDiff = version2.getFileSize() - version1.getFileSize();
        result.append(String.format("文件大小变化: %d bytes\n", sizeDiff));
        
        return result.toString();
    }
    
    /**
     * 计算文件MD5
     * @param filePath 文件路径
     * @return MD5字符串
     */
    private String calculateFileMD5(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("计算文件MD5失败: filePath={}", filePath, e);
            throw new RuntimeException("计算文件MD5失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据MD5查询文档是否已存在
     * @param fileMd5 文件MD5
     * @return 是否存在
     */
    public boolean existsByMd5(String fileMd5) {
        Long count = documentVersionMapper.selectCount(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getFileMd5, fileMd5)
                        .eq(DocumentVersion::getDelFlag, 0)
        );
        return count > 0;
    }
    
    /**
     * 获取所有文档列表
     * @return 文档列表（按documentId去重）
     */
    public List<DocumentVersion> getAllDocuments() {
        return documentVersionMapper.selectList(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getIsCurrent, 1)
                        .eq(DocumentVersion::getDelFlag, 0)
                        .orderByDesc(DocumentVersion::getCreateTime)
        );
    }
}
