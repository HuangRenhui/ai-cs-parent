/**
 * 运维调用树：把扁平日志/Span 聚成 服务 → 类 → 方法，生成可复制分享文本。
 */

/** 毫秒展示：不足 1 秒用毫秒，否则用秒 */
export const durationLabel = (ms) => {
  if (ms == null || ms < 0 || ms === '') return '—'
  const n = Number(ms)
  if (!Number.isFinite(n)) return '—'
  if (n < 1000) return `${Math.round(n)} 毫秒`
  return `${(n / 1000).toFixed(2)} 秒`
}

/** 耗时颜色：≥1s 红，≥400ms 橙，否则绿 */
export const durationTone = (ms) => {
  const n = Number(ms) || 0
  if (n >= 1000) return 'danger'
  if (n >= 400) return 'warning'
  return 'success'
}

/** 日志时间转毫秒时间戳，兼容空格分隔 */
export const parseTs = (ts) => {
  if (!ts) return NaN
  const n = Date.parse(String(ts).replace(' ', 'T'))
  return n
}

const blank = (v, fallback = '') => (v == null || v === '' ? fallback : String(v))

/** 取类名最后一段，方便树节点展示 */
export const simpleName = (className) => {
  const s = blank(className, '')
  if (!s) return '未知类'
  const i = s.lastIndexOf('.')
  return i >= 0 ? s.slice(i + 1) : s
}

/** 旧日志没有 durationMs 时，用下一条时间差补齐 */
export const fillDurations = (logs) => {
  const sorted = [...(logs || [])].sort((a, b) => String(a.timestamp || '').localeCompare(String(b.timestamp || '')))
  for (let i = 0; i < sorted.length; i++) {
    if (sorted[i].durationMs != null && sorted[i].durationMs !== '') continue
    const t0 = parseTs(sorted[i].timestamp)
    const t1 = i + 1 < sorted.length ? parseTs(sorted[i + 1].timestamp) : t0
    sorted[i].durationMs = Number.isFinite(t0) && Number.isFinite(t1) && t1 >= t0 ? t1 - t0 : 0
  }
  return sorted
}

const CLASS_HINTS = [
  { test: /意图/, cls: 'com.ai.cs.aiagent.service.ConfigurableIntentService', method: 'recognize' },
  { test: /填槽|槽位/, cls: 'com.ai.cs.aiagent.service.SlotFillingService', method: 'fill' },
  { test: /提示词/, cls: 'com.ai.cs.aiagent.service.IndustryPromptPackService', method: 'overlayPrompt' },
  { test: /检索|知识|问答|命中/, cls: 'com.ai.cs.knowledge.service.RagSearchService', method: 'search' },
  { test: /重试/, cls: 'com.ai.cs.open.service.OpenPlatformService', method: 'retry' },
  { test: /工具|连接器/, cls: 'com.ai.cs.open.service.OpenPlatformService', method: 'invoke' },
  { test: /回调/, cls: 'com.ai.cs.open.service.OpenPlatformService', method: 'callback' },
  { test: /流式/, cls: 'com.ai.cs.aiagent.service.StreamingChatService', method: 'stream' },
  { test: /落库/, cls: 'com.ai.cs.aiagent.service.AiAgentService', method: 'persist' },
  { test: /用量/, cls: 'com.ai.cs.aiagent.service.AiAgentService', method: 'recordUsage' },
  { test: /模型|生成/, cls: 'com.ai.cs.aiagent.service.AiAgentService', method: 'chat' },
  { test: /会话/, cls: 'com.ai.cs.aiagent.service.AiAgentService', method: 'loadSession' },
  { test: /接入|鉴权|转发|回写|网关/, cls: 'com.ai.cs.gateway.filter.RequestIdGatewayFilter', method: 'filter' }
]

const classOf = (log) => {
  if (log.className) return log.className
  if (log.logger) return log.logger
  const msg = blank(log.message)
  const hit = CLASS_HINTS.find((h) => h.test.test(msg))
  if (hit) return hit.cls
  const svc = blank(log.service)
  if (svc.includes('gateway')) return 'com.ai.cs.gateway.filter.RequestIdGatewayFilter'
  if (svc.includes('open')) return 'com.ai.cs.open.service.OpenPlatformService'
  if (svc.includes('agent')) return 'com.ai.cs.aiagent.service.AiAgentService'
  return 'unknown'
}

const methodOf = (log) => {
  if (log.methodName) return log.methodName
  const msg = blank(log.message)
  const hit = CLASS_HINTS.find((h) => h.test.test(msg))
  return hit ? hit.method : 'handle'
}

/** 失败 / 告警 / 慢调用给出下一步该看什么 */
export const analysisOf = (level, durationMs, name, message) => {
  const blob = `${blank(name)} ${blank(message)}`.toLowerCase()
  if (String(level).toUpperCase() === 'ERROR') return '本步失败，先看异常栈、上游 HTTP 状态与超时配置'
  if (String(level).toUpperCase() === 'WARN') {
    if (/重试|retry|超时/.test(blob)) return '出现超时或重试，核对连接器地址、白名单与超时毫秒'
    return '出现告警，核对降级是否生效、是否需要转人工'
  }
  if ((Number(durationMs) || 0) >= 400) {
    if (/模型|chat|llm|生成/.test(blob)) return '耗时偏高，优先查模型健康、token 上限与上游超时'
    if (/工具|tool|连接器/.test(blob)) return '耗时偏高，核对开放工具、连接器与对方接口延迟'
    if (/检索|知识|向量/.test(blob)) return '耗时偏高，核对向量服务与知识库检索超时'
    return '耗时偏高，沿子步骤找最慢的类与方法'
  }
  return ''
}

const worseLevel = (a, b) => {
  const rank = (lv) => (/ERROR|FATAL/i.test(lv) ? 3 : /WARN/i.test(lv) ? 2 : 1)
  return rank(b) > rank(a) ? (b || a) : (a || 'INFO')
}

const applyRange = (node, logs) => {
  node.startTime = logs[0].timestamp
  node.endTime = logs[logs.length - 1].timestamp
  const sum = logs.reduce((s, x) => s + (Number(x.durationMs) || 0), 0)
  const t0 = parseTs(node.startTime)
  const t1 = parseTs(node.endTime)
  const span = Number.isFinite(t0) && Number.isFinite(t1) && t1 >= t0 ? t1 - t0 : 0
  node.durationMs = span > 0 ? span : sum
  node.level = logs.reduce((lv, x) => worseLevel(lv, x.level), 'INFO')
}

const buildMethod = (parentId, service, className, methodName, logs, requestId, id) => {
  const node = {
    spanId: id,
    parentSpanId: parentId,
    nodeType: 'method',
    service,
    className,
    methodName,
    name: methodName,
    requestId,
    line: logs[logs.length - 1].line,
    message: logs[logs.length - 1].message || '',
    children: []
  }
  applyRange(node, logs)
  node.analysis = analysisOf(node.level, node.durationMs, methodName, node.message)
  return node
}

const buildClass = (parentId, service, className, logs, requestId, idPrefix) => {
  const node = {
    spanId: idPrefix,
    parentSpanId: parentId,
    nodeType: 'class',
    service,
    className,
    name: simpleName(className),
    requestId,
    children: []
  }
  applyRange(node, logs)
  const groups = new Map()
  logs.forEach((log) => {
    const m = methodOf(log)
    if (!groups.has(m)) groups.set(m, [])
    groups.get(m).push(log)
  })
  let i = 1
  groups.forEach((rows, methodName) => {
    node.children.push(buildMethod(node.spanId, service, className, methodName, rows, requestId, `${idPrefix}-m${i++}`))
  })
  node.analysis = analysisOf(node.level, node.durationMs, node.name, node.message)
  return node
}

const buildService = (service, logs, requestId, seq) => {
  const node = {
    spanId: `svc-${seq}`,
    nodeType: 'service',
    service,
    name: service,
    requestId,
    children: []
  }
  applyRange(node, logs)
  const groups = new Map()
  logs.forEach((log) => {
    const c = classOf(log)
    if (!groups.has(c)) groups.set(c, [])
    groups.get(c).push(log)
  })
  let i = 1
  groups.forEach((rows, cls) => {
    node.children.push(buildClass(node.spanId, service, cls, rows, requestId, `cls-${seq}-${i++}`))
  })
  node.analysis = analysisOf(node.level, node.durationMs, node.name, node.message)
  return node
}

const countMethods = (nodes) => {
  let n = 0
  ;(nodes || []).forEach((node) => {
    if (node.nodeType === 'method') n += 1
    n += countMethods(node.children)
  })
  return n
}

const labelOf = (node, serviceText) => {
  if (node.nodeType === 'class') return simpleName(node.className)
  if (node.nodeType === 'method') return `${simpleName(node.className)}.${blank(node.methodName, '处理')}`
  return serviceText(node.service || node.name)
}

const oneLine = (text) => {
  const t = String(text || '').replace(/\n/g, ' ').trim()
  return t.length > 80 ? `${t.slice(0, 80)}…` : t
}

const appendNode = (lines, node, prefix, last, serviceText) => {
  const branch = last ? '└─ ' : '├─ '
  let line = `${prefix}${branch}${labelOf(node, serviceText)}`
  if (node.durationMs != null && node.durationMs !== '') line += `  ${durationLabel(node.durationMs)}`
  if (node.nodeType === 'method' && node.message) line += `  ${oneLine(node.message)}`
  lines.push(line)
  const kids = node.children || []
  const next = prefix + (last ? '   ' : '│  ')
  kids.forEach((child, i) => appendNode(lines, child, next, i === kids.length - 1, serviceText))
}

const collectHints = (nodes, hints) => {
  ;(nodes || []).forEach((node) => {
    const bad = /ERROR|WARN/i.test(node.level || '') || (Number(node.durationMs) || 0) >= 400
    if (node.nodeType === 'method' && node.analysis && bad) {
      hints.push(`${simpleName(node.className)}.${blank(node.methodName, '?')}：${node.analysis}`)
    }
    collectHints(node.children, hints)
  })
}

/** 生成可粘贴到工单/群聊的纯文本调用树 */
export const formatShareText = ({ title, extra, durationMs, roots, serviceText = (v) => v }) => {
  const lines = [title || '调用链']
  const bits = [...(extra || []).filter(Boolean), `总耗时 ${durationLabel(durationMs)}`]
  lines.push(bits.join(' · '))
  const list = roots || []
  list.forEach((node, i) => appendNode(lines, node, '', i === list.length - 1, serviceText))
  const hints = []
  collectHints(list, hints)
  if (hints.length) {
    lines.push('', '排障方向')
    hints.forEach((h) => lines.push(`- ${h}`))
  }
  return lines.join('\n')
}

/**
 * 扁平日志 → 三层树。meta 用于标题里的链路号/请求号/会话号。
 */
export const logsToTree = (logs, meta = {}, serviceText = (v) => v) => {
  const rows = fillDurations(logs || [])
  const order = []
  const grouped = new Map()
  rows.forEach((log) => {
    const svc = log.service || 'unknown'
    if (!grouped.has(svc)) {
      grouped.set(svc, [])
      order.push(svc)
    }
    grouped.get(svc).push(log)
  })
  const requestId = meta.requestId || rows.find((x) => x.requestId)?.requestId || ''
  const roots = order.map((svc, i) => buildService(svc, grouped.get(svc), requestId, i + 1))
  const durationMs = roots.reduce((s, n) => s + (Number(n.durationMs) || 0), 0)
  const extra = [
    meta.traceId ? `链路号 ${meta.traceId}` : '',
    requestId ? `请求号 ${requestId}` : '',
    meta.sessionId ? `会话号 ${meta.sessionId}` : ''
  ]
  const shareText = formatShareText({
    title: meta.traceId ? `调用链  链路号 ${meta.traceId}` : (requestId ? `调用链  请求号 ${requestId}` : '调用链'),
    extra,
    durationMs,
    roots,
    serviceText
  })
  return { roots, durationMs, shareText, spanCount: countMethods(roots) }
}

/** 后端已返回 children 时直接用；否则把扁平 spans 再聚一层 */
export const spansToRoots = (spans) => {
  const list = spans || []
  if (!list.length) return []
  if (list.some((s) => (s.children && s.children.length) || s.nodeType)) return list
  const order = []
  const grouped = new Map()
  list.forEach((span) => {
    const svc = span.service || 'unknown'
    if (!grouped.has(svc)) {
      grouped.set(svc, [])
      order.push(svc)
    }
    grouped.get(svc).push(span)
  })
  return order.map((svc, i) => {
    const rows = grouped.get(svc)
    const children = rows.map((span, j) => ({
      spanId: span.spanId || `m-${i}-${j}`,
      parentSpanId: `svc-${i + 1}`,
      nodeType: 'method',
      service: svc,
      className: span.className || svc,
      methodName: span.methodName || span.name,
      name: span.methodName || span.name,
      startTime: span.startTime,
      endTime: span.endTime,
      durationMs: span.durationMs,
      requestId: span.requestId,
      message: span.message || '',
      analysis: span.analysis || '',
      children: []
    }))
    const durationMs = children.reduce((s, n) => s + (Number(n.durationMs) || 0), 0)
    return {
      spanId: `svc-${i + 1}`,
      nodeType: 'service',
      service: svc,
      name: svc,
      startTime: rows[0].startTime,
      endTime: rows[rows.length - 1].endTime || rows[rows.length - 1].startTime,
      durationMs,
      requestId: rows[0].requestId,
      children
    }
  })
}

/** el-tree 要的扁平/嵌套数据：补 label 字段 */
export const toTreeData = (roots, serviceText = (v) => v) => (roots || []).map((node, idx) => ({
  ...node,
  id: node.spanId || `n-${idx}`,
  label: labelOf(node, serviceText),
  children: toTreeData(node.children, serviceText)
}))
