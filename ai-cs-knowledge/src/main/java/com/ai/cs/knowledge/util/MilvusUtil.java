package com.ai.cs.knowledge.util;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.grpc.MutationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:58
 * @description Milvus 连接工具类 & 知识库检索
 * Milvus 提前执行集合创建,用于存储问题向量,此处提供基础增删查代码
 */

@Slf4j
@Component
public class MilvusUtil {
    @Value("${milvus.host}")
    private String host;
    @Value("${milvus.port}")
    private Integer port;

    private MilvusClient client;
    public static final String COLLECTION_NAME = "cs_faq_collection";

    @PostConstruct
    public void init() {
        ConnectParam param = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        client = new MilvusServiceClient(param);
    }

    public MilvusClient getClient() {
        return client;
    }

    /**
     * 向量数据插入
     * @param vector 问题向量
     * @param content 问题内容
     * @return 插入的ID
     */
    public String insert(List<Float> vector, String content) {
        try {
            // 构建字段数据列表
            List<InsertParam.Field> fields = new ArrayList<>();
            
            // 添加embedding向量字段
            fields.add(new InsertParam.Field("embedding", Collections.singletonList(vector)));
            
            // 添加content文本字段（假设集合中有content字段）
            fields.add(new InsertParam.Field("content", Collections.singletonList(content)));
            
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFields(fields)
                    .build();
            
            R<MutationResult> resp = getClient().insert(insertParam);
            
            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus插入失败: {}", resp.getMessage());
                throw new RuntimeException("Milvus插入失败: " + resp.getMessage());
            }
            
            log.info("Milvus插入成功, content={}", content);
            return String.valueOf(resp.getData().getIDs().getIntId().getData(0));
            
        } catch (Exception e) {
            log.error("Milvus插入异常, content={}", content, e);
            throw new RuntimeException("Milvus插入异常: " + e.getMessage(), e);
        }
    }

    /**
     * 批量向量数据插入
     * @param vectors 问题向量列表
     * @param contents 问题内容列表
     * @return 插入的ID列表
     */
    public List<String> batchInsert(List<List<Float>> vectors, List<String> contents) {
        try {
            // 构建字段数据列表
            List<InsertParam.Field> fields = new ArrayList<>();
            
            // 添加embedding向量字段
            fields.add(new InsertParam.Field("embedding", vectors));
            
            // 添加content文本字段
            fields.add(new InsertParam.Field("content", contents));
            
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFields(fields)
                    .build();
            
            R<MutationResult> resp = getClient().insert(insertParam);
            
            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus批量插入失败: {}", resp.getMessage());
                throw new RuntimeException("Milvus批量插入失败: " + resp.getMessage());
            }
            
            List<String> ids = new ArrayList<>();
            for (Long id : resp.getData().getIDs().getIntId().getDataList()) {
                ids.add(String.valueOf(id));
            }
            
            log.info("Milvus批量插入成功, 数量={}", ids.size());
            return ids;
            
        } catch (Exception e) {
            log.error("Milvus批量插入异常", e);
            throw new RuntimeException("Milvus批量插入异常: " + e.getMessage(), e);
        }
    }

    /**
     * 根据ID删除Milvus中的向量数据
     * @param milvusId Milvus中的记录ID
     * @return 是否删除成功
     */
    public boolean deleteById(String milvusId) {
        try {
            if (milvusId == null || milvusId.isEmpty()) {
                log.warn("Milvus ID为空，无法删除");
                return false;
            }
            
            // 构建删除表达式
            String expr = "id in [" + milvusId + "]";
            
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withExpr(expr)
                    .build();
            
            R<MutationResult> resp = getClient().delete(deleteParam);
            
            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus删除失败: {}", resp.getMessage());
                return false;
            }
            
            log.info("Milvus删除成功, milvusId={}", milvusId);
            return true;
            
        } catch (Exception e) {
            log.error("Milvus删除异常, milvusId={}", milvusId, e);
            return false;
        }
    }

    /**
     * 批量删除Milvus中的向量数据
     * @param milvusIds Milvus中的记录ID列表
     * @return 删除成功的数量
     */
    public int batchDelete(List<String> milvusIds) {
        if (milvusIds == null || milvusIds.isEmpty()) {
            return 0;
        }
        
        try {
            // 构建删除表达式
            String idsStr = String.join(",", milvusIds);
            String expr = "id in [" + idsStr + "]";
            
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withExpr(expr)
                    .build();
            
            R<MutationResult> resp = getClient().delete(deleteParam);
            
            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus批量删除失败: {}", resp.getMessage());
                return 0;
            }
            
            int deleteCount = (int) resp.getData().getDeleteCnt();
            log.info("Milvus批量删除成功, 删除数量={}", deleteCount);
            return deleteCount;
            
        } catch (Exception e) {
            log.error("Milvus批量删除异常", e);
            return 0;
        }
    }

    /**
     * 向量语义检索
     * @param vector 问题向量
     * @param topK 返回文档数量
     * @return 匹配文档的content字段列表
     */
    public List<String> search(List<Float> vector, int topK) {
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withVectors(Collections.singletonList(vector))
                    .withVectorFieldName("embedding")
                    .withTopK(topK)
                    .withOutFields(Collections.singletonList("content"))
                    .build();
            
            R<SearchResults> resp = getClient().search(searchParam);
            
            // 检查响应状态
            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus搜索失败: {}", resp.getMessage());
                throw new RuntimeException("Milvus搜索失败: " + resp.getMessage());
            }
            
            SearchResults results = resp.getData();
            List<String> contents = new ArrayList<>();
            
            // 解析搜索结果
            if (results != null && results.getResults() != null) {
                SearchResultData resultData = results.getResults();
                
                // 获取fields数据
                if (resultData.getFieldsDataCount() > 0) {
                    // 遍历所有返回的结果
                    for (int i = 0; i < resultData.getScoresCount(); i++) {
                        // 从fields中获取content字段
                        // 注意：这里需要根据实际的Milvus集合结构来解析
                        // 简化实现：返回占位符，实际使用时需要根据具体结构调整
                        String content = extractContent(resultData, i);
                        if (content != null && !content.isEmpty()) {
                            contents.add(content);
                        }
                    }
                }
            }
            
            log.info("Milvus搜索完成，返回{}条结果", contents.size());
            return contents;
            
        } catch (Exception e) {
            log.error("Milvus搜索异常", e);
            throw new RuntimeException("Milvus搜索异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从搜索结果中提取content字段
     * @param resultData 搜索结果数据
     * @param index 结果索引
     * @return content内容
     */
    private String extractContent(io.milvus.grpc.SearchResultData resultData, int index) {
        try {
            // 遍历所有字段数据
            for (int fieldIdx = 0; fieldIdx < resultData.getFieldsDataCount(); fieldIdx++) {
                io.milvus.grpc.FieldData fieldData = resultData.getFieldsData(fieldIdx);
                
                // 查找名为"content"的字段
                if ("content".equals(fieldData.getFieldName())) {
                    // 根据字段类型获取值
                    switch (fieldData.getType()) {
                        case VarChar:
                        case String:
                            if (fieldData.getScalars() != null && 
                                fieldData.getScalars().getStringData() != null) {
                                return fieldData.getScalars().getStringData().getData(index);
                            }
                            break;
                        default:
                            log.warn("不支持的字段类型: {}", fieldData.getType());
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取content字段失败, index={}", index, e);
        }
        return null;
    }
}