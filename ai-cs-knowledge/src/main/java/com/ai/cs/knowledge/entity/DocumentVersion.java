package com.ai.cs.knowledge.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 文档版本管理实体
 * 用于跟踪文档的版本历史，支持版本回退和对比
 * 
 * @author huangrenhui
 * @date 2026/6/24
 */
@Data
@TableName("cs_document_version")
public class DocumentVersion extends BaseEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 文档唯一标识（文件名或文档ID）
     */
    private String documentId;
    
    /**
     * 文档名称
     */
    private String documentName;
    
    /**
     * 版本号
     */
    private Integer version;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件类型（PDF/TXT/DOCX/MD等）
     */
    private String fileType;
    
    /**
     * 文件MD5哈希值，用于内容去重
     */
    private String fileMd5;
    
    /**
     * Milvus向量集合ID
     */
    private String milvusCollectionId;
    
    /**
     * 文档片段数量
     */
    private Integer segmentCount;
    
    /**
     * 版本描述
     */
    private String versionDescription;
    
    /**
     * 是否为当前版本
     */
    private Integer isCurrent;
    
    /**
     * 状态：0-草稿，1-已发布，2-已归档
     */
    private Integer status;
    
    /**
     * 上传人ID
     */
    private Long uploaderId;
    
    /**
     * 上传人姓名
     */
    private String uploaderName;
    
    @TableLogic
    private Integer delFlag;
}
