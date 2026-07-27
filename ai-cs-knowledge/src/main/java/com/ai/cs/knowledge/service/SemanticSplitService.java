package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.RagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 语义分层切片服务
 * 解决"切片不合理、检索不准、答非所问"问题
 * <p>
 * 策略：
 * 1. 语义切片：按段落、标题等自然语义边界切分，而非机械按字符数
 * 2. 分层切片：文档→段落→句子→词语，多粒度保留上下文
 * 3. 保留重叠区上下文，避免关键信息被切断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSplitService {

    private final RagProperties ragProperties;

    /**
     * 语义分层切片入口
     * 根据配置自动选择切片策略
     *
     * @param document 原始文档
     * @return 切片后的文本段列表
     */
    public List<TextSegment> split(Document document) {
        RagProperties.Split splitProps = ragProperties.getSplit();

        if (Boolean.TRUE.equals(splitProps.getHierarchicalSplitEnabled())) {
            return hierarchicalSplit(document, splitProps);
        } else if (Boolean.TRUE.equals(splitProps.getSemanticSplitEnabled())) {
            return semanticSplit(document, splitProps);
        } else {
            // 降级：使用 LangChain4j 递归分割
            var splitter = DocumentSplitters.recursive(
                    splitProps.getChunkSize(),
                    splitProps.getChunkOverlap()
            );
            return splitter.split(document);
        }
    }

    /**
     * 分层切片：文档 → 段落 → 句子 → 词语
     * 多粒度切分，每个 chunk 同时保留粗粒度和细粒度信息
     */
    private List<TextSegment> hierarchicalSplit(Document document, RagProperties.Split props) {
        String text = document.text();
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<TextSegment> segments = new ArrayList<>();
        Set<String> seenContent = new HashSet<>();

        // 第一层：按段落切分（大块，保留完整上下文）
        String[] paragraphs = text.split("\n{2,}");
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.length() < props.getMinChunkLength()) {
                continue; // 太短的段落丢弃
            }

            if (paragraph.length() <= props.getParagraphMaxLength()) {
                // 段落长度适中，直接作为一个 chunk
                if (seenContent.add(normalize(paragraph))) {
                    segments.add(TextSegment.from(paragraph));
                }
            } else {
                // 段落太长，第二层：按句子切分
                List<String> sentences = splitSentences(paragraph, props);
                StringBuilder chunkBuilder = new StringBuilder();
                int currentLength = 0;

                for (String sentence : sentences) {
                    if (sentence.length() < props.getMinChunkLength()) {
                        continue;
                    }

                    if (currentLength + sentence.length() > props.getSentenceMaxLength()
                            && chunkBuilder.length() > 0) {
                        // 当前 chunk 满了，保存并开始新 chunk（带重叠）
                        String chunk = chunkBuilder.toString().trim();
                        if (chunk.length() >= props.getMinChunkLength()
                                && seenContent.add(normalize(chunk))) {
                            segments.add(TextSegment.from(chunk));
                        }
                        // 重叠：保留最后一句作为上下文
                        chunkBuilder = new StringBuilder();
                        currentLength = 0;
                    }

                    chunkBuilder.append(sentence);
                    currentLength += sentence.length();
                }

                // 保存最后一个 chunk
                String lastChunk = chunkBuilder.toString().trim();
                if (lastChunk.length() >= props.getMinChunkLength()
                        && seenContent.add(normalize(lastChunk))) {
                    segments.add(TextSegment.from(lastChunk));
                }
            }
        }

        log.info("分层切片完成: 原始长度={}, 生成{}个分段", text.length(), segments.size());
        return segments;
    }

    /**
     * 语义切片：按自然语义边界切分
     * 使用段落、句子、标点等作为语义分隔符
     */
    private List<TextSegment> semanticSplit(Document document, RagProperties.Split props) {
        String text = document.text();
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<TextSegment> segments = new ArrayList<>();
        Set<String> seenContent = new HashSet<>();

        // 解析分隔符配置
        String[] separators = props.getSemanticSeparators().split(",");
        String regexPattern = buildSeparatorRegex(separators);

        // 先按大分隔符（段落）切分
        String[] largeChunks = text.split("\n{2,}");
        for (String largeChunk : largeChunks) {
            largeChunk = largeChunk.trim();
            if (largeChunk.isEmpty()) continue;

            // 如果大块超过限制，按小分隔符继续切
            if (largeChunk.length() > props.getChunkSize()) {
                List<String> subChunks = splitByPattern(largeChunk, regexPattern,
                        props.getChunkSize(), props.getChunkOverlap(), props.getMinChunkLength());
                for (String sub : subChunks) {
                    if (seenContent.add(normalize(sub))) {
                        segments.add(TextSegment.from(sub));
                    }
                }
            } else if (largeChunk.length() >= props.getMinChunkLength()) {
                if (seenContent.add(normalize(largeChunk))) {
                    segments.add(TextSegment.from(largeChunk));
                }
            }
        }

        log.info("语义切片完成: 原始长度={}, 生成{}个分段", text.length(), segments.size());
        return segments;
    }

    /**
     * 按语义分隔模式切分文本（带重叠）
     */
    private List<String> splitByPattern(String text, String regexPattern,
                                         int maxLen, int overlapLen, int minLen) {
        List<String> result = new ArrayList<>();
        String[] parts = text.split(regexPattern);

        StringBuilder chunk = new StringBuilder();
        for (String part : parts) {
            part = part.trim();
            if (part.length() < minLen) continue;

            if (chunk.length() + part.length() > maxLen && chunk.length() > 0) {
                result.add(chunk.toString().trim());
                // 重叠：保留最后 overlapLen 个字符作为上下文
                String overlap = chunk.length() > overlapLen
                        ? chunk.substring(Math.max(0, chunk.length() - overlapLen))
                        : "";
                chunk = new StringBuilder(overlap);
            }
            chunk.append(part);
        }

        if (chunk.length() >= minLen) {
            result.add(chunk.toString().trim());
        }
        return result;
    }

    /**
     * 按句子切分（中英文混合）
     */
    private List<String> splitSentences(String text, RagProperties.Split props) {
        List<String> sentences = new ArrayList<>();
        // 中英文句子分隔符
        Pattern sentencePattern = Pattern.compile(
                "[^。！？；!?;\\n]+[。！？；!?;\\n]?"
        );
        var matcher = sentencePattern.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.length() >= props.getMinChunkLength()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    /**
     * 构建语义分隔符正则
     */
    private String buildSeparatorRegex(String[] separators) {
        StringBuilder regex = new StringBuilder("(");
        for (int i = 0; i < separators.length; i++) {
            if (i > 0) regex.append("|");
            String sep = separators[i].trim();
            if (!sep.isEmpty()) {
                regex.append(Pattern.quote(sep));
            }
        }
        regex.append(")");
        return regex.toString();
    }

    /**
     * 文本归一化（用于去重比较）
     */
    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
