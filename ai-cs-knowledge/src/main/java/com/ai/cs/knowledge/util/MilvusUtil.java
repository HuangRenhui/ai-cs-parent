package com.ai.cs.knowledge.util;

import com.ai.cs.knowledge.config.MilvusProperties;
import com.alibaba.fastjson.JSONObject;
import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.CheckHealthResponse;
import io.milvus.grpc.DataType;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Milvus 向量库工具（FAQ 与文档切片两套租户集合）
 *
 * <p><b>租户隔离：每类向量一租户一集合</b>，集合名为
 * {@code 基础集合名 + "_" + 归一化租户 + "_" + 短哈希}。两类向量各用一套集合：</p>
 * <ul>
 *   <li><b>FAQ 集合</b>（{@code milvus.collection-name}）：主键 {@code faq_id}(Int64)，
 *       字段 {@code tenant_id/content/embedding}，按 faqId 幂等 upsert；</li>
 *   <li><b>文档切片集合</b>（{@code milvus.chunk-collection-name}）：主键 {@code chunk_id}(Int64)，
 *       字段 {@code tenant_id/document_id/chunk_index/content/embedding}，按 chunkId 幂等 upsert。</li>
 * </ul>
 * <p>集合在首次写入时懒建（建集合 + 建 IVF_FLAT / COSINE 索引 + load）。</p>
 *
 * <p><b>失败语义</b>：连接不可用、向量维度不符、Milvus RPC 失败一律抛异常
 * （{@link IllegalStateException} / {@link IllegalArgumentException}），由上层
 * {@code RagSearchService} 转「服务不可用」三态；<b>集合不存在视为未命中</b>，返回空列表。</p>
 *
 * <p><b>待清理登记</b>：删除向量失败时经 {@link #markPendingDelete} 把
 * {@code 业务类型:租户:id} 记入 Redis Set（{@value #PENDING_DELETE_KEY}），
 * 交由「向量对账」任务补偿清理，避免脏向量只在日志里静默累积。</p>
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class MilvusUtil {

    /** 待清理向量的 Redis Set：成员格式 {@code 业务类型:归一化租户:id} */
    public static final String PENDING_DELETE_KEY = "kb:vector:pending-delete";

    /** 待清理业务类型：FAQ 向量 */
    public static final String BIZ_TYPE_FAQ = "faq";
    /** 待清理业务类型：文档切片向量 */
    public static final String BIZ_TYPE_CHUNK = "chunk";

    /** 向量化状态查询单次最多允许的 id 个数（防超长表达式） */
    private static final int STATUS_QUERY_MAX_IDS = 500;

    /** Milvus服务地址 */
    @Value("${milvus.host}")
    private String host;

    /** Milvus服务端口 */
    @Value("${milvus.port}")
    private Integer port;

    /** Milvus客户端连接 */
    private MilvusClient client;

    /** 集合字段：FAQ主键ID */
    public static final String FIELD_FAQ_ID = "faq_id";
    /** 集合字段：文档切片主键ID */
    public static final String FIELD_CHUNK_ID = "chunk_id";
    /** 集合字段：租户ID */
    public static final String FIELD_TENANT_ID = "tenant_id";
    /** 集合字段：来源文档ID */
    public static final String FIELD_DOCUMENT_ID = "document_id";
    /** 集合字段：切片序号（文档内顺序） */
    public static final String FIELD_CHUNK_INDEX = "chunk_index";
    /** 集合字段：Embedding向量 */
    public static final String FIELD_EMBEDDING = "embedding";
    /** 集合字段：文本内容 */
    public static final String FIELD_CONTENT = "content";

    /** 已确认「存在」的集合名缓存：避免每次写入/检索都打一次 hasCollection */
    private final Set<String> readyCollections = ConcurrentHashMap.newKeySet();
    /** 建集合串行锁：只锁首次创建，不串行化并发检索 */
    private final Object collectionLock = new Object();

    /** 连接是否可用（供 isReady()/statusMessage() 用） */
    private volatile boolean ready = false;
    /** 最近一次失败原因（供 statusMessage() 用） */
    private volatile String lastError;

    /** 允许无 Redis 环境启动：无 Redis 时只记日志，不影响向量主链路 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Resource
    private MilvusProperties milvusProperties;

    // ==================== 生命周期 ====================

    /**
     * 初始化 Milvus 客户端连接（懒建连接，需真实探测一次才算就绪）
     */
    @PostConstruct
    public void init() {
        try {
            client = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(host).withPort(port).build());
            R<CheckHealthResponse> health = client.checkHealth();
            if (health.getStatus() != R.Status.Success.getCode()) {
                ready = false;
                lastError = health.getMessage();
                client = null;
                log.warn("Milvus 健康检查失败 {}:{} reason={}", host, port, lastError);
                return;
            }
            ready = true;
            readyCollections.clear();
            log.info("Milvus 连接成功 {}:{}", host, port);
        } catch (Exception e) {
            ready = false;
            client = null;
            lastError = e.getMessage();
            log.warn("Milvus 连接失败 host={}:{} reason={}", host, port, lastError);
        }
    }

    /**
     * gRPC 长连接需要显式关闭
     */
    @PreDestroy
    public void destroy() {
        ready = false;
        readyCollections.clear();
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Milvus 客户端关闭异常: {}", e.getMessage());
            }
        }
    }

    // ==================== 状态 ====================

    /**
     * 获取Milvus客户端（诊断用）
     */
    public MilvusClient getClient() {
        return client;
    }

    /**
     * 判断Milvus客户端是否已连接
     */
    public boolean isReady() {
        return ready && client != null;
    }

    /**
     * 获取健康检查状态描述
     */
    public String statusMessage() {
        if (isReady()) {
            return "UP: Milvus " + host + ":" + port;
        }
        return "DOWN: Milvus 连接不可用" + (lastError == null ? "" : "（" + lastError + "）");
    }

    /**
     * 基座校验：Milvus 不可用时抛异常，由上层转「服务不可用」
     */
    private void ensureUsable() {
        if (!isReady() || client == null) {
            throw new IllegalStateException("Milvus 不可用: " + statusMessage());
        }
    }

    // ==================== 集合命名 ====================

    /**
     * FAQ 租户集合名（健康检查等对外展示用）
     */
    public String tenantCollectionName(String tenantId) {
        return tenantCollectionName(milvusProperties.getCollectionName(), tenantId);
    }

    /**
     * 文档切片租户集合名
     */
    public String chunkCollectionName(String tenantId) {
        return tenantCollectionName(milvusProperties.getChunkCollectionName(), tenantId);
    }

    /**
     * 通用租户集合名：基础集合名 + 租户后缀
     */
    private String tenantCollectionName(String baseName, String tenantId) {
        return baseName + "_" + tenantSuffix(tenantId);
    }

    /**
     * 租户后缀：归一化 + 短哈希
     *
     * <p>归一化会把非法字符统一替换为 "_"，不同租户可能撞名（"a-b" 与 "a b" 都变 a_b），
     * 追加归一化结果的短哈希即可区分；集合名一旦产出即固定，不要随意改动本算法。</p>
     */
    private String tenantSuffix(String tenantId) {
        String normalized = normalizeTenant(tenantId);
        return normalized + "_" + Integer.toHexString(normalized.hashCode());
    }

    /**
     * 租户归一化：空值归 default，只保留集合名合法字符（小写字母/数字/下划线）
     */
    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "default";
        }
        return tenantId.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    // ==================== 配置读取 ====================

    /**
     * 获取相似度命中阈值（COSINE 分数低于该值视为未命中）
     */
    public float scoreThreshold() {
        return milvusProperties.getScoreThreshold();
    }

    /**
     * 获取检索TopK（保证至少为 1）
     */
    public int topK() {
        return Math.max(1, milvusProperties.getTopK());
    }

    // ==================== FAQ 向量：写入 / 删除 / 检索 / 状态 ====================

    /**
     * FAQ 向量 upsert：同一 faqId 只保留最新一条（主键非自增，天然幂等）
     */
    public void upsertTenant(long faqId, String tenantId, List<Float> vector, String content) {
        ensureUsable();
        validateDimension(vector);
        String collection = tenantCollectionName(tenantId);
        ensureCollection(collection, faqFields());

        JSONObject row = new JSONObject();
        row.put(FIELD_FAQ_ID, faqId);
        row.put(FIELD_TENANT_ID, normalizeTenant(tenantId));
        row.put(FIELD_CONTENT, truncate(content));
        row.put(FIELD_EMBEDDING, vector);

        R<?> result = client.upsert(UpsertParam.newBuilder()
                .withCollectionName(collection)
                .withRows(List.of(row))
                .build());
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus upsert 失败: " + result.getMessage());
        }
    }

    /**
     * 按 FAQ ID 删除租户集合中的向量
     *
     * <p>集合不存在说明该租户从未写入过向量，直接返回 false（不顺手建空集合）。</p>
     */
    public boolean deleteByFaqId(Long faqId, String tenantId) {
        if (faqId == null) {
            return false;
        }
        ensureUsable();
        String collection = tenantCollectionName(tenantId);
        if (tenantCollectionMissing(collection)) {
            return false;
        }
        R<?> result = client.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(FIELD_FAQ_ID + " == " + faqId)   // faqId 为 long，无注入风险
                .build());
        return result.getStatus() == R.Status.Success.getCode();
    }

    /**
     * 登记「待清理向量」，供后续向量对账任务补偿删除
     *
     * @param bizType  业务类型（{@link #BIZ_TYPE_FAQ} / {@link #BIZ_TYPE_CHUNK}）
     * @param tenantId 租户编码（内部会归一化）
     * @param bizId    业务主键（FAQ 主键 / 切片主键）
     */
    public void markPendingDelete(String bizType, String tenantId, Long bizId) {
        if (bizId == null) {
            return;
        }
        String member = bizType + ":" + normalizeTenant(tenantId) + ":" + bizId;
        if (redisTemplate == null) {
            log.warn("向量待清理登记跳过（Redis 不可用），请人工关注 {}", member);
            return;
        }
        try {
            redisTemplate.opsForSet().add(PENDING_DELETE_KEY, member);
            log.info("已登记待清理向量: {}", member);
        } catch (Exception e) {
            log.warn("向量待清理登记失败 member={}: {}", member, e.getMessage());
        }
    }

    /**
     * 批量查询这些 faqId 中哪些已在租户 FAQ 集合中（供「是否已向量化」状态回显）
     *
     * <p>集合不存在 → 视为全部未向量化（返回空集合）；Milvus 不可用或 RPC 失败 → 抛异常，
     * 由上层降级为「状态未知」，不要谎报成「未向量化」。</p>
     *
     * @param tenantId 租户编码
     * @param faqIds   待查询的 FAQ 主键集合
     * @return 已存在于向量库中的 faqId 集合
     */
    public Set<Long> filterExistingFaqIds(String tenantId, Collection<Long> faqIds) {
        if (faqIds == null || faqIds.isEmpty()) {
            return Set.of();
        }
        ensureUsable();
        String collection = tenantCollectionName(tenantId);
        if (tenantCollectionMissing(collection)) {
            return Set.of();
        }
        List<Long> ids = faqIds.stream().filter(Objects::nonNull).distinct()
                .limit(STATUS_QUERY_MAX_IDS).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        R<QueryResults> response = client.query(QueryParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(FIELD_FAQ_ID + " in [" + ids.stream().map(String::valueOf)
                        .collect(Collectors.joining(",")) + "]")     // 全为 long，无注入风险
                .withOutFields(List.of(FIELD_FAQ_ID))
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED) // 刚 upsert 完立刻查也能查到
                .build());
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus 状态查询失败: " + response.getMessage());
        }
        Set<Long> existing = new HashSet<>();
        try {
            QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
            for (QueryResultsWrapper.RowRecord row : wrapper.getRowRecords()) {
                Object value = row.get(FIELD_FAQ_ID);
                if (value instanceof Number number) {
                    existing.add(number.longValue());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Milvus 状态查询结果解析失败: " + e.getMessage(), e);
        }
        return existing;
    }

    /**
     * FAQ 租户向量检索
     *
     * <p>集合不存在 → 返回空列表（未命中，非错误）；Milvus 不可用或 RPC 失败 → 抛异常，
     * 由上层 {@code RagSearchService} 转「服务不可用」。</p>
     *
     * @param tenantId 租户编码
     * @param vector   查询向量
     * @param topK     返回条数上限
     * @return 命中列表（已按阈值过滤，按相似度降序）
     */
    public List<MilvusHit> search(String tenantId, List<Float> vector, int topK) {
        ensureUsable();
        validateDimension(vector);
        String collection = tenantCollectionName(tenantId);
        if (tenantCollectionMissing(collection)) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        R<SearchResults> response = client.search(SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.COSINE)
                .withFloatVectors(List.of(vector))
                .withTopK(limit)
                .withOutFields(List.of(FIELD_CONTENT))
                // 兜底：集合内按 tenant_id 再过滤一次，防止归一化碰撞串租
                .withExpr(FIELD_TENANT_ID + " == \"" + escapeExpr(normalizeTenant(tenantId)) + "\"")
                .withParams("{\"nprobe\":" + milvusProperties.getNprobe() + "}")
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .build());
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus 检索失败: " + response.getMessage());
        }
        return parseFaqHits(response.getData().getResults());
    }

    // ==================== 文档切片向量：写入 / 删除 / 检索 ====================

    /**
     * 文档切片向量 upsert：同一 chunkId 只保留最新一条
     *
     * @param tenantId   租户编码
     * @param chunkId    切片主键（{@code cs_kb_chunk.id}）
     * @param documentId 来源文档ID（{@code cs_document_version.document_id}）
     * @param chunkIndex 文档内切片序号
     * @param vector     切片内容向量
     * @param content    切片文本（超出上限会截断）
     */
    public void upsertChunk(String tenantId, long chunkId, String documentId, int chunkIndex,
                            List<Float> vector, String content) {
        ensureUsable();
        validateDimension(vector);
        String collection = chunkCollectionName(tenantId);
        ensureCollection(collection, chunkFields());

        JSONObject row = new JSONObject();
        row.put(FIELD_CHUNK_ID, chunkId);
        row.put(FIELD_TENANT_ID, normalizeTenant(tenantId));
        row.put(FIELD_DOCUMENT_ID, documentId == null ? "" : documentId);
        row.put(FIELD_CHUNK_INDEX, chunkIndex);
        row.put(FIELD_CONTENT, truncate(content));
        row.put(FIELD_EMBEDDING, vector);

        R<?> result = client.upsert(UpsertParam.newBuilder()
                .withCollectionName(collection)
                .withRows(List.of(row))
                .build());
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus 切片 upsert 失败: " + result.getMessage());
        }
    }

    /**
     * 按来源文档删除切片向量（文档重切/删除时先清旧切片）
     *
     * @return 是否删除成功；集合不存在（该租户无切片向量）返回 false
     */
    public boolean deleteChunksByDocument(String tenantId, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return false;
        }
        ensureUsable();
        String collection = chunkCollectionName(tenantId);
        if (tenantCollectionMissing(collection)) {
            return false;
        }
        R<?> result = client.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(FIELD_DOCUMENT_ID + " == \"" + escapeExpr(documentId) + "\"")
                .build());
        return result.getStatus() == R.Status.Success.getCode();
    }

    /**
     * 文档切片向量检索（全租户范围）
     */
    public List<MilvusChunkHit> searchChunks(String tenantId, List<Float> vector, int topK) {
        return searchChunks(tenantId, null, vector, topK);
    }

    /**
     * 文档切片向量检索
     *
     * <p>集合不存在 → 返回空列表（未命中）；Milvus 不可用或 RPC 失败 → 抛异常，
     * 由上层转「服务不可用」。</p>
     *
     * @param documentId 可选：只在该文档内检索；为空则全租户检索
     */
    public List<MilvusChunkHit> searchChunks(String tenantId, String documentId,
                                             List<Float> vector, int topK) {
        ensureUsable();
        validateDimension(vector);
        String collection = chunkCollectionName(tenantId);
        if (tenantCollectionMissing(collection)) {
            return List.of();
        }
        StringBuilder expr = new StringBuilder()
                .append(FIELD_TENANT_ID).append(" == \"")
                .append(escapeExpr(normalizeTenant(tenantId))).append("\"");
        if (documentId != null && !documentId.isBlank()) {
            expr.append(" and ").append(FIELD_DOCUMENT_ID)
                    .append(" == \"").append(escapeExpr(documentId)).append("\"");
        }
        R<SearchResults> response = client.search(SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.COSINE)
                .withFloatVectors(List.of(vector))
                .withTopK(Math.max(1, topK))
                .withOutFields(List.of(FIELD_DOCUMENT_ID, FIELD_CHUNK_INDEX, FIELD_CONTENT))
                .withExpr(expr.toString())
                .withParams("{\"nprobe\":" + milvusProperties.getNprobe() + "}")
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .build());
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus 切片检索失败: " + response.getMessage());
        }
        return parseChunkHits(response.getData().getResults());
    }

    // ==================== 结果解析 ====================

    /**
     * 解析 FAQ 检索结果并按阈值过滤（COSINE 分数越大越相似）
     */
    private List<MilvusHit> parseFaqHits(SearchResultData data) {
        try {
            SearchResultsWrapper wrapper = new SearchResultsWrapper(data);
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);   // 第 0 条查询向量
            List<?> contents = wrapper.getFieldData(FIELD_CONTENT, 0);
            List<MilvusHit> hits = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) {
                float score = scores.get(i).getScore();
                if (score < scoreThreshold()) {
                    continue;
                }
                long faqId = scores.get(i).getLongID();                          // 主键为 Int64
                String content = i < contents.size() ? String.valueOf(contents.get(i)) : "";
                hits.add(new MilvusHit(faqId, score, content));
            }
            return hits;
        } catch (Exception e) {
            throw new IllegalStateException("Milvus 检索结果解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析切片检索结果并按同一阈值过滤
     */
    private List<MilvusChunkHit> parseChunkHits(SearchResultData data) {
        try {
            SearchResultsWrapper wrapper = new SearchResultsWrapper(data);
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            List<?> documentIds = wrapper.getFieldData(FIELD_DOCUMENT_ID, 0);
            List<?> chunkIndexes = wrapper.getFieldData(FIELD_CHUNK_INDEX, 0);
            List<?> contents = wrapper.getFieldData(FIELD_CONTENT, 0);
            List<MilvusChunkHit> hits = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) {
                float score = scores.get(i).getScore();
                if (score < scoreThreshold()) {
                    continue;
                }
                long chunkId = scores.get(i).getLongID();
                String documentId = i < documentIds.size() ? String.valueOf(documentIds.get(i)) : "";
                int chunkIndex = i < chunkIndexes.size() && chunkIndexes.get(i) instanceof Number number
                        ? number.intValue() : 0;
                String content = i < contents.size() ? String.valueOf(contents.get(i)) : "";
                hits.add(new MilvusChunkHit(chunkId, documentId, chunkIndex, content, score));
            }
            return hits;
        } catch (Exception e) {
            throw new IllegalStateException("Milvus 切片检索结果解析失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部辅助 ====================

    /**
     * FAQ 集合字段定义（主键 faq_id 非自增，便于按 faqId 幂等 upsert）
     */
    private List<FieldType> faqFields() {
        return List.of(
                FieldType.newBuilder().withName(FIELD_FAQ_ID)
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true).withAutoID(false).build(),
                FieldType.newBuilder().withName(FIELD_TENANT_ID)
                        .withDataType(DataType.VarChar).withMaxLength(64).build(),
                FieldType.newBuilder().withName(FIELD_CONTENT)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(milvusProperties.getContentMaxLength()).build(),
                FieldType.newBuilder().withName(FIELD_EMBEDDING)
                        .withDataType(DataType.FloatVector)
                        .withDimension(milvusProperties.getDimension()).build());
    }

    /**
     * 文档切片集合字段定义（主键 chunk_id 非自增，便于按 chunkId 幂等 upsert）
     */
    private List<FieldType> chunkFields() {
        return List.of(
                FieldType.newBuilder().withName(FIELD_CHUNK_ID)
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true).withAutoID(false).build(),
                FieldType.newBuilder().withName(FIELD_TENANT_ID)
                        .withDataType(DataType.VarChar).withMaxLength(64).build(),
                FieldType.newBuilder().withName(FIELD_DOCUMENT_ID)
                        .withDataType(DataType.VarChar).withMaxLength(64).build(),
                FieldType.newBuilder().withName(FIELD_CHUNK_INDEX)
                        .withDataType(DataType.Int32).build(),
                FieldType.newBuilder().withName(FIELD_CONTENT)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(milvusProperties.getContentMaxLength()).build(),
                FieldType.newBuilder().withName(FIELD_EMBEDDING)
                        .withDataType(DataType.FloatVector)
                        .withDimension(milvusProperties.getDimension()).build());
    }

    /**
     * 集合是否不存在；存在则写入缓存（不存在不缓存，避免别处刚建好却读不到）
     */
    private boolean tenantCollectionMissing(String collectionName) {
        if (readyCollections.contains(collectionName)) {
            return false;
        }
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        if (exists.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus hasCollection 失败: " + exists.getMessage());
        }
        boolean present = Boolean.TRUE.equals(exists.getData());
        if (present) {
            readyCollections.add(collectionName);
        }
        return !present;
    }

    /**
     * 懒建集合：双检锁只锁首次创建；已存在也要 load（保证可检索）
     *
     * @param fields 集合字段定义（FAQ 集合或切片集合）
     */
    private void ensureCollection(String collectionName, List<FieldType> fields) {
        if (readyCollections.contains(collectionName)) {
            return;
        }
        synchronized (collectionLock) {
            if (readyCollections.contains(collectionName)) {
                return;
            }
            boolean created = false;
            if (tenantCollectionMissing(collectionName)) {
                createCollectionIfAbsent(collectionName, fields);
                createIndexIfAbsent(collectionName);
                created = true;
            }
            client.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName).build());
            readyCollections.add(collectionName);
            if (created) {
                log.info("租户集合已创建: {}", collectionName);
            }
        }
    }

    /**
     * 建集合：多副本并发首次写入时，另一实例可能已建好（Milvus 返回「已存在」），按成功处理
     */
    private void createCollectionIfAbsent(String collectionName, List<FieldType> fields) {
        R<RpcStatus> result = client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSchema(CollectionSchemaParam.newBuilder()
                        .withFieldTypes(fields)
                        .build())
                .build());
        checkCreateResult(result.getStatus(), result.getMessage(), "createCollection", collectionName);
    }

    /**
     * 建向量索引：并发竞态下的「索引已存在」同样按成功处理
     */
    private void createIndexIfAbsent(String collectionName) {
        R<RpcStatus> result = client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collectionName).withFieldName(FIELD_EMBEDDING)
                .withIndexType(IndexType.IVF_FLAT).withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":" + milvusProperties.getNlist() + "}")
                .build());
        checkCreateResult(result.getStatus(), result.getMessage(), "createIndex", collectionName);
    }

    /**
     * 建集合/索引的结果校验：并发竞态下的「已存在」不算错误
     */
    private void checkCreateResult(int status, String message, String action, String collectionName) {
        if (status == R.Status.Success.getCode()) {
            return;
        }
        String msg = message == null ? "" : message.toLowerCase();
        if (msg.contains("already exist") || msg.contains("duplicate")) {
            log.info("{} 跳过（已由其它实例完成）: {}", action, collectionName);
            return;
        }
        throw new IllegalStateException("Milvus " + action + " 失败: " + message);
    }

    /**
     * 维度校验：与 milvus.dimension 不符直接失败（更换向量模型后需重建租户集合）
     */
    private void validateDimension(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("向量为空，无法写入/检索 Milvus");
        }
        int expect = milvusProperties.getDimension();
        if (vector.size() != expect) {
            throw new IllegalArgumentException("向量维度不符: 实际 " + vector.size()
                    + "，配置 milvus.dimension=" + expect + "（更换向量模型后需重建租户集合）");
        }
    }

    /**
     * 内容截断：超出 milvus.content-max-length 时按长度截断，避免写入失败
     */
    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        int max = milvusProperties.getContentMaxLength();
        return content.length() > max ? content.substring(0, max) : content;
    }

    /**
     * Milvus 表达式字符串转义（防表达式注入）
     */
    private String escapeExpr(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
