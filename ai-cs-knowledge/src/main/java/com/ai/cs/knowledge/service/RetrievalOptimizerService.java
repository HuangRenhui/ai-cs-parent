package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索召回优化服务
 * 解决"冗余文档干扰答案"和"上下文过长超限"问题
 * <p>
 * 功能：
 * 1. 文档质量过滤：过滤低质、过短、重复率高的文档
 * 2. 相似度阈值动态过滤：根据向量相似度/BMR分数过滤不相关文档
 * 3. 召回数量限制：控制最终送入LLM的文档数量
 * 4. 上下文摘要压缩：对过长上下文进行摘要压缩
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalOptimizerService {

    private final RagProperties ragProperties;

    /**
     * 检索结果条目
     */
    public static class RetrievalItem {
        public String id;
        public String content;
        public double vectorScore;   // 向量相似度分数
        public double bm25Score;     // BM25关键词分数
        public double combinedScore; // 综合分数
        public String sourceType;    // faq / document / image_knowledge / audio_knowledge
        public Map<String, Object> metadata;

        public RetrievalItem(String id, String content, double vectorScore) {
            this.id = id;
            this.content = content;
            this.vectorScore = vectorScore;
            this.combinedScore = vectorScore;
            this.metadata = new HashMap<>();
        }
    }

    // ==================== 质量过滤 ====================

    /**
     * 质量过滤：移除低质文档
     * <p>
     * 过滤规则：
     * - 内容过短（小于 minContentLength）
     * - 纯数字/纯符号（信息量低）
     * - 重复率过高（大于 maxDuplicateRatio）
     *
     * @param items 原始检索结果
     * @return 过滤后的结果
     */
    public List<RetrievalItem> filterByQuality(List<RetrievalItem> items) {
        RagProperties.Retrieve props = ragProperties.getRetrieve();

        if (!Boolean.TRUE.equals(props.getQualityFilterEnabled()) || items == null || items.isEmpty()) {
            return items != null ? items : Collections.emptyList();
        }

        List<RetrievalItem> filtered = new ArrayList<>();
        Set<String> seenContent = new HashSet<>();

        for (RetrievalItem item : items) {
            if (item.content == null) continue;

            // 规则1：内容过短过滤
            if (item.content.trim().length() < props.getMinContentLength()) {
                log.debug("过滤低质文档（内容过短）: id={}, length={}", item.id, item.content.length());
                continue;
            }

            // 规则2：纯数字/纯符号过滤
            String trimmed = item.content.trim();
            double alphaRatio = countAlphaNum(trimmed) / (double) Math.max(trimmed.length(), 1);
            if (alphaRatio < 0.3) {
                log.debug("过滤低质文档（信息量低）: id={}, alphaRatio={}", item.id, alphaRatio);
                continue;
            }

            // 规则3：重复内容去重
            String normalized = normalize(item.content);
            if (seenContent.contains(normalized)) {
                log.debug("过滤重复文档: id={}", item.id);
                continue;
            }

            // 规则4：高重复率过滤（字符级重复度检测）
            double duplicateRatio = calculateDuplicateRatio(item.content);
            if (duplicateRatio > props.getMaxDuplicateRatio()) {
                log.debug("过滤高重复率文档: id={}, duplicateRatio={}", item.id, duplicateRatio);
                continue;
            }

            seenContent.add(normalized);
            filtered.add(item);
        }

        log.info("质量过滤: {} 条 → {} 条 (过滤 {} 条)",
                items.size(), filtered.size(), items.size() - filtered.size());
        return filtered;
    }

    // ==================== 相似度阈值过滤 ====================

    /**
     * 相似度阈值过滤：移除向量相似度过低的文档
     *
     * @param items 检索结果列表
     * @return 过滤后的结果
     */
    public List<RetrievalItem> filterBySimilarity(List<RetrievalItem> items) {
        RagProperties.Retrieve props = ragProperties.getRetrieve();

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<RetrievalItem> filtered = items.stream()
                .filter(item -> item.vectorScore >= props.getVectorMinScore())
                .collect(Collectors.toList());

        log.debug("相似度过滤: {} 条 → {} 条 (阈值={})",
                items.size(), filtered.size(), props.getVectorMinScore());
        return filtered;
    }

    /**
     * BM25分数过滤：移除关键词匹配度低的文档
     */
    public List<RetrievalItem> filterByBM25(List<RetrievalItem> items) {
        RagProperties.Retrieve props = ragProperties.getRetrieve();

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<RetrievalItem> filtered = items.stream()
                .filter(item -> item.bm25Score >= props.getBm25MinScore())
                .collect(Collectors.toList());

        log.debug("BM25过滤: {} 条 → {} 条 (阈值={})",
                items.size(), filtered.size(), props.getBm25MinScore());
        return filtered;
    }

    // ==================== 召回数量限制 ====================

    /**
     * 限制召回文档数量（控制上下文长度）
     * 取综合分数最高的前 N 条
     *
     * @param items 排序后的检索结果
     * @return 截断后的结果
     */
    public List<RetrievalItem> limitRecallCount(List<RetrievalItem> items) {
        RagProperties.Retrieve props = ragProperties.getRetrieve();

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // 按综合分数降序排列
        items.sort((a, b) -> Double.compare(b.combinedScore, a.combinedScore));

        int limit = props.getMaxRecallDocs();
        if (items.size() <= limit) {
            return items;
        }

        List<RetrievalItem> limited = items.subList(0, limit);
        log.info("召回数量限制: {} 条 → {} 条", items.size(), limited.size());
        return limited;
    }

    // ==================== 上下文摘要压缩 ====================

    /**
     * 上下文压缩：对过长上下文进行摘要压缩
     * <p>
     * 策略：
     * - 如果上下文总长度 > maxContextLength，按分数截断
     * - 对排名靠后的文档，截取首尾关键句保留语义
     *
     * @param items 检索结果列表
     * @return 压缩后的结果
     */
    public List<RetrievalItem> compressContext(List<RetrievalItem> items) {
        RagProperties.Retrieve props = ragProperties.getRetrieve();

        if (!Boolean.TRUE.equals(props.getSummaryCompressEnabled())
                || items == null || items.isEmpty()) {
            return items;
        }

        int totalLength = items.stream().mapToInt(item -> item.content.length()).sum();

        if (totalLength <= props.getMaxContextLength()) {
            return items; // 无需压缩
        }

        log.info("上下文过长触发压缩: totalLength={}, maxLength={}", totalLength, props.getMaxContextLength());

        // 策略：按分数排序，优先保留高分文档的完整内容
        items.sort((a, b) -> Double.compare(b.combinedScore, a.combinedScore));

        List<RetrievalItem> compressed = new ArrayList<>();
        int currentLength = 0;
        int targetLength = props.getCompressTargetLength();

        for (int i = 0; i < items.size(); i++) {
            RetrievalItem item = items.get(i);
            int remaining = targetLength - currentLength;

            if (remaining <= 0) break;

            if (i == 0) {
                // 最高分文档：完整保留
                compressed.add(item);
                currentLength += item.content.length();
            } else if (item.content.length() <= remaining) {
                // 能完整放入：完整保留
                compressed.add(item);
                currentLength += item.content.length();
            } else if (remaining > 100) {
                // 不能完整放入但还有空间：截取首尾关键句
                String truncated = truncateWithKeyInfo(item.content, remaining);
                RetrievalItem truncatedItem = new RetrievalItem(item.id, truncated, item.vectorScore);
                truncatedItem.bm25Score = item.bm25Score;
                truncatedItem.combinedScore = item.combinedScore;
                truncatedItem.sourceType = item.sourceType;
                truncatedItem.metadata = item.metadata;
                compressed.add(truncatedItem);
                currentLength += truncated.length();
            }
            // 剩余空间太小，跳过后续文档
        }

        log.info("上下文压缩完成: {} 条({} chars) → {} 条({} chars)",
                items.size(), totalLength, compressed.size(), currentLength);
        return compressed;
    }

    /**
     * 截取文本的首尾关键信息
     * 保留开头（主题）和结尾（结论）
     */
    private String truncateWithKeyInfo(String text, int maxLength) {
        if (text.length() <= maxLength) return text;

        int headLen = (int) (maxLength * 0.6); // 60%给开头
        int tailLen = maxLength - headLen - 10; // 40%给结尾，减10给省略号

        String head = text.substring(0, headLen);
        // 在句子边界截断
        int lastPeriod = Math.max(head.lastIndexOf('。'), head.lastIndexOf('.'));
        if (lastPeriod > headLen * 0.5) {
            head = text.substring(0, lastPeriod + 1);
            headLen = head.length();
            tailLen = maxLength - headLen - 10;
        }

        if (tailLen <= 0) return head;

        String tail = text.substring(text.length() - tailLen);
        // 在句子边界截断
        int firstPeriod = Math.min(
                tail.indexOf('。') > 0 ? tail.indexOf('。') : Integer.MAX_VALUE,
                tail.indexOf('.') > 0 ? tail.indexOf('.') : Integer.MAX_VALUE
        );
        if (firstPeriod < Integer.MAX_VALUE && firstPeriod < tail.length() - 1) {
            tail = tail.substring(firstPeriod + 1);
        }

        return head + "\n... [已压缩] ...\n" + tail;
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算文本中字母数字字符的比例
     */
    private double countAlphaNum(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || isChineseChar(c)) {
                count++;
            }
        }
        return count;
    }

    private boolean isChineseChar(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    /**
     * 计算文本的字符级重复率
     * 使用滑动窗口检测重复模式
     */
    private double calculateDuplicateRatio(String text) {
        if (text == null || text.length() < 10) return 0.0;

        int windowSize = 5;
        Set<String> windows = new HashSet<>();
        int duplicateCount = 0;
        int totalWindows = 0;

        for (int i = 0; i <= text.length() - windowSize; i++) {
            String window = text.substring(i, i + windowSize);
            totalWindows++;
            if (!windows.add(window)) {
                duplicateCount++;
            }
        }

        return totalWindows > 0 ? (double) duplicateCount / totalWindows : 0.0;
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
