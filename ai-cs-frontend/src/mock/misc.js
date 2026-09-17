/**
 * 补齐型能力的演示数据与拦截逻辑。
 *
 * 覆盖：文件管理、AI 对话扩展（识图/语音/工具链/辅助）、统计补充、
 * 提示词预览、工单流程、多模态问答与检索，以及若干单条详情接口。
 * 与 graphEnhanced / media 一样独立成模块，避免主 mock 文件继续膨胀。
 */

const ok = (data, msg = '操作成功') => ({ code: 200, msg, data })
const fail = (msg, code = 400) => ({ code, msg, data: null })

const tail = (path) => decodeURIComponent(path.split('/').pop() || '')

// ===================== 文件管理 =====================
const files = [
  { fileId: 'f-1001', name: '退款政策说明.pdf', ext: 'pdf', sizeKb: 1284, storageType: 'local', path: '/data/files/f-1001.pdf', uploader: '超级管理员', createTime: '2026-09-10 09:00:00' },
  { fileId: 'f-1002', name: '物流时效对照.xlsx', ext: 'xlsx', sizeKb: 246, storageType: 'local', path: '/data/files/f-1002.xlsx', uploader: '运营管理员', createTime: '2026-09-11 10:30:00' },
  { fileId: 'f-1003', name: '售后话术合集.docx', ext: 'docx', sizeKb: 862, storageType: 'oss', path: 'oss://bucket/f-1003.docx', uploader: '运营管理员', createTime: '2026-09-12 14:20:00' },
  { fileId: 'f-1004', name: '产品图片素材.zip', ext: 'zip', sizeKb: 18420, storageType: 'local', path: '/data/files/f-1004.zip', uploader: '超级管理员', createTime: '2026-09-13 16:45:00' }
]

/** 解压任务 / 任务内文件，key 为 taskId */
const decompressTasks = {
  'task-01': [
    { name: '产品图-01.png', sizeKb: 486, ext: 'png' },
    { name: '产品图-02.png', sizeKb: 512, ext: 'png' },
    { name: '说明.txt', sizeKb: 2, ext: 'txt' }
  ]
}

// ===================== AI 对话扩展 =====================
const aiTools = [
  { toolName: 'query_logistics', description: '查询物流进度', risk: 'read', source: '内置', enabled: 1 },
  { toolName: 'query_account', description: '查询账户状态（脱敏）', risk: 'read', source: '内置', enabled: 1 },
  { toolName: 'refund_apply', description: '发起退款申请', risk: 'write', source: '开放接入', enabled: 1 },
  { toolName: 'change_address', description: '修改收货地址', risk: 'critical', source: '开放接入', enabled: 0 }
]

// ===================== 工单流程 =====================
const flowDefinitions = [
  { definitionId: 'wf-refund', name: '退款审批流', version: 3, nodeCount: 4, deployed: 1, deployTime: '2026-09-01 10:00:00' },
  { definitionId: 'wf-complaint', name: '投诉升级流', version: 2, nodeCount: 5, deployed: 1, deployTime: '2026-08-20 15:30:00' },
  { definitionId: 'wf-simple', name: '简单工单闭环', version: 1, nodeCount: 2, deployed: 1, deployTime: '2026-07-15 09:00:00' }
]

const flowTasks = [
  { taskId: 't-2001', workOrderId: 9001, workOrderNo: 'WO_9001', taskName: '客服初审', assignee: '运营管理员', nodeId: 'userTask1', createTime: '2026-09-10 10:00:00', dueTime: '2026-09-10 18:00:00', status: 'pending' },
  { taskId: 't-2002', workOrderId: 9002, workOrderNo: 'WO_9002', taskName: '财务复核', assignee: '超级管理员', nodeId: 'userTask2', createTime: '2026-09-09 11:05:00', dueTime: '2026-09-09 18:00:00', status: 'pending' },
  { taskId: 't-2000', workOrderId: 9003, workOrderNo: 'WO_9003', taskName: '客服初审', assignee: '运营管理员', nodeId: 'userTask1', createTime: '2026-09-08 09:00:00', dueTime: '2026-09-08 18:00:00', status: 'completed' }
]

/** 每个工单的流程实例状态 */
const flowInstances = {
  9001: { workOrderId: 9001, workOrderNo: 'WO_9001', definitionId: 'wf-refund', processInstanceId: 'pi-88001', currentNode: '客服初审', status: 'running', startTime: '2026-09-10 10:00:00', progress: 25 },
  9002: { workOrderId: 9002, workOrderNo: 'WO_9002', definitionId: 'wf-refund', processInstanceId: 'pi-88002', currentNode: '财务复核', status: 'running', startTime: '2026-09-09 11:05:00', progress: 60 },
  9003: { workOrderId: 9003, workOrderNo: 'WO_9003', definitionId: 'wf-simple', processInstanceId: 'pi-88003', currentNode: '已完成', status: 'completed', startTime: '2026-09-08 09:00:00', progress: 100 }
}

/** 流程历史：按 workOrderId 存 */
const flowHistory = {
  9001: [
    { time: '2026-09-10 10:00:00', node: '开始', assignee: '系统', action: '流程启动', comment: '由客服提交退款申请' },
    { time: '2026-09-10 10:00:05', node: '客服初审', assignee: '运营管理员', action: '待处理', comment: '' }
  ],
  9003: [
    { time: '2026-09-08 09:00:00', node: '开始', assignee: '系统', action: '流程启动', comment: '' },
    { time: '2026-09-08 09:30:00', node: '客服初审', assignee: '运营管理员', action: '通过', comment: '情况属实，同意退款' },
    { time: '2026-09-08 09:31:00', node: '结束', assignee: '系统', action: '流程完成', comment: '' }
  ]
}

// ===================== 统计补充 =====================
const statisticsWorkOrder = {
  byStatus: [
    { status: '1', label: '待处理', count: 12 },
    { status: '2', label: '处理中', count: 28 },
    { status: '3', label: '已完成', count: 186 },
    { status: '4', label: '已关闭', count: 34 }
  ],
  byType: [
    { type: '咨询', count: 142 },
    { type: '投诉', count: 48 },
    { type: '建议', count: 70 }
  ],
  avgHandleHours: 3.6,
  slaHitRate: 94.2
}

// ===================== 详情兜底 =====================
/** 单条详情演示数据：不同资源的字段不同，这里按路径关键字给一套合理结构 */
const detailOf = (kind, id) => {
  const base = { id: Number(id) || id }
  if (kind === 'customer') {
    return { ...base, phone: '13800138000', email: 'demo@example.com', nickname: '演示客户', gender: 1, customerTag: 'VIP', sessionCount: 6, workOrderCount: 2, createTime: '2026-08-01 09:00:00' }
  }
  if (kind === 'workorder') {
    return { ...base, orderNo: `WO_${id}`, orderType: '咨询', orderContent: '查询订单 SO10086 物流', orderStatus: 2, assignee: '运营管理员', createTime: '2026-09-10 10:00:00' }
  }
  if (kind === 'intent') {
    return { ...base, intentCode: 'QUERY_LOGISTICS', intentName: '查询物流', intentType: 'business', keywords: ['物流', '到哪了', '快递'], examples: ['帮我查下物流', '快递到哪了'] }
  }
  if (kind === 'slot') {
    return { ...base, slotCode: 'order_no', slotName: '订单号', required: 1, question: '请提供订单号，我帮您查询', validateRule: '^SO\\d{6,}$' }
  }
  if (kind === 'faq') {
    return { ...base, question: '如何退款？', answer: '提交售后申请后 1-3 个工作日到账。', category: '售后', status: 1, vectorized: 1, viewCount: 88 }
  }
  if (kind === 'multimodal') {
    return { ...base, mediaType: 'image', title: '退款流程示意图', description: '说明退款各环节与时效', vectorized: 1, tags: ['售后', '示意图'] }
  }
  return base
}

/**
 * 补齐型能力拦截。
 * @returns 命中返回响应对象，未命中返回 null。
 */
export function handleMisc(method, path, p) {
  // ---------- 文件管理 ----------
  if (path === '/file/list') {
    const kw = String(p.keyword || '').trim().toLowerCase()
    return ok(kw ? files.filter((f) => f.name.toLowerCase().includes(kw)) : [...files])
  }
  if (path === '/file/info') {
    const row = files.find((f) => f.fileId === p.fileId)
    return row ? ok(row) : fail('文件不存在')
  }
  if (path === '/file/preview') {
    const row = files.find((f) => f.fileId === p.fileId) || files[0]
    return ok({ fileId: row.fileId, name: row.name, ext: row.ext, previewType: row.ext === 'pdf' ? 'pdf' : 'text', previewUrl: `/api/file/download?fileId=${row.fileId}` })
  }
  if (path.startsWith('/file/preview/')) {
    const fileId = tail(path)
    const row = files.find((f) => f.fileId === fileId) || files[0]
    return ok({ fileId: row.fileId, name: row.name, ext: row.ext, previewUrl: `/api/file/download?fileId=${row.fileId}` })
  }
  if (path === '/file/download') {
    const row = files.find((f) => f.fileId === p.fileId) || files[0]
    return ok({ fileId: row.fileId, name: row.name, url: `/data/files/${row.fileId}.${row.ext}`, expiresIn: 3600 })
  }
  if (path === '/file/download/range') {
    return ok({ fileId: p.fileId || 'f-1004', rangeStart: 0, rangeEnd: 1048575, contentLength: 1048576, hasMore: true })
  }
  if (path === '/file/download/batch') {
    const ids = Array.isArray(p.fileIds) ? p.fileIds : []
    return ok({ packageName: `files_${Date.now()}.zip`, fileCount: ids.length || files.length, downloadUrl: '/api/file/download?fileId=package-demo' }, '打包任务已提交（演示）')
  }
  if (path === '/file/decompress' || path === '/file/decompress/local') {
    const taskId = 'task-01'
    return ok({ taskId, fileCount: (decompressTasks[taskId] || []).length, totalSizeKb: 1000 }, '解压完成（演示）')
  }
  if (path === '/file/decompress/contents') {
    const taskId = p.taskId || 'task-01'
    return ok(decompressTasks[taskId] || [])
  }
  if (path.startsWith('/file/decompressed/list/')) {
    return ok(decompressTasks[tail(path)] || [])
  }
  if (path.startsWith('/file/decompressed/preview/')) {
    const list = decompressTasks[tail(path)] || []
    const row = list.find((x) => x.name === p.name) || list[0]
    return row ? ok({ ...row, previewType: row.ext === 'txt' ? 'text' : 'binary', content: row.ext === 'txt' ? '这是解压出来的文本说明（演示内容）。' : '' }) : fail('文件不在该任务内')
  }

  // ---------- AI 对话扩展 ----------
  if (path === '/ai/tools/list') return ok(aiTools)
  if (path === '/ai/tools/cache/clear') return ok('工具缓存已清空（演示）')
  if (path === '/ai/tools/chain') {
    const names = Array.isArray(p.toolNames) ? p.toolNames : (p.toolNames ? String(p.toolNames).split(',') : [])
    if (!names.length) return fail('请选择要串联的工具')
    return ok({
      chain: names,
      steps: names.map((n, i) => ({
        step: i + 1,
        tool: n,
        success: 1,
        output: i === names.length - 1 ? '物流状态：运输中，预计明日送达' : `已执行 ${n}`,
        elapsedMs: 320 + i * 110
      })),
      totalElapsedMs: 320 + names.length * 110
    }, '工具链执行完成（演示）')
  }
  if (path === '/ai/chat/image') {
    return ok({
      answer: '图里是一张退款申请截图，订单号 SO10086，申请金额 ￥128.00。需要我帮您提交退款吗？',
      recognized: { type: 'screenshot', text: '订单编号：SO10086　退款金额：￥128.00' },
      confidence: 0.9
    }, '识图完成（演示）')
  }
  if (path === '/ai/chat/speech-to-text') {
    return ok({ text: '您好，我想问一下退款大概多久能到账？', language: 'zh', duration: 6, confidence: 0.93 }, '转写完成（演示）')
  }
  if (path === '/ai/chat/text-to-speech') {
    return ok({ audioUrl: '/api/audio/download/aud-3001', format: 'mp3', duration: 8, voice: p.voice || 'female_zh' }, '语音合成完成（演示）')
  }
  if (path === '/ai/assist/recommend') {
    return ok({
      recommendations: [
        '如何查询订单物流？',
        '退款多久到账？',
        '可以修改收货地址吗？',
        '发票能补开吗？'
      ]
    })
  }
  if (path === '/ai/assist/summary') {
    return ok({
      summary: '客户咨询退款到账时间，已告知微信/支付宝 1-3 个工作日、银行卡 3-7 个工作日；客户接受，未转人工。',
      keywords: ['退款', '到账时效'],
      sentiment: 'neutral',
      needFollowUp: false
    })
  }
  if (path === '/ai/assist/next-sentence') {
    return ok({
      suggestions: [
        '请问还有其他可以帮您的吗？',
        '需要我帮您提交退款申请吗？',
        '如果超时未到账，可以为您提供交易流水核查。'
      ]
    })
  }
  if (path === '/ai/flow/step') {
    return ok({
      step: p.step || 1,
      node: p.step >= 3 ? '结束' : '客服初审',
      status: 'ok',
      nextActions: ['继续', '转人工', '结束会话'],
      elapsedMs: 860
    })
  }
  if (path.startsWith('/ai/industry-pack/')) {
    const code = tail(path)
    const PACK = {
      ecommerce: { code: 'ecommerce', name: '电商', tools: ['query_logistics', 'refund_apply'], intents: ['QUERY_LOGISTICS', 'REFUND'], scenes: ['ORDER', 'AFTER_SALE'] },
      finance: { code: 'finance', name: '金融', tools: ['query_account'], intents: ['QUERY_ACCOUNT'], scenes: ['GENERAL'] }
    }
    return PACK[code] ? ok(PACK[code]) : fail(`行业包不存在：${code}`)
  }

  // ---------- 统计补充 ----------
  if (path === '/statistics/overview') {
    return ok({ todaySessions: 128, todayWorkOrders: 16, todayCustomers: 24, weekSessions: 862, monthSessions: 3720, aiDeflectRate: 73.6 })
  }
  if (path === '/statistics/workorder-stats') return ok(statisticsWorkOrder)
  if (path === '/statistics/chat-trend' || path === '/statistics/customer-trend') {
    const days = Number(p.days || 14)
    return ok(Array.from({ length: days }, (_, i) => {
      const d = new Date()
      d.setDate(d.getDate() - (days - 1 - i))
      const date = `${d.getMonth() + 1}/${d.getDate()}`
      return path.includes('chat')
        ? { date, count: 80 + Math.round(Math.sin(i / 2) * 30) + i * 4 }
        : { date, count: 12 + (i % 5) * 3 }
    }))
  }

  // ---------- 提示词预览 ----------
  if (path === '/prompt/preview/system') {
    const text = '你是专业客服助手，负责电商售后咨询。\n回答必须依据知识库内容，检索不到时引导转人工。\n回答不超过 50 字。'
    return ok({ systemPrompt: text, length: text.length })
  }
  if (path === '/prompt/preview/full') {
    const systemPrompt = '你是专业客服助手，负责电商售后咨询。'
    const userPrompt = `以下是知识库检索到的内容：\n【如何退款？】提交售后申请后 1-3 个工作日到账。\n\n用户问题：${p.question || '退款多久到账'}`
    return ok({ systemPrompt, userPrompt, fullPrompt: `${systemPrompt}\n\n---\n\n${userPrompt}`, totalLength: systemPrompt.length + userPrompt.length, contextDocCount: 1 })
  }
  if (path === '/prompt/preview/messages') {
    const messages = [
      { role: 'system', content: '你是专业客服助手，负责电商售后咨询。' },
      { role: 'user', content: `知识库：提交售后申请后 1-3 个工作日到账。\n问题：${p.question || '退款多久到账'}` }
    ]
    return ok({ messages, messageCount: messages.length, totalLength: messages.reduce((s, m) => s + m.content.length, 0) })
  }
  if (path.startsWith('/prompt/presets/')) {
    const name = tail(path)
    return ok({
      name, systemRole: '专业客服助手', roleDescription: '耐心、准确，不确定时引导转人工', roleDomain: '电商售后',
      outputFormat: 'markdown', strictOutput: true, cotEnabled: false, cotInstruction: '',
      fewShotEnabled: false, boundaryEnabled: true, noDataReply: '抱歉，我暂时没有找到相关信息。', noFabrication: true, contextOnlyReply: true
    })
  }

  // ---------- 工单流程 ----------
  if (path === '/workorder/flow/definitions') return ok(flowDefinitions)
  if (path === '/workorder/flow/tasks') {
    const st = p.status
    return ok(st ? flowTasks.filter((t) => t.status === st) : [...flowTasks])
  }
  if (path.startsWith('/workorder/flow/status/')) {
    const id = tail(path)
    if (!flowInstances[id]) return fail('该工单尚未启动流程')
    return ok(flowInstances[id])
  }
  if (path.startsWith('/workorder/flow/history/')) {
    const id = tail(path)
    return ok(flowHistory[id] || [])
  }
  if (path.startsWith('/workorder/flow/start/')) {
    const id = tail(path)
    if (!flowInstances[id]) {
      flowInstances[id] = {
        workOrderId: Number(id) || id, workOrderNo: `WO_${id}`, definitionId: p.definitionId || 'wf-refund',
        processInstanceId: `pi-${Date.now() % 100000}`, currentNode: '客服初审', status: 'running',
        startTime: '2026-09-15 12:00:00', progress: 25
      }
      flowHistory[id] = [{ time: '2026-09-15 12:00:00', node: '开始', assignee: '系统', action: '流程启动', comment: p.comment || '' }]
      flowTasks.unshift({
        taskId: `t-${Date.now() % 100000}`, workOrderId: Number(id) || id, workOrderNo: `WO_${id}`,
        taskName: '客服初审', assignee: '运营管理员', nodeId: 'userTask1',
        createTime: '2026-09-15 12:00:00', dueTime: '2026-09-15 18:00:00', status: 'pending'
      })
    }
    return ok(flowInstances[id], '流程已启动（演示）')
  }
  if (path.startsWith('/workorder/flow/cancel/')) {
    const id = tail(path)
    const inst = flowInstances[id]
    if (!inst) return fail('该工单尚未启动流程')
    if (inst.status === 'completed') return fail('流程已完成，无法取消')
    inst.status = 'cancelled'
    inst.currentNode = '已取消'
    ;(flowHistory[id] || (flowHistory[id] = [])).push({ time: '2026-09-15 12:30:00', node: inst.currentNode, assignee: '操作人', action: '取消流程', comment: p.comment || '' })
    return ok(inst, '流程已取消（演示）')
  }
  if (path.startsWith('/workorder/flow/complete/')) {
    const taskId = tail(path)
    const task = flowTasks.find((t) => t.taskId === taskId)
    if (!task) return fail('任务不存在')
    if (task.status === 'completed') return fail('该任务已处理过')
    task.status = 'completed'
    task.comment = p.comment || ''
    task.completeTime = '2026-09-15 12:10:00'
    return ok(task, '任务已完成（演示）')
  }

  // ---------- 多模态问答与检索 ----------
  if (path.startsWith('/multimodal-knowledge/') && path.endsWith('/qa')) {
    const kind = path.includes('/image/') ? '图片' : path.includes('/audio/') ? '音频' : path.includes('/video/') ? '视频' : '混合'
    return ok({
      answer: `（${kind}问答演示）图里是退款申请截图，订单 SO10086，金额 ￥128.00，符合 7 天无理由条件，可提交退款。`,
      confidence: 0.88,
      references: ['img-2001', 'aud-3001']
    })
  }
  if (path === '/multimodal-knowledge/hybrid-search' || path === '/multimodal-knowledge/cross-modal-search') {
    return ok({
      query: p.query || '退款',
      hits: [
        { knowledgeId: 'img-2001', mediaType: 'image', title: '退款流程示意图', score: 0.92, matchType: '图' },
        { knowledgeId: 'aud-3001', mediaType: 'audio', title: '退款政策播报', score: 0.87, matchType: '音' },
        { knowledgeId: 'faq-1001', mediaType: 'text', title: '如何退款？', score: 0.85, matchType: '文' }
      ]
    })
  }
  if (path === '/multimodal-knowledge/multimodal-chat') {
    return ok({ answer: '结合您发的截图和描述，这笔订单可以申请退款，预计 1-3 个工作日到账。', confidence: 0.9, usedModalities: ['image', 'text'] })
  }
  if (path.startsWith('/multimodal-knowledge/detail/')) {
    return ok(detailOf('multimodal', tail(path)))
  }
  if (method === 'delete' && path.startsWith('/multimodal-knowledge/delete/')) {
    return ok('删除成功')
  }
  if (path === '/multimodal-knowledge/video/analyze') {
    return ok({
      duration: 128,
      scenes: [
        { start: 0, end: 12, label: '片头品牌展示' },
        { start: 12, end: 68, label: '退款流程演示' },
        { start: 68, end: 128, label: '常见问题答疑' }
      ],
      summary: '视频介绍了退款全流程：申请 → 审核 → 到账，并说明各渠道到账时效差异。',
      hasSubtitle: true
    }, '视频分析完成（演示）')
  }
  if (path === '/multimodal/search' || path === '/multimodal/cross-search') {
    return ok([
      { knowledgeId: 'img-2001', mediaType: 'image', title: '退款流程示意图', score: 0.92 },
      { knowledgeId: 'aud-3001', mediaType: 'audio', title: '退款政策播报', score: 0.86 }
    ])
  }
  if (path === '/multimodal/qa') {
    return ok({ answer: '（多模态检索问答演示）退款 1-3 个工作日到账，图示与音频均说明了这一点。', confidence: 0.87 })
  }
  if (path === '/multimodal/stats') {
    return ok({ imageCount: 14, audioCount: 6, videoCount: 2, vectorCount: 22, searchCount: 386, avgElapsedMs: 240 })
  }

  // ---------- 零散单条详情 ----------
  // 这几条用排除式正则：/{id} 不能把 list / save 这类固定路径也吃掉
  if (/^\/customer\/(?!list$|page$|save$|update$|export$|import$)[^/]+$/.test(path)) {
    return ok(detailOf('customer', tail(path)))
  }
  if (/^\/workorder\/info\/[^/]+$/.test(path)) {
    return ok(detailOf('workorder', tail(path)))
  }
  if (path === '/workorder/field/page') {
    return ok({ records: [
      { id: 1, fieldKey: 'orderNo', fieldName: '关联订单号', fieldType: 'text', required: 1, status: 1, sortNum: 10 },
      { id: 2, fieldKey: 'refundAmount', fieldName: '退款金额', fieldType: 'number', required: 1, status: 1, sortNum: 20 },
      { id: 3, fieldKey: 'refundReason', fieldName: '退款原因', fieldType: 'select', required: 0, status: 1, sortNum: 30 }
    ], total: 3, current: 1, size: 10 })
  }
  if (/^\/system\/intent\/(?!list$|listAll$|save$|update$|delete$)[^/]+$/.test(path)) {
    return ok(detailOf('intent', tail(path)))
  }
  if (/^\/system\/slot\/(?!list$|listAll$|save$|update$|delete$)[^/]+$/.test(path)) {
    return ok(detailOf('slot', tail(path)))
  }
  if (/^\/knowledge\/faq\/[^/]+$/.test(path)) return ok(detailOf('faq', tail(path)))
  if (path === '/knowledge/vectorize/increment') {
    return ok({ vectorized: Number(p.limit || 50), remaining: 12 }, '增量向量化完成（演示）')
  }
  if (path.match(/^\/session\/[^/]+\/snapshot$/)) {
    const sessionId = path.split('/')[2]
    return ok({
      sessionId, customerName: '演示客户', status: 'active', messageCount: 12,
      lastMessage: '好的，谢谢', transferred: 0, createTime: '2026-09-15 09:30:00',
      recentMessages: [
        { role: 'user', content: '退款多久到账？', time: '2026-09-15 09:31:00' },
        { role: 'ai', content: '微信/支付宝 1-3 个工作日，银行卡 3-7 个工作日。', time: '2026-09-15 09:31:05' }
      ]
    })
  }
  if (path === '/session/merge-anonymous') {
    if (!p.visitorRef) return fail('请提供访客标识')
    return ok({ mergedSessions: 2, mergedMessages: 18, customerId: p.customerId || 1 }, '匿名会话已合并（演示）')
  }
  if (path === '/ops/health/self') {
    return ok({ status: 'UP', service: 'ops', uptimeSeconds: 86400, jvmHeapUsedMb: 320, cpuUsage: 0.12, checks: [
      { name: 'base-service', status: 'UP' },
      { name: 'knowledge', status: 'UP' },
      { name: 'agent', status: 'UP' },
      { name: 'redis', status: 'UP' },
      { name: 'mysql', status: 'UP' }
    ] })
  }
  if (path.startsWith('/system/config/get/')) {
    const key = path.split('/system/config/get/')[1]
    return ok({ configKey: key, configValue: key === 'site.name' ? '智能客服' : '35' })
  }
  if (path === '/system/data-retention/get') {
    return ok({ sessionDays: 180, chatMsgDays: 180, operationLogDays: 365, workOrderDays: 730, autoClean: 1, nextCleanTime: '2026-09-16 02:00:00' })
  }
  if (path.match(/^\/document\/version\/current\/[^/]+$/)) {
    const documentId = tail(path)
    return ok({ documentId, versionId: 'v3', version: 3, sizeKb: 1284, chunkCount: 42, createTime: '2026-09-10 09:00:00', current: 1 })
  }
  if (path === '/rag/memory/clear/user') {
    return ok({ cleared: 1 }, '该用户会话记忆已清理（演示）')
  }
  if (path === '/rag/memory/clear/all') {
    return ok({ cleared: 128 }, '全部会话记忆已清理（演示）')
  }
  if (path === '/open/connector/mapping/batchSave') {
    const list = Array.isArray(p.mappings) ? p.mappings : []
    return ok({ saved: list.length || 3 }, '字段映射已保存（演示）')
  }
  if (path === '/open/openapi/listByTenant') {
    return ok([
      { id: 1, name: '订单服务 OpenAPI', version: 'v2', toolCount: 6, status: 'success', tenantCode: p.tenantCode || 'default' },
      { id: 2, name: '物流服务 OpenAPI', version: 'v1', toolCount: 3, status: 'success', tenantCode: p.tenantCode || 'default' }
    ])
  }
  if (path === '/open/card/listByTenant' || path === '/open/card/listByPack') {
    return ok([
      { id: 1, cardCode: 'ORDER_CARD', cardName: '订单卡片', packCode: 'ecommerce', tenantCode: p.tenantCode || 'default', status: 1 },
      { id: 2, cardCode: 'LOGISTICS_CARD', cardName: '物流卡片', packCode: 'ecommerce', tenantCode: p.tenantCode || 'default', status: 1 }
    ])
  }
  if (path === '/open/webhook/inbound/listByTenant' || path === '/open/webhook/outbound/listByTenant') {
    const inbound = path.includes('/inbound/')
    return ok([
      { id: 1, name: inbound ? '订单状态推送' : '工单创建通知', eventType: inbound ? 'order.status' : 'workorder.create', tenantCode: p.tenantCode || 'default', enabled: 1, secretSet: 1 }
    ])
  }
  if (path === '/open/prompt/pack/listByPack' || path === '/open/prompt/pack/listByPackAndType') {
    return ok([
      { id: 1, packCode: p.packCode || 'ecommerce', type: p.type || 'system', name: '电商客服系统提示词', content: '你是电商售后客服…', status: 1 },
      { id: 2, packCode: p.packCode || 'ecommerce', type: p.type || 'system', name: '电商拒答话术', content: '这个问题我帮不上忙…', status: 1 }
    ])
  }
  if (path === '/open/prompt/pack/batchImport') {
    return ok({ total: 3, success: 3, failed: 0 }, '提示词包批量导入完成（演示）')
  }
  if (path === '/system/skill-group/page') {
    return ok({ records: [
      { id: 1, groupCode: 'GROUP_AFTER_SALE', groupName: '售后组', skillDesc: '售后 / 退款 / 物流', agentCount: 3, dataScope: 'GROUP', status: 1 },
      { id: 2, groupCode: 'GROUP_PRESALE', groupName: '售前咨询组', skillDesc: '商品 / 下单 / 优惠', agentCount: 2, dataScope: 'GROUP', status: 1 }
    ], total: 2, current: 1, size: 10 })
  }
  if (path.match(/^\/system\/skill-group\/[^/]+\/data-scope$/)) {
    return ok(null, `数据权限已调整为 ${p.dataScope}（演示）`)
  }

  // ---------- RAG 快速入库（不建版本记录）----------
  // 与 /rag/upload/*/versioned 的区别：那条会写版本历史，这条只入库。
  // 返回结构与带版本的一致，前端两条链路可以共用同一段读取逻辑。
  if (path === '/rag/upload/pdf' || path === '/rag/upload/file') {
    const documentId = p.documentId || `doc-${Date.now()}`
    const isPdf = path.endsWith('/pdf')
    return ok({
      documentId,
      documentName: p.documentName || (isPdf ? '未命名文档.pdf' : '未命名文档.txt'),
      version: 1,
      segmentCount: isPdf ? 42 : 18,
      fileSize: isPdf ? 512000 : 46080,
      status: 'completed'
    }, '已入库（演示）')
  }

  // ---------- RAG 问答（旁路接口，按 userId 隔离会话记忆）----------
  if (method === 'post' && path === '/rag/chat') {
    const q = String(p.question || p.message || '').trim()
    if (!q) return fail('请填写问题')
    return ok({
      question: q,
      answer: `（演示回答）关于「${q}」，知识库中最相关的是「如何退款？」与「物流多久送达？」两条，均已标注依据来源。`,
      references: ['如何退款？', '物流多久送达？'],
      confidence: 0.82,
      sessionId: p.sessionId || `rag-${Date.now()}`
    })
  }

  // ---------- 运维：单条链路详情 ----------
  // 列表接口已覆盖，详情供后续页面下钻使用；结构与列表项保持一致再加调用步骤
  if (path.startsWith('/ops/traces/')) {
    const traceId = decodeURIComponent(path.replace(/^\/ops\/traces\//, '')) || 'trace-demo-1'
    const idx = Number(String(traceId).replace(/\D/g, '')) || 1
    return ok({
      traceId,
      sessionId: `sess_demo_${1000 + idx}`,
      requestId: `req-demo-${idx}`,
      spanCount: 14,
      durationMs: 1720,
      startTime: '2026-09-15 10:20:00',
      endTime: '2026-09-15 10:20:02',
      hint: '当前为演示链路，点「查看调用树」逐步展开服务 / 类 / 方法。'
    })
  }

  // ---------- 系统管理：单个用户详情 ----------
  // 供「编辑用户 / 查看详情」下钻；列表接口在 mock 主文件里
  if (path.startsWith('/system/user/info/')) {
    const id = Number(tail(path)) || 1
    return ok({
      id,
      username: ['admin', 'operator', 'agent001'][(id - 1) % 3],
      realName: ['超级管理员', '运营管理员', '演示坐席'][(id - 1) % 3],
      phone: '138****' + String(1000 + id).slice(-4),
      email: `user${id}@example.com`,
      roleIds: id === 1 ? [1] : [2],
      tenantCode: 'default',
      status: 1,
      createTime: '2026-08-01 09:00:00',
      lastLoginTime: '2026-09-15 08:30:00'
    })
  }

  return null
}
