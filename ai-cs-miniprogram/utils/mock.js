/**
 * 本地演示数据：结构与后端 Result 一致，便于不启动 Java 服务也能走通页面。
 * 与 Web 端 src/mock/index.js 保持同一套演示口径。
 */
const ok = (data, msg = '操作成功') => ({ code: 200, msg, data })

const now = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const faqs = [
  { id: 1, category: '售后', question: '如何退款？', answer: '提交售后申请后 1-3 个工作日原路退回。', likeCount: 12, viewCount: 88 },
  { id: 2, category: '物流', question: '物流多久送达？', answer: '江浙沪 48 小时，其他地区 3-5 天。', likeCount: 9, viewCount: 64 },
  { id: 3, category: '订单', question: '怎么修改收货地址？', answer: '发货前可在订单详情修改一次地址。', likeCount: 3, viewCount: 21 }
]

const sessions = [
  { sessionId: 'sess_demo_1001', sessionStatus: 1, startTime: '2026-09-10 20:01:00' },
  { sessionId: 'sess_demo_1002', sessionStatus: 2, startTime: '2026-09-09 11:00:00' }
]

const messages = {
  sess_demo_1001: [
    { id: 11, msgType: 1, msgContent: '我的订单怎么还没发货？', createTime: '2026-09-10 20:01:10' },
    { id: 12, msgType: 2, msgContent: '请提供订单号，我帮您查物流进度。', createTime: '2026-09-10 20:01:12' }
  ],
  sess_demo_1002: [
    { id: 21, msgType: 1, msgContent: '要退款', createTime: '2026-09-09 11:00:10' },
    { id: 22, msgType: 2, msgContent: '已为您登记退款工单 WO_9002。', createTime: '2026-09-09 11:05:00' }
  ]
}

const workOrders = [
  { id: 1, orderNo: 'WO_9001', orderType: '咨询', orderContent: '查询订单 SO-10086 物流', orderStatus: 2, createTime: '2026-09-10 20:10:00' },
  { id: 2, orderNo: 'WO_9002', orderType: '投诉', orderContent: '退款未到账', orderStatus: 1, createTime: '2026-09-09 11:06:00' },
  { id: 3, orderNo: 'WO_9003', orderType: '建议', orderContent: '希望增加夜间客服', orderStatus: 3, createTime: '2026-09-08 16:00:00' }
]

/** 文档知识库演示数据：同一 documentId 多行即多版本，isCurrent=1 为当前版本 */
const docVersions = [
  { id: 101, documentId: 'doc-refund', documentName: '退款政策说明.pdf', version: 1, fileType: 'PDF', fileSize: 486000, segmentCount: 41, versionDescription: '首版发布', isCurrent: 0, uploaderName: '超级管理员', createTime: '2026-08-20 14:02:00', updateTime: '2026-08-20 14:02:00' },
  { id: 102, documentId: 'doc-refund', documentName: '退款政策说明.pdf', version: 2, fileType: 'PDF', fileSize: 512000, segmentCount: 48, versionDescription: '补充跨境退款条款', isCurrent: 1, uploaderName: '超级管理员', createTime: '2026-09-10 09:30:00', updateTime: '2026-09-10 09:30:00' },
  { id: 103, documentId: 'doc-logistics', documentName: '物流时效说明.docx', version: 1, fileType: 'DOCX', fileSize: 128400, segmentCount: 26, versionDescription: '首版发布', isCurrent: 1, uploaderName: '张敏', createTime: '2026-09-11 16:45:00', updateTime: '2026-09-11 16:45:00' },
  { id: 104, documentId: 'doc-aftermarket', documentName: '售后处理规范.md', version: 1, fileType: 'MD', fileSize: 45200, segmentCount: 18, versionDescription: '首版发布', isCurrent: 1, uploaderName: '李蕾', createTime: '2026-09-12 11:08:00', updateTime: '2026-09-12 11:08:00' }
]

const replyOf = (q) => `【演示】关于「${q || '您的问题'}」：7 天无理由可退，提交后 1-3 个工作日到账。如仍不清楚可转人工客服。`

/** 路径去掉查询串与 /api 前缀，便于匹配 */
const clean = (url) => String(url || '').replace(/^https?:\/\/[^/]+/, '').replace(/^\/api/, '').split('?')[0]

function handle(method, url, data) {
  const path = clean(url)
  const m = String(method || 'get').toLowerCase()
  const body = data || {}

  return new Promise((resolve) => {
    setTimeout(() => {
      if (m === 'post' && path === '/auth/login/wechat') {
        resolve(ok({
          token: 'mock-token-wechat',
          username: 'wx_user',
          realName: '微信用户',
          loginType: 'platform',
          roles: ['CUSTOMER']
        }, '微信登录成功'))
        return
      }
      if (m === 'post' && (path === '/auth/login' || path === '/auth/login/platform')) {
        resolve(ok({
          token: 'mock-token-miniprogram',
          username: '游客用户',
          realName: '微信用户',
          loginType: 'platform',
          roles: ['CUSTOMER']
        }, '登录成功'))
        return
      }
      if (path === '/ai/chat/send' || path === '/ai/chat') {
        const question = body.msg || body.question || body.content || ''
        const base = {
          reply: replyOf(question),
          sessionId: body.sessionId || 'sess_demo_1001'
        }
        // 演示转人工：带上坐席信息，便于前端展示接待卡
        if (question.indexOf('转人工') >= 0) {
          Object.assign(base, {
            transferred: true,
            intent: '转人工',
            agentId: 2,
            agentNo: 'A002',
            agentName: '张晓梅'
          })
        }
        resolve(ok(base))
        return
      }
      // 对话附件上传（演示）：返回附件元信息，供前端预览与随消息发送
      if (path === '/file/chat-attachment') {
        const fileName = (body.file && body.file.name) || 'demo-attachment.png'
        const isImage = /\.(png|jpe?g|gif|webp|bmp)$/i.test(fileName)
        resolve(ok({
          fileId: 'att_' + Date.now(),
          url: '/files/chat/demo-' + Date.now() + (isImage ? '.png' : '.dat'),
          fileName,
          category: isImage ? 'image' : 'document',
          contentType: isImage ? 'image/png' : 'application/octet-stream',
          fileSize: 204800,
          parseStatus: 0
        }))
        return
      }
      if (path === '/help-center/search') {
        resolve(ok({ reply: replyOf(body.question), citations: [{ faqId: 1, question: '如何退款？' }] }))
        return
      }
      if (path === '/help-center/categories') {
        resolve(ok(['售后', '物流', '订单']))
        return
      }
      if (path === '/help-center/faqs') {
        const cat = body.category
        resolve(ok(cat ? faqs.filter((f) => f.category === cat) : faqs))
        return
      }
      if (path.indexOf('/help-center/feedback/') === 0) {
        resolve(ok('反馈成功'))
        return
      }
      if (path.indexOf('/help-center/view/') === 0) {
        resolve(ok('ok'))
        return
      }
      if (path === '/session/list' || path === '/session/page') {
        resolve(ok(sessions))
        return
      }
      if (/^\/session\/[^/]+\/messages$/.test(path)) {
        const sid = path.split('/')[2]
        resolve(ok(messages[sid] || []))
        return
      }
      if (path === '/session/message' && m === 'post') {
        resolve(ok('已保存'))
        return
      }
      if (path === '/workorder/page') {
        resolve(ok({ records: workOrders, total: workOrders.length, current: 1, size: 10 }))
        return
      }
      if (path === '/workorder/create') {
        resolve(ok('WO_' + Date.now().toString().slice(-4)))
        return
      }

      // ===== 管理台：数据概览 =====
      if (path === '/statistics/dashboard') {
        resolve(ok({
          totalSessions: 1280, totalCustomers: 386, totalWorkOrders: 96,
          aiHandled: 942, transferHuman: 338, satisfaction: 96.2,
          trend: [
            { date: '09-05', count: 168 }, { date: '09-06', count: 192 }, { date: '09-07', count: 155 },
            { date: '09-08', count: 210 }, { date: '09-09', count: 244 }, { date: '09-10', count: 198 }, { date: '09-11', count: 231 }
          ]
        }))
        return
      }

      // ===== 管理台：报表 =====
      if (path === '/report/overview') {
        resolve(ok({ inbound: 1280, aiHandled: 942, transferHuman: 338, aiDeflectRate: 73.6, avgFirstReplySeconds: 6, avgHandleSeconds: 214, satisfactionRate: 96.2 }))
        return
      }
      if (path === '/report/csat') {
        resolve(ok({ csat: 4.72, nps: 42, evaluated: 386, satisfied: 371, dissatisfied: 15 }))
        return
      }
      if (path === '/report/sla') {
        resolve(ok({
          frtSeconds: 6, ahtSeconds: 214, slaTargetSeconds: 30, slaHitRate: 98.4,
          rows: [
            { group: '售后组', frtSeconds: 4, ahtSeconds: 226, slaHitRate: 99.1 },
            { group: '售前咨询组', frtSeconds: 7, ahtSeconds: 198, slaHitRate: 97.6 },
            { group: '金融专席', frtSeconds: 9, ahtSeconds: 241, slaHitRate: 95.8 }
          ]
        }))
        return
      }
      if (path === '/report/trend') {
        resolve(ok([
          { date: '09-05', count: 168 }, { date: '09-06', count: 192 }, { date: '09-07', count: 155 }, { date: '09-08', count: 210 }
        ]))
        return
      }

      // ===== 管理台：排队监控 =====
      if (path === '/queue/list') {
        resolve(ok([
          { sessionId: 'sess_demo_2001', customerName: '测试用户1', lastMessage: '退款未到账', skillGroup: '售后组', waitSeconds: 42, priority: 1, enqueueTime: '2026-09-11 10:01:00' },
          { sessionId: 'sess_demo_2002', customerName: '小王', lastMessage: '物流到哪了', skillGroup: '售后组', waitSeconds: 96, priority: 2, enqueueTime: '2026-09-11 10:00:10' },
          { sessionId: 'sess_demo_2003', customerName: '物流咨询客', lastMessage: '要开发票', skillGroup: '售前咨询组', waitSeconds: 15, priority: 1, enqueueTime: '2026-09-11 10:02:30' }
        ]))
        return
      }
      if (path === '/queue/monitor') {
        resolve(ok({ waiting: 3, avgWaitSeconds: 51, maxWaitSeconds: 96, onlineAgents: 6, busyAgents: 4, idleAgents: 2, assignToday: 37, abandonToday: 2 }))
        return
      }

      // ===== 管理台：多租户 =====
      if (path === '/system/tenant/list') {
        resolve(ok([
          { id: 1, tenantCode: 'default', tenantName: '默认租户', planCode: 'BASIC', status: 1, createTime: '2026-08-01 09:00:00', expireTime: '2026-12-31 23:59:59' },
          { id: 2, tenantCode: 'ecommerce', tenantName: '电商旗舰店', planCode: 'PRO', status: 1, createTime: '2026-08-15 10:00:00', expireTime: '2027-08-15 23:59:59' },
          { id: 3, tenantCode: 'finance', tenantName: '金融事业部', planCode: 'ENTERPRISE', status: 1, createTime: '2026-09-01 14:20:00', expireTime: '2027-09-01 23:59:59' },
          { id: 4, tenantCode: 'retail', tenantName: '新零售试点', planCode: 'TRIAL', status: 0, createTime: '2026-09-08 16:30:00', expireTime: '2026-09-30 23:59:59' }
        ]))
        return
      }

      // ===== 管理台：技能组 =====
      if (path === '/system/skill-group/list') {
        resolve(ok([
          { id: 1, groupCode: 'GROUP_AFTER_SALE', groupName: '售后组', skillDesc: '售后 / 退款 / 物流', agentCount: 3, status: 1, dataScope: 'GROUP' },
          { id: 2, groupCode: 'GROUP_PRESALE', groupName: '售前咨询组', skillDesc: '商品 / 下单 / 优惠', agentCount: 2, status: 1, dataScope: 'GROUP' },
          { id: 3, groupCode: 'GROUP_FINANCE', groupName: '金融专席', skillDesc: '查账 / 挂失 / 分期', agentCount: 2, status: 0, dataScope: 'TENANT' }
        ]))
        return
      }

      // ===== 管理台：工单字段 =====
      if (path === '/workorder/field/list') {
        resolve(ok([
          { id: 1, fieldKey: 'orderNo', fieldName: '关联订单号', fieldType: 'text', required: 1, status: 1, sortNum: 10 },
          { id: 2, fieldKey: 'refundAmount', fieldName: '退款金额', fieldType: 'number', required: 1, status: 1, sortNum: 20 },
          { id: 3, fieldKey: 'refundReason', fieldName: '退款原因', fieldType: 'select', required: 0, status: 1, sortNum: 30 }
        ]))
        return
      }

      // ===== 管理台：知识图谱 =====
      if (path === '/knowledge-graph/nodes') {
        resolve(ok([
          { id: 1, name: '退款', type: '业务动作', relation: '属于→售后', source: 'FAQ 如何退款' },
          { id: 2, name: '物流', type: '业务对象', relation: '关联→订单', source: 'FAQ 物流多久送达' },
          { id: 3, name: '订单', type: '业务实体', relation: '包含→商品', source: 'FAQ 修改收货地址' },
          { id: 4, name: '发票', type: '业务对象', relation: '属于→财务', source: '未命中问题' }
        ]))
        return
      }

      // ===== 管理台：接入向导 =====
      if (path === '/onboarding/steps') {
        resolve(ok([
          { order: 1, title: '开通租户', desc: '创建租户并选择套餐', status: '已完成' },
          { order: 2, title: '选择行业包', desc: '电商 / 金融 / 新零售', status: '已完成' },
          { order: 3, title: '配置连接器', desc: '对接订单与物流接口', status: '进行中' },
          { order: 4, title: '接入 SDK', desc: '网页 / 小程序 / APP', status: '未开始' },
          { order: 5, title: '导入知识', desc: 'FAQ 与文档', status: '未开始' },
          { order: 6, title: '配置回调', desc: '会话进展与工单事件', status: '未开始' }
        ]))
        return
      }
      if (path === '/onboarding/progress') {
        resolve(ok({ total: 6, done: 2, current: 3, percent: 33 }))
        return
      }

      // ===== 管理台：客户 / 知识 / 坐席 =====
      if (path === '/customer/page' || path === '/customer/list') {
        const list = [
          { id: 1, phone: '13800138001', email: 'vip@example.com', nickname: '测试用户1', customerTag: '贵宾', createTime: '2026-09-01 10:00:00' },
          { id: 2, phone: '13900139002', email: 'user2@example.com', nickname: '小王', customerTag: '普通', createTime: '2026-09-03 14:20:00' },
          { id: 3, phone: '13700137003', email: '', nickname: '物流咨询客', customerTag: '售后', createTime: '2026-09-08 09:12:00' }
        ]
        resolve(ok(path === '/customer/list' ? list : { records: list, total: list.length, current: 1, size: 10 }))
        return
      }
      if (path === '/knowledge/list') {
        const rows = faqs.map((f) => Object.assign({}, f, { status: 1, auditStatus: 2 }))
        resolve(ok({ records: rows, total: rows.length, current: 1, size: 10 }))
        return
      }
      if (path === '/agent/list') {
        resolve(ok([
          { id: 1, agentAccount: 'admin', agentName: '值班长王芳', agentNo: 'A001', skill: '值班长', agentStatus: 1 },
          { id: 2, agentAccount: 'agent001', agentName: '张晓梅', agentNo: 'A002', skill: '售后', agentStatus: 1 },
          { id: 3, agentAccount: 'agent002', agentName: '李浩然', agentNo: 'A003', skill: '售前', agentStatus: 0 }
        ]))
        return
      }

      // ===== 管理台：AI 配置 =====
      if (path === '/system/ai-model/list') {
        resolve(ok([
          { id: 1, modelName: '演示对话模型', modelType: 'LLM', provider: 'OPENAI', remoteModel: 'demo-chat', isActive: 1, enabled: 1, health: 'HEALTHY' },
          { id: 2, modelName: '演示向量模型', modelType: 'EMBEDDING', provider: 'OPENAI', remoteModel: 'demo-embed', isActive: 1, enabled: 1, health: 'HEALTHY' }
        ]))
        return
      }
      if (path === '/system/intent/list') {
        resolve(ok([
          { id: 1, intentCode: 'QUERY_LOGISTICS', intentName: '查物流', intentType: 'business', priority: 10, enabled: 1 },
          { id: 2, intentCode: 'TO_AGENT', intentName: '转人工', intentType: 'transfer', priority: 5, enabled: 1 }
        ]))
        return
      }
      if (path === '/system/slot/list') {
        resolve(ok([
          { id: 1, intentCode: 'QUERY_LOGISTICS', slotName: '订单号', slotType: 'order_id', required: 1, enabled: 1 }
        ]))
        return
      }
      if (m === 'get' && path === '/prompt/config') {
        // 与后端 PromptDebugController.getConfig() 的嵌套结构保持一致
        resolve(ok({
          enabled: true,
          debugLog: false,
          preset: 'default',
          roleConfig: { systemRole: '专业客服助手', roleDescription: '耐心、准确地基于知识库作答', roleDomain: '电商售后' },
          formatConfig: { outputFormat: 'markdown', strictOutput: false },
          cotConfig: { cotEnabled: true, cotInstruction: '先判断问题意图，再检索依据，最后给出结论' },
          fewShotConfig: { fewShotEnabled: false },
          boundaryConfig: { boundaryEnabled: true, noDataReply: '抱歉，我暂时没有找到相关信息。', noFabrication: true },
          contextConfig: { contextWindowSize: 5, contextOnlyReply: true }
        }))
        return
      }
      if (path === '/prompt/retrieval-config') {
        resolve(ok({ topK: 5, scoreThreshold: 0.35, strategy: 'hybrid', citationTemplate: '依据：{{question}}', implemented: true }))
        return
      }
      if (path === '/multimodal-knowledge/list') {
        resolve(ok([
          { knowledgeId: 'mm-img-1', mediaType: 'image', title: '退款流程指引图', createTime: '2026-09-05 10:00:00' },
          { knowledgeId: 'mm-aud-1', mediaType: 'audio', title: '客服问候语音', createTime: '2026-09-06 11:20:00' },
          { knowledgeId: 'mm-vid-1', mediaType: 'video', title: '产品使用教程', createTime: '2026-09-07 15:40:00' }
        ]))
        return
      }

      // ===== 管理台：开放接入 / Widget =====
      if (path === '/open/pack/list') {
        resolve(ok([
          { code: 'ecommerce', name: '电商', remark: '物流 / 退款', enabled: 1 },
          { code: 'finance', name: '金融', remark: '查账 / 挂失', enabled: 0 }
        ]))
        return
      }
      if (path === '/open/tool/list') {
        resolve(ok([
          { id: 1, name: '查询物流', description: '查询物流轨迹', intentBind: 'QUERY_LOGISTICS', packCode: 'ecommerce', risk: 'low' }
        ]))
        return
      }
      if (path === '/open/widget-config/get') {
        resolve(ok({ brandName: '智能客服', brandColor: '#2f6bff', welcomeText: '您好，我是智能小客', privacyEnabled: 1, position: 'right' }))
        return
      }

      // ===== 管理台：运维 =====
      if (path === '/ops/overview') {
        resolve(ok({ services: 6, healthy: 5, warnings: 1, todayRequests: 12800, errorRate: 0.6, avgLatencyMs: 42 }))
        return
      }
      if (path === '/ops/health') {
        resolve(ok([
          { service: 'ai-cs-gateway', status: 'UP', latencyMs: 8 },
          { service: 'ai-cs-base-service', status: 'UP', latencyMs: 12 },
          { service: 'ai-cs-agent', status: 'UP', latencyMs: 20 },
          { service: 'ai-cs-knowledge', status: 'UP', latencyMs: 18 },
          { service: 'ai-cs-workorder', status: 'UP', latencyMs: 15 },
          { service: 'ai-cs-open', status: 'DEGRADED', latencyMs: 120 }
        ]))
        return
      }

      // ===== 管理台：系统 =====
      if (path === '/system/user/list') {
        resolve(ok([
          { id: 1, username: 'admin', realName: '超级管理员', email: 'admin@example.com', status: 1, lastLoginTime: now() },
          { id: 2, username: 'operator', realName: '运营管理员', email: 'operator@example.com', status: 1, lastLoginTime: now() }
        ]))
        return
      }
      if (path === '/system/role/list') {
        resolve(ok([
          { id: 1, roleName: '超级管理员', roleCode: 'SUPER_ADMIN', description: '全部权限', status: 1 },
          { id: 2, roleName: '坐席', roleCode: 'AGENT', description: '接待与工单', status: 1 },
          { id: 3, roleName: '运营管理员', roleCode: 'ADMIN', description: '可进后台', status: 1 }
        ]))
        return
      }
      if (path === '/system/config/list') {
        resolve(ok([
          { id: 1, configKey: 'ai.default.model', configValue: 'demo-chat', configType: 'system', description: '默认对话模型', status: 1 },
          { id: 2, configKey: 'ai.quota.daily', configValue: '1000', configType: 'number', description: '租户日配额', status: 1 }
        ]))
        return
      }
      if (path === '/system/log/page') {
        resolve(ok({
          records: [
            { id: 1, username: 'admin', realName: '超级管理员', module: 'knowledge', operation: '保存知识库', requestMethod: 'POST', requestUrl: '/knowledge/save', ip: '127.0.0.1', createTime: now(), status: 1 },
            { id: 2, username: 'operator', realName: '运营管理员', module: 'workorder', operation: '更新工单', requestMethod: 'PUT', requestUrl: '/workorder/update', ip: '127.0.0.1', createTime: now(), status: 1 }
          ],
          total: 2,
          current: 1,
          size: 10
        }))
        return
      }

      // ===== 开放接入子页 =====
      if (path === '/open/webhook/inbound/list') {
        resolve(ok([
          { id: 1, name: '订单系统入站', eventType: 'order.created', enabled: 1 },
          { id: 2, name: 'CRM 入站', eventType: 'customer.sync', enabled: 1 }
        ]))
        return
      }
      if (path === '/open/webhook/outbound/list') {
        resolve(ok([
          { id: 1, name: '会话进展回调', eventType: 'session.progress', url: 'https://example.com/hook', enabled: 1 }
        ]))
        return
      }
      if (path === '/open/card/list') {
        resolve(ok([
          { id: 1, templateCode: 'order_card', name: '订单卡片', enabled: 1 },
          { id: 2, templateCode: 'form_card', name: '表单卡片', enabled: 0 }
        ]))
        return
      }
      if (path === '/open/connector/whitelist/list') {
        resolve(ok([
          { id: 1, tenantCode: 'default', urlPattern: 'https://api.example.com/**', enabled: 1 }
        ]))
        return
      }
      if (path === '/open/prompt/pack/list') {
        resolve(ok([
          { id: 1, packCode: 'ecommerce', packName: '电商提示词', enabled: 1 },
          { id: 2, packCode: 'finance', packName: '金融合规提示词', enabled: 0 }
        ]))
        return
      }
      if (path === '/open/openapi/list') {
        resolve(ok([
          { id: 1, name: '订单查询接口', specUrl: 'https://api.example.com/openapi.json', status: 'IMPORTED' }
        ]))
        return
      }

      // ===== 运维子页 =====
      if (path === '/ops/logs') {
        resolve(ok({
          list: [
            { timestamp: now(), level: 'INFO', service: 'ai-cs-gateway', message: '接入对话请求' },
            { timestamp: now(), level: 'WARN', service: 'ai-cs-open', message: '连接器首包超时，准备重试' }
          ],
          total: 2
        }))
        return
      }
      if (path === '/ops/traces') {
        resolve(ok({
          list: [
            { traceId: 'trace-demo-1', service: 'ai-cs-gateway', durationMs: 860, status: 'OK' },
            { traceId: 'trace-demo-2', service: 'ai-cs-agent', durationMs: 420, status: 'OK' }
          ],
          total: 2
        }))
        return
      }
      if (path === '/ops/alerts') {
        resolve(ok({
          rules: [
            { name: '服务宕机', code: 'service_down', description: '健康检查失败', enabled: 1 },
            { name: '错误突增', code: 'error_spike', description: '5 分钟错误过多', enabled: 1 },
            { name: '配额将尽', code: 'quota_near', description: '租户日配额超过 80%', enabled: 0 }
          ],
          events: [
            { time: now(), title: '模型超时', status: 'open', message: '演示事件 #1' },
            { time: now(), title: '错误率升高', status: 'resolved', message: '演示事件 #2' }
          ]
        }))
        return
      }

      // ===== 系统子页 =====
      if (path === '/system/menu/list') {
        resolve(ok([
          { id: 1, menuName: '工作台', path: '/dashboard', menuType: 0, sortNum: 1, status: 1 },
          { id: 11, menuName: '数据概览', path: '/dashboard', menuType: 1, sortNum: 1, status: 1 },
          { id: 2, menuName: '知识库管理', path: '/knowledge', menuType: 1, sortNum: 2, status: 1 }
        ]))
        return
      }
      if (path === '/system/log/analysis') {
        resolve(ok({
          todayVisits: 1280,
          uniqueUsers: 386,
          topPaths: [
            { path: '/ai/chat/send', count: 942 },
            { path: '/knowledge/search', count: 316 }
          ]
        }))
        return
      }

      if (path === '/system/data-retention/list') {
        resolve(ok([
          { id: 1, tenantCode: 'default', dataType: 'session', retentionDays: 90, allowExternalDomain: 0, allowUserDelete: 1, anonymizeAfterDays: 180, description: '会话保留', status: 1 },
          { id: 2, tenantCode: 'default', dataType: 'message', retentionDays: 180, allowExternalDomain: 0, allowUserDelete: 1, anonymizeAfterDays: 365, description: '消息保留', status: 1 }
        ]))
        return
      }

      // ===== 文档知识库：文档列表 / 版本 / 切片 / 上传入库 =====
      if (path === '/document/version/list') {
        resolve(ok(docVersions.filter((d) => d.isCurrent === 1)))
        return
      }
      if (path.indexOf('/document/version/versions/') === 0) {
        const documentId = decodeURIComponent(path.split('/').pop())
        resolve(ok(docVersions.filter((d) => d.documentId === documentId).sort((a, b) => b.version - a.version)))
        return
      }
      if (path === '/document/version/compare') {
        const a = docVersions.filter((d) => d.id === Number(body.version1Id))[0]
        const b = docVersions.filter((d) => d.id === Number(body.version2Id))[0]
        resolve(ok(a && b ? `v${a.version} → v${b.version}：切片 ${a.segmentCount} → ${b.segmentCount}` : '版本不存在'))
        return
      }
      if (path === '/document/version/rollback') {
        const target = Number(body.targetVersion)
        docVersions.forEach((d) => {
          if (d.documentId === body.documentId) d.isCurrent = d.version === target ? 1 : 0
        })
        resolve(ok('已回退到 v' + target))
        return
      }
      if (path === '/rag/upload/pdf/versioned' || path === '/rag/upload/file/versioned') {
        const documentName = body.documentName || '未命名文档'
        const documentId = body.documentId || documentName
        const same = docVersions.filter((d) => d.documentId === documentId)
        const nextVersion = same.length ? Math.max.apply(null, same.map((d) => d.version)) + 1 : 1
        // 新版本生效后旧版本取消当前标记
        same.forEach((d) => { d.isCurrent = 0 })
        const segmentCount = 18 + nextVersion * 9
        docVersions.unshift({
          id: Date.now(),
          documentId,
          documentName,
          version: nextVersion,
          fileType: path.indexOf('/pdf/') > 0 ? 'PDF' : 'TXT',
          fileSize: 204800,
          segmentCount,
          versionDescription: body.versionDescription || '',
          isCurrent: 1,
          uploaderName: '微信用户',
          createTime: now(),
          updateTime: now()
        })
        resolve(ok(`文档导入成功，共分割：${segmentCount} 个文本片段，版本号：${nextVersion}`))
        return
      }
      if (path === '/rag/chunks') {
        const documentId = body.documentId || ''
        const doc = docVersions.filter((d) => d.documentId === documentId)[0]
        const count = doc ? Math.min(doc.segmentCount || 0, 8) : 6
        const topics = ['适用范围', '退款条件', '到账时间', '跨境差异', '常见问答', '责任说明', '举证材料', '时效承诺']
        const records = []
        for (let i = 0; i < count; i++) {
          records.push({
            index: i + 1,
            length: 120 + i * 13,
            content: `【${topics[i % topics.length]}】${(doc && doc.documentName) || '演示文档'} 第 ${i + 1} 段：文档解析后切出的文本片段。`
          })
        }
        resolve(ok({ total: count, records, documentId, implemented: true }))
        return
      }

      // ===== 通用写操作（演示）：直接返回成功，不落库 =====
      if (m === 'post' && /\/save$/.test(path)) {
        resolve(ok('保存成功'))
        return
      }
      if (m === 'put' && /\/update$/.test(path)) {
        resolve(ok('更新成功'))
        return
      }
      if (m === 'delete' && path.indexOf('/delete/') >= 0) {
        resolve(ok('删除成功'))
        return
      }

      resolve(ok(null, '演示模式未覆盖该接口，已返回空数据'))
    }, 120)
  })
}

module.exports = { handle, now }
