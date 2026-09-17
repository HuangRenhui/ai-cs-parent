/**
 * 知识图谱增强能力的演示数据与拦截逻辑。
 *
 * 从 `mock/index.js` 拆出来单独放：这组能力有 60+ 接口、8 个子域，
 * 塞进主文件会把主文件撑爆。结构上只依赖传入的路径与方法，不碰主文件状态。
 */

const ok = (data, msg = '操作成功') => ({ code: 200, msg, data })
const fail = (msg, code = 400) => ({ code, msg, data: null })

/** 路径末段取参（id / tag / ruleName 等） */
const tail = (path) => decodeURIComponent(path.split('/').pop() || '')

// ===================== 3D 模型 =====================
const models3d = [
  { knowledgeId: '3d-1001', name: '智能音箱 Pro', format: 'glb', size: 2456789, tags: ['电子', '音箱'], source: '商品库导入', createTime: '2026-09-10 10:20:00', vertices: 12480, faces: 8320 },
  { knowledgeId: '3d-1002', name: '扫地机器人 S9', format: 'glb', size: 4120345, tags: ['电子', '家电'], source: '商品库导入', createTime: '2026-09-11 14:05:00', vertices: 21890, faces: 14560 },
  { knowledgeId: '3d-1003', name: '可折叠收纳箱', format: 'obj', size: 987654, tags: ['家居'], source: '手工上传', createTime: '2026-09-12 09:40:00', vertices: 5620, faces: 3740 },
  { knowledgeId: '3d-1004', name: '儿童安全座椅', format: 'glb', size: 3210987, tags: ['母婴', '汽车'], source: '商品库导入', createTime: '2026-09-13 16:30:00', vertices: 18340, faces: 12220 }
]

/** 3D 模型问答的答案池，按关键词命中 */
const model3dQa = (question) => {
  const q = String(question || '')
  if (q.includes('尺寸') || q.includes('多大')) {
    return '该模型主体尺寸约 180 × 120 × 95 毫米，含包装后的体积重约 1.2 千克。'
  }
  if (q.includes('材质') || q.includes('材料')) {
    return '外壳为 ABS + 聚碳酸酯，内部支架为铝合金，均可回收。'
  }
  if (q.includes('拆') || q.includes('结构')) {
    return '模型共 6 个可拆解部件：上盖、主体、底座、电池仓、滤网、电源模块，拆解顺序为从上到下。'
  }
  return '该 3D 模型共有 12480 个顶点、8320 个三角面，用于商品详情页 360° 展示与 AR 摆放。'
}

// ===================== Neo4j 图分析 =====================
const graphNodes = [
  { nodeId: 'n-1', name: '退款', label: '业务动作', degree: 12, community: '售后域' },
  { nodeId: 'n-2', name: '物流', label: '业务对象', degree: 9, community: '履约域' },
  { nodeId: 'n-3', name: '订单', label: '业务实体', degree: 24, community: '交易域' },
  { nodeId: 'n-4', name: '发票', label: '业务对象', degree: 6, community: '财务域' },
  { nodeId: 'n-5', name: '会员等级', label: '业务属性', degree: 8, community: '交易域' },
  { nodeId: 'n-6', name: '优惠券', label: '营销对象', degree: 11, community: '营销域' }
]

const graphEdges = [
  { relationId: 'r-1', source: '退款', target: '订单', relType: '关联', weight: 0.92 },
  { relationId: 'r-2', source: '物流', target: '订单', relType: '属于', weight: 0.88 },
  { relationId: 'r-3', source: '发票', target: '订单', relType: '基于', weight: 0.75 },
  { relationId: 'r-4', source: '优惠券', target: '订单', relType: '作用于', weight: 0.81 },
  { relationId: 'r-5', source: '会员等级', target: '优惠券', relType: '决定', weight: 0.66 }
]

// ===================== 推理规则 =====================
const reasoningRules = [
  { ruleName: '退款可退金额', description: '按订单实付金额扣除已用优惠券反算可退金额', enabled: 1, hitCount: 128, category: '业务规则' },
  { ruleName: '超期无理由判定', description: '签收超过 7 天且非质量问题时判定为不可退', enabled: 1, hitCount: 64, category: '业务规则' },
  { ruleName: '同一实体合并', description: '同名且属性相似度高于阈值的实体自动合并候选', enabled: 1, hitCount: 37, category: '图谱维护' },
  { ruleName: '环路检测', description: '检测「A 属于 B、B 属于 A」这类互相指向的环路', enabled: 0, hitCount: 5, category: '图谱维护' }
]

// ===================== 时序 =====================
const temporalTimeline = [
  { time: '2026-06-01', event: '创建', detail: '「智能音箱 Pro」节点首次入库' },
  { time: '2026-07-12', event: '属性变更', detail: '价格区间由 299-399 调整为 249-349' },
  { time: '2026-08-20', event: '新增关系', detail: '关联「以旧换新」活动' },
  { time: '2026-09-10', event: '关系失效', detail: '与「旧款配件」的关系已过期' }
]

// ===================== 多语言 =====================
const languageDistribution = [
  { language: 'zh', name: '中文', count: 1280, ratio: 62.5 },
  { language: 'en', name: '英文', count: 512, ratio: 25.0 },
  { language: 'ja', name: '日文', count: 154, ratio: 7.5 },
  { language: 'ko', name: '韩文', count: 102, ratio: 5.0 }
]

// ===================== 图嵌入 =====================
const embeddingMissingLinks = [
  { source: '退款', target: '售后政策', score: 0.91, reason: '向量邻近但图中无直连' },
  { source: '发票', target: '报销', score: 0.87, reason: '向量邻近但图中无直连' },
  { source: '优惠券', target: '大促活动', score: 0.84, reason: '向量邻近但图中无直连' }
]

// ===================== 演化 =====================
const evolutionLog = [
  { id: 1, time: '2026-09-15 03:00:00', action: '全量重算', detail: '重建全部节点的图嵌入，耗时 128 秒', operator: '系统定时任务', status: 1 },
  { id: 2, time: '2026-09-14 03:00:00', action: '增量更新', detail: '新增 42 个节点、87 条关系', operator: '系统定时任务', status: 1 },
  { id: 3, time: '2026-09-13 03:00:00', action: '清理孤立节点', detail: '移除 6 个无任何关系的节点', operator: '系统定时任务', status: 1 },
  { id: 4, time: '2026-09-12 15:20:00', action: '全量重算', detail: '手动触发，重建图嵌入', operator: '超级管理员', status: 1 }
]

/**
 * 图谱增强的全部拦截逻辑。
 * @returns 命中则返回响应对象，未命中返回 null，交由主 mock 继续匹配。
 */
export function handleGraphEnhanced(method, path, p) {
  if (!path.startsWith('/knowledge-enhanced/')) return null

  // ---------- 3D 模型 ----------
  if (path === '/knowledge-enhanced/3d-model/all') return ok(models3d)
  if (path === '/knowledge-enhanced/3d-model/search') {
    const kw = String(p.keyword || '').trim().toLowerCase()
    return kw
      ? ok(models3d.filter((m) => `${m.name}${m.tags.join('')}`.toLowerCase().includes(kw)))
      : ok(models3d)
  }
  if (path.startsWith('/knowledge-enhanced/3d-model/tag/')) {
    const tag = tail(path)
    return ok(models3d.filter((m) => m.tags.includes(tag)))
  }
  if (path.startsWith('/knowledge-enhanced/3d-model/similar/')) {
    const id = tail(path)
    return ok(models3d.filter((m) => m.knowledgeId !== id).slice(0, 3)
      .map((m) => ({ ...m, similarity: 0.82 })))
  }
  if (path.startsWith('/knowledge-enhanced/3d-model/report/')) {
    const id = tail(path)
    const m = models3d.find((x) => x.knowledgeId === id) || models3d[0]
    return ok({
      knowledgeId: m.knowledgeId, name: m.name, format: m.format,
      vertices: m.vertices, faces: m.faces, sizeKb: Math.round(m.size / 1024),
      lodLevels: 3, textureCount: 4, animationCount: 2,
      issues: ['顶点数偏高，建议生成 LOD2 低模用于列表页预览', '未包含法线贴图，AR 模式下反光偏平']
    })
  }
  if (path === '/knowledge-enhanced/3d-model/import') {
    return ok({ knowledgeId: `3d-${1000 + Math.floor(Math.random() * 900)}`, status: 'success' }, '模型已导入（演示）')
  }
  if (path === '/knowledge-enhanced/3d-model/batch-import') {
    return ok({ total: 3, success: 3, failed: 0 }, '批量导入完成（演示）')
  }
  if (path === '/knowledge-enhanced/3d-model/compare') {
    return ok([
      { knowledgeId: '3d-1001', name: '智能音箱 Pro', vertices: 12480, faces: 8320, sizeKb: 2399 },
      { knowledgeId: '3d-1002', name: '扫地机器人 S9', vertices: 21890, faces: 14560, sizeKb: 4024 }
    ])
  }
  if (path === '/knowledge-enhanced/3d-model/qa') {
    return ok({ answer: model3dQa(p.question), confidence: 0.87, references: ['3d-1001'] })
  }
  if (path.startsWith('/knowledge-enhanced/3d-model/conversion-advice/')) {
    return ok({
      advice: [
        '转为 glTF 2.0 并开启 Draco 压缩，预计体积下降约 62%',
        '烘焙一张 1024×1024 的环境贴图，替代 4 张材质贴图',
        '生成 LOD2 低模（目标 3000 面以内）供移动端与列表页预览'
      ],
      estimatedSizeKb: 912
    })
  }

  // ---------- Neo4j 图分析 ----------
  if (path === '/knowledge-enhanced/neo4j/status') {
    return ok({ uri: 'bolt://localhost:7687', connected: true, version: '5.20.0', nodeCount: 4286, relationCount: 11342, latencyMs: 12 })
  }
  if (path === '/knowledge-enhanced/neo4j/stats') {
    return ok({ nodeCount: 4286, relationCount: 11342, labelCount: 14, relTypeCount: 22, avgDegree: 5.3, density: 0.00062 })
  }
  if (path === '/knowledge-enhanced/neo4j/cypher') {
    const q = String(p.cypher || '').toUpperCase()
    if (q.includes('DELETE') || q.includes('DROP')) {
      return fail('演示环境禁止执行写操作，请改用只读查询（MATCH / RETURN）')
    }
    return ok({
      columns: ['n.name', 'n.label', 'degree'],
      rows: graphNodes.map((n) => [n.name, n.label, n.degree]),
      rowCount: graphNodes.length,
      elapsedMs: 9
    })
  }
  if (path === '/knowledge-enhanced/neo4j/sync') {
    return ok({ synced: 4286, created: 42, updated: 17, elapsedMs: 3210 }, '图谱已同步到 Neo4j（演示）')
  }
  if (path === '/knowledge-enhanced/neo4j/shortest-path') {
    return ok({
      from: p.from || '退款', to: p.to || '发票', length: 3,
      path: ['退款', '订单', '发票']
    })
  }
  if (path.startsWith('/knowledge-enhanced/neo4j/k-hop/')) {
    const node = tail(path)
    const k = Number(p.k || 2)
    return ok({
      center: node, k,
      nodes: graphNodes.slice(0, Math.min(graphNodes.length, k + 3)),
      edges: graphEdges.slice(0, Math.min(graphEdges.length, k + 2))
    })
  }
  if (path === '/knowledge-enhanced/neo4j/communities') {
    return ok([
      { communityId: 'c-1', name: '售后域', nodeCount: 128, cohesion: 0.78, coreMember: '退款' },
      { communityId: 'c-2', name: '交易域', nodeCount: 342, cohesion: 0.71, coreMember: '订单' },
      { communityId: 'c-3', name: '营销域', nodeCount: 96, cohesion: 0.64, coreMember: '优惠券' },
      { communityId: 'c-4', name: '履约域', nodeCount: 154, cohesion: 0.69, coreMember: '物流' }
    ])
  }
  if (path === '/knowledge-enhanced/neo4j/pagerank') {
    return ok([
      { nodeId: 'n-3', name: '订单', score: 0.184 },
      { nodeId: 'n-1', name: '退款', score: 0.152 },
      { nodeId: 'n-6', name: '优惠券', score: 0.121 },
      { nodeId: 'n-2', name: '物流', score: 0.118 }
    ])
  }
  if (path === '/knowledge-enhanced/neo4j/bridge-nodes') {
    return ok([
      { nodeId: 'n-3', name: '订单', bridgeScore: 0.91, connects: ['交易域', '售后域', '履约域'] },
      { nodeId: 'n-6', name: '优惠券', bridgeScore: 0.72, connects: ['营销域', '交易域'] }
    ])
  }
  if (path === '/knowledge-enhanced/neo4j/circular-deps') {
    return ok([
      { cycle: ['退款', '订单', '退款'], length: 2, severity: '中' },
      { cycle: ['发票', '报销', '发票'], length: 2, severity: '低' }
    ])
  }

  // ---------- 图谱推理 ----------
  if (path === '/knowledge-enhanced/reasoning/rules') return ok(reasoningRules)
  if (path === '/knowledge-enhanced/reasoning/execute-all') {
    return ok({ executed: reasoningRules.filter((r) => r.enabled).length, newFacts: 42, elapsedMs: 1580 }, '全量推理完成（演示）')
  }
  if (path.startsWith('/knowledge-enhanced/reasoning/execute/')) {
    const rule = tail(path)
    return ok({ ruleName: rule, newFacts: 12, samples: ['退款：可退金额 = 实付金额 − 已用优惠券'], elapsedMs: 320 }, '已执行（演示）')
  }
  if (path === '/knowledge-enhanced/reasoning/paths') {
    return ok([
      { path: ['会员等级', '优惠券', '订单'], hops: 2, weight: 0.81 },
      { path: ['订单', '物流', '签收'], hops: 2, weight: 0.74 }
    ])
  }
  if (path === '/knowledge-enhanced/reasoning/hierarchy') {
    return ok([
      { name: '业务实体', children: [{ name: '订单' }, { name: '发票' }] },
      { name: '业务动作', children: [{ name: '退款' }, { name: '签收' }] },
      { name: '营销对象', children: [{ name: '优惠券' }] }
    ])
  }
  if (path === '/knowledge-enhanced/reasoning/disambiguation') {
    const kw = p.keyword || '苹果'
    return ok([
      { candidate: `${kw}（水果）`, confidence: 0.82, evidence: '关联「生鲜」「水果」节点' },
      { candidate: `${kw}（品牌）`, confidence: 0.61, evidence: '关联「手机」「电子产品」节点' }
    ])
  }
  if (path === '/knowledge-enhanced/reasoning/merge-entities') {
    return ok({ merged: 2, keptId: 'n-3' }, '实体已合并（演示）')
  }
  if (path === '/knowledge-enhanced/reasoning/chain') {
    return ok({
      chain: ['退款', '订单', '物流', '签收'],
      conclusion: '退款链路依赖订单与物流签收状态，投诉高发点在「已发货未签收」阶段'
    })
  }

  // ---------- 时序图谱 ----------
  if (path === '/knowledge-enhanced/temporal/relation') {
    return ok({ relationId: `tr-${Date.now() % 10000}` }, '时序关系已保存（演示）')
  }
  if (path === '/knowledge-enhanced/temporal/snapshot') {
    return ok({
      at: p.at || '2026-09-15', nodeCount: 4286, relationCount: 11342,
      nodes: graphNodes.slice(0, 4), edges: graphEdges.slice(0, 3)
    })
  }
  if (path === '/knowledge-enhanced/temporal/changes') {
    return ok([
      { time: '2026-09-10', type: '新增关系', detail: '「智能音箱 Pro」→「以旧换新」' },
      { time: '2026-09-08', type: '属性变更', detail: '「扫地机器人 S9」价格区间更新' },
      { time: '2026-09-05', type: '关系失效', detail: '「旧款配件」关联已过期' }
    ])
  }
  if (path.startsWith('/knowledge-enhanced/temporal/timeline/')) {
    return ok({ nodeId: tail(path), events: temporalTimeline })
  }
  if (path === '/knowledge-enhanced/temporal/global-timeline') {
    return ok([
      { date: '2026-09-15', added: 42, changed: 17, removed: 6 },
      { date: '2026-09-14', added: 31, changed: 22, removed: 3 },
      { date: '2026-09-13', added: 26, changed: 11, removed: 8 },
      { date: '2026-09-12', added: 48, changed: 19, removed: 2 },
      { date: '2026-09-11', added: 22, changed: 14, removed: 5 }
    ])
  }
  if (path.startsWith('/knowledge-enhanced/temporal/evolution/')) {
    return ok({ nodeId: tail(path), timeline: temporalTimeline, totalChanges: temporalTimeline.length })
  }
  if (path === '/knowledge-enhanced/temporal/predict-trend') {
    return ok({
      nodeId: p.nodeId || 'n-1',
      trend: 'up',
      forecast: [
        { period: '2026-10', mentions: 186 },
        { period: '2026-11', mentions: 214 },
        { period: '2026-12', mentions: 268 }
      ]
    })
  }
  if (path === '/knowledge-enhanced/temporal/validate') {
    return ok({
      total: 11342, valid: 11298, invalid: 44,
      issues: [
        { type: '时间区间倒挂', count: 18, sample: '关系生效时间晚于失效时间' },
        { type: '缺少时间戳', count: 26, sample: '历史导入数据未补 startTime' }
      ]
    })
  }

  // ---------- 多语言融合 ----------
  if (path === '/knowledge-enhanced/multilingual/language-distribution') {
    return ok(languageDistribution)
  }
  if (path === '/knowledge-enhanced/multilingual/equivalents') {
    return ok([
      { language: 'en', term: 'refund', confidence: 0.94 },
      { language: 'ja', term: '返金', confidence: 0.88 },
      { language: 'ko', term: '환불', confidence: 0.85 }
    ])
  }
  if (path === '/knowledge-enhanced/multilingual/cross-lingual-relations') {
    return ok([
      { source: '退款', sourceLang: 'zh', target: 'refund', targetLang: 'en', relType: '同义', confidence: 0.94 },
      { source: '物流', sourceLang: 'zh', target: 'logistics', targetLang: 'en', relType: '同义', confidence: 0.91 },
      { source: '发票', sourceLang: 'zh', target: 'invoice', targetLang: 'en', relType: '同义', confidence: 0.89 }
    ])
  }
  if (path === '/knowledge-enhanced/multilingual/link') {
    return ok({ linked: 3 }, '已建立跨语言链接（演示）')
  }
  if (path === '/knowledge-enhanced/multilingual/fuse') {
    return ok({ fused: 3, keptId: 'n-1' }, '多语言实体已融合（演示）')
  }
  if (path === '/knowledge-enhanced/multilingual/tag-language') {
    return ok({ tagged: 128 }, '已标注语种（演示）')
  }
  if (path === '/knowledge-enhanced/multilingual/aliases') {
    return ok({ saved: 4 }, '别名已保存（演示）')
  }

  // ---------- 图嵌入 ----------
  if (path === '/knowledge-enhanced/embedding/generate') {
    return ok({ generated: 4286, dimension: 128, elapsedMs: 8420 }, '图嵌入已生成（演示）')
  }
  if (path === '/knowledge-enhanced/embedding/missing-links') return ok(embeddingMissingLinks)
  if (path === '/knowledge-enhanced/embedding/predict-link') {
    return ok([
      { target: '售后政策', score: 0.91 },
      { target: '报销', score: 0.87 }
    ])
  }
  if (path.startsWith('/knowledge-enhanced/embedding/similar-nodes/')) {
    return ok(graphNodes.slice(1, 4).map((n) => ({ ...n, similarity: 0.86 })))
  }
  if (path === '/knowledge-enhanced/embedding/cluster') {
    return ok({
      clusters: [
        { id: 0, size: 342, keywords: ['订单', '支付', '优惠券'] },
        { id: 1, size: 128, keywords: ['退款', '售后', '投诉'] },
        { id: 2, size: 154, keywords: ['物流', '签收', '配送'] }
      ],
      silhouette: 0.62
    })
  }
  if (path.startsWith('/knowledge-enhanced/embedding/node/')) {
    return ok({
      nodeId: tail(path),
      dimension: 128,
      vector: Array.from({ length: 16 }, (_, i) => Number((Math.sin(i) * 0.5).toFixed(4))),
      truncated: true,
      note: '演示仅返回前 16 维'
    })
  }
  if (path === '/knowledge-enhanced/embedding/export') {
    return ok({ format: p.format || 'jsonl', exported: 4286, fileUrl: '/api/file/download?fileId=demo-embed' }, '导出任务已提交（演示）')
  }

  // ---------- GraphRAG ----------
  if (path === '/knowledge-enhanced/graphrag/qa') {
    return ok({
      answer: '退款到账时间取决于支付渠道：微信/支付宝 1-3 个工作日，银行卡 3-7 个工作日。若超时可提供交易流水核查。',
      confidence: 0.89,
      graphPaths: [['退款', '订单', '支付渠道'], ['退款', '到账时效']],
      entities: ['退款', '订单', '支付渠道']
    })
  }
  if (path === '/knowledge-enhanced/graphrag/entity-enhanced-retrieval') {
    return ok({
      query: p.query || '退款多久到账',
      entities: ['退款', '到账时效'],
      expanded: ['支付渠道', '银行卡', '微信支付'],
      hits: [
        { doc: '如何退款？', score: 0.93, from: '图扩展' },
        { doc: '退款到账时效说明', score: 0.88, from: '向量召回' }
      ]
    })
  }
  if (path === '/knowledge-enhanced/graphrag/guided-retrieval') {
    return ok({
      guide: ['退款', '到账时效', '支付渠道'],
      stepHits: [
        { step: '退款', hits: 12 },
        { step: '到账时效', hits: 5 },
        { step: '支付渠道', hits: 3 }
      ]
    })
  }
  if (path === '/knowledge-enhanced/graphrag/enhanced-ranking') {
    return ok([
      { doc: '如何退款？', vectorScore: 0.82, graphScore: 0.94, finalScore: 0.91 },
      { doc: '退款到账时效说明', vectorScore: 0.79, graphScore: 0.88, finalScore: 0.86 },
      { doc: '部分退款怎么操作？', vectorScore: 0.74, graphScore: 0.71, finalScore: 0.73 }
    ])
  }
  if (path === '/knowledge-enhanced/graphrag/stats') {
    return ok({ qaCount: 1246, avgEntities: 2.8, avgPaths: 3.4, avgElapsedMs: 862, hitRate: 0.91 })
  }

  // ---------- 图谱演化 ----------
  if (path === '/knowledge-enhanced/evolution/health') {
    return ok({
      score: 86, level: '良好',
      metrics: [
        { name: '孤立节点占比', value: '1.2%', status: 'good' },
        { name: '环路数量', value: '2', status: 'warn' },
        { name: '平均度数', value: '5.3', status: 'good' },
        { name: '关系有效率', value: '99.6%', status: 'good' }
      ]
    })
  }
  if (path === '/knowledge-enhanced/evolution/log') {
    const limit = Number(p.limit || 20)
    return ok(evolutionLog.slice(0, limit))
  }
  if (path === '/knowledge-enhanced/evolution/trend') {
    return ok([
      { date: '2026-09-11', nodes: 4180, relations: 11020 },
      { date: '2026-09-12', nodes: 4198, relations: 11104 },
      { date: '2026-09-13', nodes: 4214, relations: 11162 },
      { date: '2026-09-14', nodes: 4244, relations: 11255 },
      { date: '2026-09-15', nodes: 4286, relations: 11342 }
    ])
  }
  if (method === 'post' && path.startsWith('/knowledge-enhanced/evolution/')) {
    const action = tail(path)
    const label = {
      'incremental-update': '增量更新已完成（演示）',
      'recalculate-core': '核心节点已重算（演示）',
      'cleanup-relations': '冗余关系已清理（演示）',
      'cleanup-isolated': '孤立节点已清理（演示）',
      'trigger-full': '全量演化任务已触发（演示）'
    }[action] || '已执行（演示）'
    return ok({ action, affected: 42, elapsedMs: 1580 }, label)
  }

  return null
}
