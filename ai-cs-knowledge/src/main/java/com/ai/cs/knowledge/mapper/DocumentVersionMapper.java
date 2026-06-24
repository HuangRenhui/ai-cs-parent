package com.ai.cs.knowledge.mapper;

import com.ai.cs.knowledge.entity.DocumentVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档版本管理Mapper
 * 
 * @author huangrenhui
 * @date 2026/6/24
 */
@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersion> {
    
    /**
     * 根据文档ID查询所有版本
     * @param documentId 文档ID
     * @return 版本列表
     */
    @Select("SELECT * FROM cs_document_version WHERE document_id = #{documentId} AND del_flag = 0 ORDER BY version DESC")
    List<DocumentVersion> selectVersionsByDocumentId(@Param("documentId") String documentId);
    
    /**
     * 查询文档的当前版本
     * @param documentId 文档ID
     * @return 当前版本
     */
    @Select("SELECT * FROM cs_document_version WHERE document_id = #{documentId} AND is_current = 1 AND del_flag = 0 LIMIT 1")
    DocumentVersion selectCurrentVersion(@Param("documentId") String documentId);
    
    /**
     * 查询文档的最大版本号
     * @param documentId 文档ID
     * @return 最大版本号
     */
    @Select("SELECT MAX(version) FROM cs_document_version WHERE document_id = #{documentId} AND del_flag = 0")
    Integer selectMaxVersion(@Param("documentId") String documentId);
}
