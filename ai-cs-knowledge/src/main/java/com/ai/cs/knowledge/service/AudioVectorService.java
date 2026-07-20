package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.AudioProperties;
import com.ai.cs.knowledge.entity.AudioMetadata;
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
 * 音频向量化服务
 * 负责将音频元数据/转录文本生成描述文本，向量化后存入Chroma向量库
 * 支持基于自然语言的音频语义搜索
 */
@Slf4j
@Service
public class AudioVectorService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> audioEmbeddingStore;
    private final AudioProperties audioProperties;

    public AudioVectorService(
            EmbeddingModel embeddingModel,
            @Qualifier("audioEmbeddingStore") EmbeddingStore<TextSegment> audioEmbeddingStore,
            AudioProperties audioProperties) {
        this.embeddingModel = embeddingModel;
        this.audioEmbeddingStore = audioEmbeddingStore;
        this.audioProperties = audioProperties;
    }

    /**
     * 将音频元数据向量化并存入Chroma
     * 基于元数据（文件名、标题、艺术家、时长、格式、转录文本等）生成描述文本，向量化后入库
     *
     * @param metadata 音频元数据
     * @return 向量库中的记录ID
     */
    public String vectorize(AudioMetadata metadata) {
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
            String vectorId = audioEmbeddingStore.add(embedding, segment);

            // 5. 更新元数据的向量化状态
            metadata.setVectorId(vectorId);
            metadata.setVectorized(true);
            metadata.setVectorCollection(audioProperties.getVectorCollection());

            log.info("音频向量化完成: fileId={}, vectorId={}, collection={}", 
                    metadata.getFileId(), vectorId, audioProperties.getVectorCollection());
            return vectorId;

        } catch (Exception e) {
            log.error("音频向量化失败: fileId={}", metadata.getFileId(), e);
            throw new RuntimeException("音频向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量向量化音频元数据
     *
     * @param metadataList 音频元数据列表
     * @return 成功向量化的数量
     */
    public int batchVectorize(List<AudioMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return 0;
        }

        List<TextSegment> segments = new ArrayList<>();
        List<AudioMetadata> validMetadataList = new ArrayList<>();

        for (AudioMetadata metadata : metadataList) {
            try {
                String description = generateDescription(metadata);
                metadata.setVectorDescription(description);

                Map<String, Object> segmentMetadata = buildSegmentMetadata(metadata);
                TextSegment segment = TextSegment.from(description,
                        dev.langchain4j.data.document.Metadata.from(segmentMetadata));

                segments.add(segment);
                validMetadataList.add(metadata);
            } catch (Exception e) {
                log.warn("音频元数据预处理失败: fileId={}, 错误: {}", metadata.getFileId(), e.getMessage());
            }
        }

        if (segments.isEmpty()) {
            return 0;
        }

        try {
            // 批量向量化
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // 批量存入Chroma
            List<String> vectorIds = audioEmbeddingStore.addAll(embeddings, segments);

            // 更新元数据状态
            for (int i = 0; i < validMetadataList.size(); i++) {
                AudioMetadata metadata = validMetadataList.get(i);
                metadata.setVectorId(vectorIds.get(i));
                metadata.setVectorized(true);
                metadata.setVectorCollection(audioProperties.getVectorCollection());
            }

            log.info("批量音频向量化完成: 总数={}, 成功={}", metadataList.size(), vectorIds.size());
            return vectorIds.size();

        } catch (Exception e) {
            log.error("批量音频向量化失败", e);
            throw new RuntimeException("批量音频向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 基于自然语言搜索相似音频
     *
     * @param query 搜索查询文本（如："轻快的背景音乐"、"英语演讲录音"）
     * @param maxResults 最大返回结果数
     * @return 匹配的音频元数据列表
     */
    public List<Map<String, Object>> search(String query, int maxResults) {
        try {
            // 将查询文本向量化
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 在Chroma中进行相似度搜索
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults > 0 ? maxResults : audioProperties.getSearchMaxResults())
                    .minScore(audioProperties.getSearchMinScore())
                    .build();

            EmbeddingSearchResult<TextSegment> result = audioEmbeddingStore.search(searchRequest);

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
                item.put("duration", meta.getString("duration"));
                item.put("artist", meta.getString("artist"));
                item.put("title", meta.getString("title"));
                item.put("album", meta.getString("album"));
                item.put("genre", meta.getString("genre"));

                searchResults.add(item);
            });

            log.info("音频语义搜索完成: query='{}', 返回{}条结果", query, searchResults.size());
            return searchResults;

        } catch (Exception e) {
            log.error("音频语义搜索失败: query={}", query, e);
            throw new RuntimeException("音频语义搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据向量ID删除Chroma中的音频向量
     *
     * @param vectorId 向量记录ID
     */
    public void deleteByVectorId(String vectorId) {
        try {
            if (vectorId != null && !vectorId.isEmpty()) {
                audioEmbeddingStore.remove(vectorId);
                log.info("音频向量删除完成: vectorId={}", vectorId);
            }
        } catch (Exception e) {
            log.error("删除音频向量失败: vectorId={}", vectorId, e);
        }
    }

    /**
     * 更新音频转录文本并重新向量化
     * 当音频转录完成后，使用转录文本增强向量化效果
     *
     * @param metadata 音频元数据（需已包含转录文本）
     * @return 新的向量记录ID
     */
    public String reVectorizeWithTranscription(AudioMetadata metadata) {
        // 删除旧向量
        if (metadata.getVectorId() != null && !metadata.getVectorId().isEmpty()) {
            deleteByVectorId(metadata.getVectorId());
        }
        // 重新向量化（此时description会包含转录文本）
        return vectorize(metadata);
    }

    /**
     * 根据音频元数据生成描述文本
     * 综合文件名、格式、时长、标签信息、转录文本等生成自然语言描述
     */
    private String generateDescription(AudioMetadata metadata) {
        StringBuilder desc = new StringBuilder();

        // 基本信息
        desc.append("音频文件: ").append(metadata.getOriginalFilename());
        desc.append(", 格式: ").append(metadata.getFormat());

        // 时长信息
        if (metadata.getDuration() > 0) {
            desc.append(", 时长: ").append(formatDuration(metadata.getDuration()));
        }

        // 音频质量信息
        if (metadata.getBitrate() > 0) {
            desc.append(", 比特率: ").append(metadata.getBitrate()).append("kbps");
        }
        if (metadata.getSampleRate() > 0) {
            desc.append(", 采样率: ").append(metadata.getSampleRate()).append("Hz");
        }
        if (metadata.getChannels() > 0) {
            desc.append(", 声道: ").append(metadata.getChannels() == 1 ? "单声道" : "立体声");
        }

        // 标签信息
        if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
            desc.append(", 标题: ").append(metadata.getTitle());
        }
        if (metadata.getArtist() != null && !metadata.getArtist().isEmpty()) {
            desc.append(", 艺术家: ").append(metadata.getArtist());
        }
        if (metadata.getAlbum() != null && !metadata.getAlbum().isEmpty()) {
            desc.append(", 专辑: ").append(metadata.getAlbum());
        }
        if (metadata.getGenre() != null && !metadata.getGenre().isEmpty()) {
            desc.append(", 流派: ").append(metadata.getGenre());
        }
        if (metadata.getYear() != null) {
            desc.append(", 年份: ").append(metadata.getYear());
        }

        // 转录文本（最重要的语义信息）
        if (metadata.getTranscription() != null && !metadata.getTranscription().isEmpty()) {
            desc.append(", 转录内容: ").append(metadata.getTranscription());
        }

        return desc.toString();
    }

    /**
     * 格式化时长显示
     */
    private String formatDuration(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);

        if (hours > 0) {
            return String.format("%d小时%d分%d秒", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%d分%d秒", minutes, secs);
        } else {
            return String.format("%d秒", secs);
        }
    }

    /**
     * 构建TextSegment的元数据Map
     */
    private Map<String, Object> buildSegmentMetadata(AudioMetadata metadata) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", "audio");
        meta.put("fileId", metadata.getFileId());
        meta.put("originalFilename", metadata.getOriginalFilename());
        meta.put("storagePath", metadata.getStoragePath());
        meta.put("format", metadata.getFormat());
        meta.put("duration", String.valueOf(metadata.getDuration()));
        meta.put("bitrate", String.valueOf(metadata.getBitrate()));
        meta.put("sampleRate", String.valueOf(metadata.getSampleRate()));
        meta.put("channels", String.valueOf(metadata.getChannels()));
        meta.put("artist", metadata.getArtist() != null ? metadata.getArtist() : "");
        meta.put("title", metadata.getTitle() != null ? metadata.getTitle() : "");
        meta.put("album", metadata.getAlbum() != null ? metadata.getAlbum() : "");
        meta.put("genre", metadata.getGenre() != null ? metadata.getGenre() : "");
        meta.put("fileSize", String.valueOf(metadata.getFileSize()));
        meta.put("uploadTime", metadata.getUploadTime() != null ? metadata.getUploadTime().toString() : "");
        meta.put("waveformPath", metadata.getWaveformPath() != null ? metadata.getWaveformPath() : "");
        return meta;
    }
}
