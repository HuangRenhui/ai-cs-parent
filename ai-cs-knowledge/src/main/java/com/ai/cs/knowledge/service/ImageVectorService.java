package com.ai.cs.knowledge.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.knowledge.config.ImageProperties;
import com.ai.cs.knowledge.entity.ImageMetadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片向量化服务
 * 负责将图片元数据生成描述文本，向量化后存入Chroma向量库
 * 支持基于自然语言的图片语义搜索
 */
@Slf4j
@Service
public class ImageVectorService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> imageEmbeddingStore;
    private final ImageProperties imageProperties;

    public ImageVectorService(
            EmbeddingModel embeddingModel,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageEmbeddingStore,
            ImageProperties imageProperties) {
        this.embeddingModel = embeddingModel;
        this.imageEmbeddingStore = imageEmbeddingStore;
        this.imageProperties = imageProperties;
    }

    /**
     * 将图片元数据向量化并存入Chroma
     * 基于元数据（文件名、尺寸、格式、EXIF信息等）生成描述文本，向量化后入库
     *
     * @param metadata 图片元数据
     * @return 向量库中的记录ID
     */
    public String vectorize(ImageMetadata metadata) {
        try {
            // 1. 生成描述文本
            String description = generateDescription(metadata);
            metadata.setVectorDescription(description);

            // 2. 创建TextSegment并附加元数据
            Map<String, Object> segmentMetadata = buildSegmentMetadata(metadata);
            TextSegment segment = TextSegment.from(description, 
                    dev.langchain4j.data.document.Metadata.from(segmentMetadata));

            // 3. 向量化
            Embedding embedding = embeddingModel.embed(segment).content();

            // 4. 存入Chroma向量库
            String vectorId = imageEmbeddingStore.add(embedding, segment);

            // 5. 更新元数据的向量化状态
            metadata.setVectorId(vectorId);
            metadata.setVectorized(true);
            metadata.setVectorCollection(imageProperties.getVectorCollection());

            log.info("图片向量化完成: fileId={}, vectorId={}, collection={}", 
                    metadata.getFileId(), vectorId, imageProperties.getVectorCollection());
            return vectorId;

        } catch (Exception e) {
            log.error("图片向量化失败: fileId={}", metadata.getFileId(), e);
            throw new BusinessException(503, "图片向量化失败: " + e.getMessage());
        }
    }

    /**
     * 批量向量化图片元数据
     *
     * @param metadataList 图片元数据列表
     * @return 成功向量化的数量
     */
    public int batchVectorize(List<ImageMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return 0;
        }

        List<TextSegment> segments = new ArrayList<>();
        List<ImageMetadata> validMetadataList = new ArrayList<>();

        for (ImageMetadata metadata : metadataList) {
            try {
                String description = generateDescription(metadata);
                metadata.setVectorDescription(description);

                Map<String, Object> segmentMetadata = buildSegmentMetadata(metadata);
                TextSegment segment = TextSegment.from(description,
                        dev.langchain4j.data.document.Metadata.from(segmentMetadata));

                segments.add(segment);
                validMetadataList.add(metadata);
            } catch (Exception e) {
                log.warn("图片元数据预处理失败: fileId={}, 错误: {}", metadata.getFileId(), e.getMessage());
            }
        }

        if (segments.isEmpty()) {
            return 0;
        }

        try {
            // 批量向量化
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // 批量存入Chroma
            List<String> vectorIds = imageEmbeddingStore.addAll(embeddings, segments);

            // 更新元数据状态
            for (int i = 0; i < validMetadataList.size(); i++) {
                ImageMetadata metadata = validMetadataList.get(i);
                metadata.setVectorId(vectorIds.get(i));
                metadata.setVectorized(true);
                metadata.setVectorCollection(imageProperties.getVectorCollection());
            }

            log.info("批量图片向量化完成: 总数={}, 成功={}", metadataList.size(), vectorIds.size());
            return vectorIds.size();

        } catch (Exception e) {
            log.error("批量图片向量化失败", e);
            throw new BusinessException(503, "批量图片向量化失败: " + e.getMessage());
        }
    }

    /**
     * 基于自然语言搜索相似图片
     *
     * @param query 搜索查询文本（如："海边日落的照片"）
     * @param maxResults 最大返回结果数
     * @return 匹配的图片元数据列表
     */
    public List<Map<String, Object>> search(String query, int maxResults) {
        try {
            // 将查询文本向量化
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 在Chroma中进行相似度搜索
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults > 0 ? maxResults : imageProperties.getSearchMaxResults())
                    .minScore(imageProperties.getSearchMinScore())
                    .build();

            EmbeddingSearchResult<TextSegment> result = imageEmbeddingStore.search(searchRequest);

            // 解析搜索结果
            List<Map<String, Object>> searchResults = new ArrayList<>();
            result.matches().forEach(match -> {
                Map<String, Object> item = new HashMap<>();
                item.put("score", match.score());
                item.put("description", match.embedded().text());
                
                // 提取元数据
                dev.langchain4j.data.document.Metadata meta = match.embedded().metadata();
                item.put("fileId", meta.getString("fileId"));
                item.put("originalFilename", meta.getString("originalFilename"));
                item.put("storagePath", meta.getString("storagePath"));
                item.put("format", meta.getString("format"));
                item.put("width", meta.getString("width"));
                item.put("height", meta.getString("height"));

                searchResults.add(item);
            });

            log.info("图片语义搜索完成: query='{}', 返回{}条结果", query, searchResults.size());
            return searchResults;

        } catch (Exception e) {
            log.error("图片语义搜索失败: query={}", query, e);
            throw new BusinessException(503, "图片语义搜索失败: " + e.getMessage());
        }
    }

    /**
     * 根据向量ID删除Chroma中的图片向量
     *
     * @param vectorId 向量记录ID
     */
    public void deleteByVectorId(String vectorId) {
        try {
            if (vectorId != null && !vectorId.isEmpty()) {
                imageEmbeddingStore.remove(vectorId);
                log.info("图片向量删除完成: vectorId={}", vectorId);
            }
        } catch (Exception e) {
            log.error("删除图片向量失败: vectorId={}", vectorId, e);
        }
    }

    /**
     * 根据图片元数据生成描述文本
     * 综合文件名、格式、尺寸、EXIF信息等生成自然语言描述
     */
    private String generateDescription(ImageMetadata metadata) {
        StringBuilder desc = new StringBuilder();

        // 基本信息
        desc.append("图片文件: ").append(metadata.getOriginalFilename());
        desc.append(", 格式: ").append(metadata.getFormat());

        if (metadata.getWidth() > 0 && metadata.getHeight() > 0) {
            desc.append(", 尺寸: ").append(metadata.getWidth()).append("x").append(metadata.getHeight());
        }

        // EXIF信息增强描述
        if (metadata.getDescription() != null && !metadata.getDescription().isEmpty()) {
            desc.append(", 描述: ").append(metadata.getDescription());
        }

        if (metadata.getCameraMake() != null && !metadata.getCameraMake().isEmpty()) {
            desc.append(", 相机: ").append(metadata.getCameraMake());
            if (metadata.getCameraModel() != null) {
                desc.append(" ").append(metadata.getCameraModel());
            }
        }

        if (metadata.getDateTimeOriginal() != null) {
            desc.append(", 拍摄时间: ").append(metadata.getDateTimeOriginal());
        }

        if (metadata.getAperture() != null && !metadata.getAperture().isEmpty()) {
            desc.append(", 光圈: f/").append(metadata.getAperture());
        }

        if (metadata.getIso() != null) {
            desc.append(", ISO: ").append(metadata.getIso());
        }

        if (metadata.getFocalLength() != null) {
            desc.append(", 焦距: ").append(metadata.getFocalLength()).append("mm");
        }

        // GPS位置信息
        if (metadata.getGpsLatitude() != null && metadata.getGpsLongitude() != null) {
            desc.append(", 拍摄位置: ").append(String.format("%.4f, %.4f", 
                    metadata.getGpsLatitude(), metadata.getGpsLongitude()));
        }

        if (metadata.getCopyright() != null && !metadata.getCopyright().isEmpty()) {
            desc.append(", 版权: ").append(metadata.getCopyright());
        }

        return desc.toString();
    }

    /**
     * 构建TextSegment的元数据Map
     */
    private Map<String, Object> buildSegmentMetadata(ImageMetadata metadata) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "image");
        meta.put("fileId", metadata.getFileId());
        meta.put("originalFilename", metadata.getOriginalFilename());
        meta.put("storagePath", metadata.getStoragePath());
        meta.put("thumbnailPath", metadata.getThumbnailPath() != null ? metadata.getThumbnailPath() : "");
        meta.put("format", metadata.getFormat());
        meta.put("width", String.valueOf(metadata.getWidth()));
        meta.put("height", String.valueOf(metadata.getHeight()));
        meta.put("fileSize", String.valueOf(metadata.getFileSize()));
        meta.put("uploadTime", metadata.getUploadTime() != null ? metadata.getUploadTime().toString() : "");
        return meta;
    }
}