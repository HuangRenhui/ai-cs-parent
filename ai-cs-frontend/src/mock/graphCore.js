/**
 * 知识图谱「基础管理」的演示数据与拦截逻辑。
 *
 * 与 graphEnhanced.js 的分工：那边是图谱之上的增强分析（图数据库、推理、时序、图嵌入…），
 * 这边是图谱本身的增删改查 —— 节点 / 关系 / 子图 / 可视化 / 统计。
 *
 * 字段名严格对齐 GraphManagePage 的表格列，否则页面拿到了数据也渲染不出来。
 * 统计值由当前数组实时算出，所以增删节点后统计会跟着变，保证演示里数据是「活的」。
 */

const ok = (data, msg = '操作成功') => ({ code: 200, msg, data })
const fail = (msg, code = 400) => ({ code, msg, data: null })

/** 路径末段取参 */
const tail = (path) => decodeURIComponent(path.split('/').pop() || '')

let seq = 900
const nid = () => ++seq

/** 节点：字段与表格列一一对应 */
const nodes = [
  { nodeId: 'n-1', name: '退款', label: '业务动作', degree: 12, community: '售后域' },
  { nodeId: 'n-2', name: '物流', label: '业务对象', degree: 9, community: '履约域' },
  { nodeId: 'n-3', name: '订单', label: '业务实体', degree: 24, community: '交易域' },
  { nodeId: 'n-4', name: '发票', label: '业务对象', degree: 6, community: '财务域' },
  { nodeId: 'n-5', name: '会员等级', label: '业务属性', degree: 8, community: '交易域' },
  { nodeId: 'n-6', name: '优惠券', label: '营销对象', degree: 11, community: '营销域' },
  { nodeId: 'n-7', name: '以旧换新', label: '营销活动', degree: 5, community: '营销域' },
  { nodeId: 'n-8', name: '客服话术', label: '知识条目', degree: 7, community: '服务域' }
]

/** 关系：起点与终点存节点名称，与页面上展示的保持一致 */
const relations = [
  { relationId: 'r-1', source: '退款', relType: '关联', target: '订单', weight: 0.92 },
  { relationId: 'r-2', source: '物流', relType: '属于', target: '订单', weight: 0.88 },
  { relationId: 'r-3', source: '发票', relType: '基于', target: '订单', weight: 0.75 },
  { relationId: 'r-4', source: '优惠券', relType: '作用于', target: '订单', weight: 0.81 },
  { relationId: 'r-5', source: '会员等级', relType: '决定', target: '优惠券', weight: 0.66 },
  { relationId: 'r-6', source: '以旧换新', relType: '关联', target: '订单', weight: 0.7 },
  { relationId: 'r-7', source: '客服话术', relType: '解释', target: '退款', weight: 0.58 },
  { relationId: 'r-8', source: '物流', relType: '关联', target: '客服话术', weight: 0.44 }
]

/** 统计实时算，增删后立刻反映 */
const buildStats = () => {
  const labels = new Set(nodes.map((n) => n.label))
  const avg = nodes.length
    ? Math.round((nodes.reduce((s, n) => s + (n.degree || 0), 0) / nodes.length) * 10) / 10
    : 0
  return {
    nodeCount: nodes.length,
    relationCount: relations.length,
    labelCount: labels.size,
    avgDegree: avg
  }
}

/** 以一个节点为中心，按给定跳数扩散出子图 */
const subgraphOf = (centerId, depth = 2) => {
  const center = nodes.find((n) => n.nodeId === String(centerId) || n.name === String(centerId))
  if (!center) return { center: null, nodes: [], edges: [] }
  const picked = new Set([center.name])
  const edges = []
  for (let hop = 0; hop < Number(depth) || 1; hop++) {
    for (const r of relations) {
      // 任一端已在集合里，就把另一端和这条关系一起纳入
      if (picked.has(r.source) || picked.has(r.target)) {
        picked.add(r.source)
        picked.add(r.target)
        if (!edges.some((e) => e.relationId === r.relationId)) edges.push(r)
      }
    }
  }
  const around = nodes.filter((n) => n.name !== center.name && picked.has(n.name))
  return { center: center.name, nodes: around, edges, centerNode: center }
}

/**
 * 图谱基础管理的拦截逻辑。
 * @returns 命中返回响应对象，未命中返回 null。
 */
export function handleGraphCore(method, path, p) {
  if (!path.startsWith('/knowledge-graph/')) return null

  // ---------- 统计 ----------
  if (path === '/knowledge-graph/statistics') return ok(buildStats())

  // ---------- 子图 ----------
  if (path === '/knowledge-graph/subgraph') {
    const { centerNode, nodes: around, edges } = subgraphOf(p.nodeId || 'n-3', p.depth || 1)
    // 不传中心时返回全图，页面初始加载走这一支
    if (!p.nodeId) {
      return ok({ nodes: [...nodes], edges: [...relations], center: centerNode ? centerNode.name : null })
    }
    return ok({ nodes: around, edges, center: centerNode ? centerNode.name : null })
  }

  // ---------- 可视化 ----------
  if (path === '/knowledge-graph/visualization') {
    const { center, nodes: around, edges } = subgraphOf(p.nodeId || 'n-3', p.depth || 2)
    if (!center) return fail('节点不存在，换个编号再试')
    return ok({ center, nodes: around, edges })
  }

  // ---------- 按类型筛节点 ----------
  if (path.startsWith('/knowledge-graph/nodes/label/')) {
    const label = tail(path)
    return ok(nodes.filter((n) => n.label === label))
  }

  // ---------- 某个节点的关系 ----------
  if (path.startsWith('/knowledge-graph/relations/')) {
    const idOrName = tail(path)
    const node = nodes.find((n) => n.nodeId === idOrName)
    const name = node ? node.name : idOrName
    return ok(relations.filter((r) => r.source === name || r.target === name))
  }

  // ---------- 节点增删 ----------
  if (method === 'post' && path === '/knowledge-graph/node') {
    const name = String(p.name || '').trim()
    if (!name) return fail('请填写节点名称')
    if (nodes.some((n) => n.name === name)) return fail(`节点已存在：${name}`)
    const row = {
      nodeId: `n-${nid()}`,
      name,
      label: p.label || '业务实体',
      community: p.community || '未归类',
      degree: 0
    }
    nodes.unshift(row)
    return ok(row, '节点已保存（演示）')
  }
  if (method === 'delete' && path.startsWith('/knowledge-graph/node/')) {
    const id = tail(path)
    const i = nodes.findIndex((n) => n.nodeId === id)
    if (i < 0) return fail('节点不存在')
    const [removed] = nodes.splice(i, 1)
    // 删节点同时清掉它的关系，与页面上的提示文案保持一致
    for (let k = relations.length - 1; k >= 0; k--) {
      if (relations[k].source === removed.name || relations[k].target === removed.name) relations.splice(k, 1)
    }
    return ok('节点及其关系已删除（演示）')
  }

  // ---------- 关系增删 ----------
  if (method === 'post' && path === '/knowledge-graph/relation') {
    if (!p.source || !p.relType || !p.target) return fail('请填写起点、关系与终点')
    const row = {
      relationId: `r-${nid()}`,
      source: String(p.source).trim(),
      relType: String(p.relType).trim(),
      target: String(p.target).trim(),
      weight: p.weight == null ? 0.8 : Number(p.weight)
    }
    relations.push(row)
    return ok(row, '关系已保存（演示）')
  }
  if (method === 'delete' && path.startsWith('/knowledge-graph/relation/')) {
    const id = tail(path)
    const i = relations.findIndex((r) => r.relationId === id)
    if (i < 0) return fail('关系不存在')
    relations.splice(i, 1)
    return ok('关系已删除（演示）')
  }

  // ---------- 检索与问答 ----------
  if (path === '/knowledge-graph/search') {
    const kw = String(p.keyword || '').trim().toLowerCase()
    const hitNodes = kw ? nodes.filter((n) => `${n.name}${n.label}${n.community}`.toLowerCase().includes(kw)) : nodes
    const hitEdges = kw
      ? relations.filter((r) => `${r.source}${r.relType}${r.target}`.toLowerCase().includes(kw))
      : relations
    return ok({ nodes: hitNodes, edges: hitEdges, keyword: p.keyword || '' })
  }
  if (method === 'post' && path === '/knowledge-graph/qa') {
    const q = String(p.question || '').trim()
    if (!q) return fail('请填写问题')
    // 演示：按问题里出现过的节点名拼一条可解释的答案
    const used = nodes.filter((n) => q.includes(n.name))
    const name = used.length ? used[0].name : '订单'
    const rel = relations.filter((r) => r.source === name || r.target === name).slice(0, 3)
    const answer = rel.length
      ? `「${name}」在图谱中与 ${rel.map((r) => (r.source === name ? `${r.relType}→${r.target}` : `${r.source}${r.relType}→`)).join('、')} 相关，共 ${relations.filter((r) => r.source === name || r.target === name).length} 条关系。`
      : `图谱中暂未收录「${name}」的关联信息，建议先在「图谱管理」补录节点与关系。`
    return ok({
      question: q,
      answer,
      references: rel.map((r) => `${r.source} ${r.relType} ${r.target}`),
      confidence: rel.length ? 0.86 : 0.32
    })
  }

  return null
}
