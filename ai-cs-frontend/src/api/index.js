import request from '../utils/request'

// ===== 数据概览（Dashboard）=====
export const getDashboardStatistics = () => request.get('/statistics/dashboard')

export const sendChat = (data, config) => request.post('/ai/chat/send', data, config)

export const uploadAvatar = (file) => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/file/avatar', data)
}

/**
 * 上传对话附件（图片/文档/音频等，允许全部类型，单文件上限 20MB）。
 * 返回附件元信息，随消息的 attachments 字段一起提交给 /ai/chat/send。
 */
export const uploadChatAttachment = (file) => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/file/chat-attachment', data)
}

export const listCustomers = (keyword) => request.get('/customer/list', { params: { keyword } })
export const pageCustomers = (params) => request.get('/customer/page', { params })
export const saveCustomer = (data) => request.post('/customer/save', data)
export const updateCustomer = (data) => request.put('/customer/update', data)
export const deleteCustomer = (id) => request.delete(`/customer/delete/${id}`)

export const listWorkOrders = (params) => request.get('/workorder/page', { params })
export const createWorkOrder = (data) => request.post('/workorder/create', data)
export const updateWorkOrder = (data) => request.put('/workorder/update', data)
export const updateWorkOrderStatus = (id, status) =>
  request.put(`/workorder/status/${id}`, null, { params: { status } })
export const completeWorkOrder = (id) => request.put(`/workorder/complete/${id}`)
export const closeWorkOrder = (id) => request.put(`/workorder/close/${id}`)
export const assignWorkOrder = (id, agentId) => request.put(`/workorder/assign/${id}`, null, { params: { agentId } })
export const similarWorkOrders = (id) => request.get(`/workorder/similar/${id}`)
export const autoClassifyWorkOrder = (id) => request.post(`/workorder/auto-classify/${id}`)
export const deleteWorkOrder = (id) => request.delete(`/workorder/delete/${id}`)

export const listFaqs = (params) => {
  const query = typeof params === 'string' || params == null
    ? { keyword: params || undefined, page: 1, size: 20 }
    : params
  return request.get('/knowledge/list', { params: query })
}
export const saveFaq = (data) => request.post('/knowledge/save', data)
export const updateFaq = (data) => request.put('/knowledge/update', data)
export const deleteFaq = (id) => request.delete(`/knowledge/delete/${id}`)
export const vectorizeFaq = (id) => request.post(`/knowledge/vectorize/${id}`)
export const batchVectorize = (tenantCode) => request.post('/knowledge/vectorize/batch', null, { params: { tenantCode } })
export const searchKnowledge = (question, tenantCode) => request.get('/knowledge/search', { params: { question, tenantCode } })
export const ragSearchKnowledge = (question, tenantCode) =>
  request.get('/knowledge/rag/search', { params: { question, tenantCode } })
export const listKnowledgeMiss = (params) => request.get('/knowledge/miss', { params })
export const importFaqs = (data) => request.post('/knowledge/import', data)
export const getKnowledgeHealth = () => request.get('/knowledge/health')

export const listSessions = () => request.get('/session/list')
export const pageSessions = (params) => request.get('/session/page', { params })
export const ensureSession = (data) => request.post('/session/ensure', data)
export const saveSessionMessage = (data) => request.post('/session/message', data)
export const listSessionMessages = (sessionId) => request.get(`/session/${sessionId}/messages`)
export const endSession = (sessionId) => request.put(`/session/${sessionId}/end`)
export const transferSession = (sessionId) => request.put(`/session/${sessionId}/transfer`)

export const listAgents = () => request.get('/agent/list')
export const saveAgent = (data) => request.post('/agent/save', data)
export const updateAgent = (data) => request.put('/agent/update', data)
export const updateAgentStatus = (id, agentStatus) => request.put(`/agent/status/${id}`, { agentStatus })
export const deleteAgent = (id) => request.delete(`/agent/delete/${id}`)

export const initWidget = (data) => request.post('/open/widget/init', data)
export const listConnectors = () => request.get('/open/connector/list')
export const saveConnector = (data) => request.post('/open/connector/save', data)
export const updateConnector = (data) => request.put('/open/connector/update', data)
export const deleteConnector = (id) => request.delete(`/open/connector/delete/${id}`)
export const listOpenTools = () => request.get('/open/tool/list')
export const saveOpenTool = (data) => request.post('/open/tool/save', data)
export const updateOpenTool = (data) => request.put('/open/tool/update', data)
export const deleteOpenTool = (id) => request.delete(`/open/tool/delete/${id}`)
export const invokeOpenTool = (data) => request.post('/open/tool/invoke', data)
export const listOpenPacks = () => request.get('/open/pack/list')
export const saveOpenPack = (data) => request.post('/open/pack/save', data)
export const updateOpenPack = (data) => request.put('/open/pack/update', data)
export const deleteOpenPack = (code) => request.delete(`/open/pack/delete/${encodeURIComponent(code)}`)
export const setPackEnabled = (code, enabled) => request.put(`/open/pack/${code}/enabled`, null, { params: { enabled } })
export const activatePack = (code) => request.put(`/open/pack/${code}/activate`)
export const listSceneConfigs = () => request.get('/open/scene-config/list')
export const saveSceneConfig = (data) => request.post('/open/scene-config/save', data)
export const updateSceneConfig = (data) => request.put('/open/scene-config/update', data)
export const setSceneConfigEnabled = (id, enabled) => request.put(`/open/scene-config/${id}/enabled`, null, { params: { enabled } })
export const deleteSceneConfig = (id) => request.delete(`/open/scene-config/delete/${id}`)

export const getOpsOverview = () => request.get('/ops/overview')
export const getOpsHealth = () => request.get('/ops/health')
export const getOpsLogs = (params) => request.get('/ops/logs', { params })
export const getOpsLogsByRequestId = (requestId) => request.get(`/ops/logs/${encodeURIComponent(requestId)}`)
export const getOpsTraces = (params) => request.get('/ops/traces', { params })
export const getOpsTrace = (traceId) => request.get(`/ops/traces/${encodeURIComponent(traceId)}`)
export const getOpsAlerts = () => request.get('/ops/alerts')
export const saveOpsAlerts = (rules) => request.put('/ops/alerts', rules)

// ===== AI 模型统一管理（base-service /system/ai-model）=====
export const listAiModels = () => request.get('/system/ai-model/list')
export const listAiModelsEnabled = (modelType) => request.get('/system/ai-model/enabled', { params: { modelType } })
export const getAiModelActive = (modelType) => request.get('/system/ai-model/active', { params: { modelType } })
export const saveAiModel = (data) => request.post('/system/ai-model/save', data)
export const setAiModelActive = (id) => request.put(`/system/ai-model/active/${id}`)
export const setAiModelEnabled = (id, enabled) => request.put(`/system/ai-model/enabled/${id}`, { enabled })
export const deleteAiModel = (id) => request.delete(`/system/ai-model/delete/${id}`)
export const testAiModel = (id) => request.post(`/system/ai-model/test/${id}`)
export const pageModelUsage = (params) => request.get('/system/ai-model/usage/page', { params })
export const getModelUsageSummary = (params) => request.get('/system/ai-model/usage/summary', { params })
export const getModelRecentFail = (minutes = 5) => request.get('/system/ai-model/usage/recent-fail', { params: { minutes } })

// ===== AI 工具注册（base-service /system/ai-tool）=====
/** 自定义工具列表（内置工具仍由前端 catalog 提供，两者合并展示） */
export const listAiTools = (params) => request.get('/system/ai-tool/list', { params })
/** 新建或更新自定义工具 */
export const saveAiTool = (data) => request.post('/system/ai-tool/save', data)
/** 删除自定义工具 */
export const deleteAiTool = (id) => request.delete(`/system/ai-tool/delete/${id}`)
/** 启用/停用自定义工具 */
export const setAiToolEnabled = (id, enabled) => request.put(`/system/ai-tool/enabled/${id}`, { enabled })
/** 测试工具绑定的开放接口连通性 */
export const testAiTool = (id) => request.post(`/system/ai-tool/test/${id}`)

// ===== AI 与自动化：可配置意图（base-service /system/intent）=====
export const listIntents = (tenantCode) => request.get('/system/intent/listAll', { params: { tenantCode } })
export const saveIntent = (data) => request.post('/system/intent/save', data)
export const deleteIntent = (id) => request.delete(`/system/intent/delete/${id}`)

// ===== AI 与自动化：多轮填槽（base-service /system/slot）=====
export const listSlots = (tenantCode) => request.get('/system/slot/listAll', { params: { tenantCode } })
export const saveSlot = (data) => request.post('/system/slot/save', data)
export const deleteSlot = (id) => request.delete(`/system/slot/delete/${id}`)

// ===== AI 与自动化：数据保留策略（base-service /system/data-retention）=====
export const listDataRetentions = (tenantCode) => request.get('/system/data-retention/list', { params: { tenantCode } })
export const saveDataRetention = (data) => request.post('/system/data-retention/save', data)
export const deleteDataRetention = (id) => request.delete(`/system/data-retention/delete/${id}`)

// ===== 知识库管理：点赞点踩 + 未命中转问 =====
export const faqFeedback = (id, type) => request.post(`/knowledge/feedback/${id}`, null, { params: { type } })
export const convertMiss = (id, answer, category) => request.post(`/knowledge/miss/${id}/convert`, null, { params: { answer, category } })

// ===== 对外帮助中心（knowledge /help-center，匿名）=====
export const helpSearch = (question, tenantCode) => request.get('/help-center/search', { params: { question, tenantCode } })
export const helpFaqs = (params) => request.get('/help-center/faqs', { params })
export const helpCategories = (tenantCode) => request.get('/help-center/categories', { params: { tenantCode } })
export const helpFeedback = (id, type) => request.post(`/help-center/feedback/${id}`, null, { params: { type } })
export const helpView = (id) => request.post(`/help-center/view/${id}`)

// ===== 认证 =====
export const login = (data, config) => request.post('/auth/login', data, config)
export const platformLogin = (data, config) => request.post('/auth/login/platform', data, config)
export const logout = () => request.post('/auth/logout')
export const getUserInfo = () => request.get('/auth/userinfo')
export const getAuthMenus = () => request.get('/auth/menus')

// ===== 用户 / 角色 / 菜单 =====
export const pageUsers = (params) => request.get('/system/user/page', { params })
export const listUsers = () => request.get('/system/user/list')
export const saveUser = (data) => request.post('/system/user/save', data)
export const updateUser = (data) => request.put('/system/user/update', data)
export const deleteUser = (id) => request.delete(`/system/user/delete/${id}`)
export const getUserDetail = (id) => request.get(`/system/user/info/${id}`)
export const listRoles = () => request.get('/system/role/list')
export const saveRole = (data) => request.post('/system/role/save', data)
export const updateRole = (data) => request.put('/system/role/update', data)
export const deleteRole = (id) => request.delete(`/system/role/delete/${id}`)
export const getMenuTree = () => request.get('/system/menu/tree')
export const listMenus = () => request.get('/system/menu/list')
export const saveMenu = (data) => request.post('/system/menu/save', data)
export const updateMenu = (data) => request.put('/system/menu/update', data)
export const deleteMenu = (id) => request.delete(`/system/menu/delete/${id}`)

// ===== 系统配置 / 操作审计 =====
export const listSysConfigs = () => request.get('/system/config/list')
export const saveSysConfig = (data) => request.post('/system/config/save', data)
export const updateSysConfig = (data) => request.put('/system/config/update', data)
export const pageOperationLogs = (params) => request.get('/system/log/page', { params })
export const getAccessAnalysis = (params) => request.get('/system/log/analysis', { params })
export const deleteOperationLog = (id) => request.delete(`/system/log/delete/${id}`)

// ===== 租户日配额 =====
export const getTenantQuota = (tenantCode) => request.get(`/ai/quota/${encodeURIComponent(tenantCode)}`)
export const setTenantQuota = (tenantCode, limit) => request.post(`/ai/quota/${encodeURIComponent(tenantCode)}`, { limit })
export const interruptChat = (sessionId) => request.post('/ai/chat/interrupt', { sessionId })

// ===== 开放层：Webhook / 卡片 / 白名单 / 映射 / 提示词 / OpenAPI =====
export const listInboundWebhooks = () => request.get('/open/webhook/inbound/list')
export const saveInboundWebhook = (data) => request.post('/open/webhook/inbound/save', data)
export const deleteInboundWebhook = (id) => request.delete(`/open/webhook/inbound/delete/${id}`)
export const enableInboundWebhook = (id, enabled) => request.put(`/open/webhook/inbound/enable/${id}`, null, { params: { enabled } })
export const listOutboundWebhooks = () => request.get('/open/webhook/outbound/list')
export const saveOutboundWebhook = (data) => request.post('/open/webhook/outbound/save', data)
export const deleteOutboundWebhook = (id) => request.delete(`/open/webhook/outbound/delete/${id}`)
export const enableOutboundWebhook = (id, enabled) => request.put(`/open/webhook/outbound/enable/${id}`, null, { params: { enabled } })
export const triggerOutboundWebhook = (eventType, tenantCode, payload) =>
  request.post('/open/webhook/outbound/trigger', payload, { params: { eventType, tenantCode } })

export const listCardTemplates = () => request.get('/open/card/list')
export const saveCardTemplate = (data) => request.post('/open/card/save', data)
export const deleteCardTemplate = (id) => request.delete(`/open/card/delete/${id}`)
export const enableCardTemplate = (id, enabled) => request.put(`/open/card/enable/${id}`, null, { params: { enabled } })
export const renderCardTemplate = (templateCode, tenantCode, data) =>
  request.post('/open/card/render', data, { params: { templateCode, tenantCode } })

export const listUrlWhitelist = (tenantCode) => request.get('/open/connector/whitelist/list', { params: { tenantCode } })
export const saveUrlWhitelist = (data) => request.post('/open/connector/whitelist/save', data)
export const deleteUrlWhitelist = (id) => request.delete(`/open/connector/whitelist/delete/${id}`)

export const listFieldMappings = (connectorId) => request.get(`/open/connector/mapping/list/${connectorId}`)
export const saveFieldMapping = (data) => request.post('/open/connector/mapping/save', data)
export const deleteFieldMapping = (id) => request.delete(`/open/connector/mapping/delete/${id}`)
export const testApplyMapping = (connectorId, sourceData) =>
  request.post('/open/connector/mapping/testApply', sourceData, { params: { connectorId } })
export const testReverseMapping = (connectorId, targetData) =>
  request.post('/open/connector/mapping/testReverse', targetData, { params: { connectorId } })

export const listPromptPacks = () => request.get('/open/prompt/pack/list')
export const savePromptPack = (data) => request.post('/open/prompt/pack/save', data)
export const deletePromptPack = (id) => request.delete(`/open/prompt/pack/delete/${id}`)
export const enablePromptPack = (id, enabled) => request.put(`/open/prompt/pack/enable/${id}`, null, { params: { enabled } })
export const loadPromptPack = (packCode) => request.get(`/open/prompt/pack/load/${encodeURIComponent(packCode)}`)

export const listOpenApiImports = () => request.get('/open/openapi/list')
export const createOpenApiImport = (data) => request.post('/open/openapi/create', data)
export const executeOpenApiImport = (id) => request.post(`/open/openapi/execute/${id}`)
export const deleteOpenApiImport = (id) => request.delete(`/open/openapi/delete/${id}`)

// ===== 多租户管理（base-service /system/tenant）=====
export const listTenants = (keyword) => request.get('/system/tenant/list', { params: { keyword } })
export const pageTenants = (params) => request.get('/system/tenant/page', { params })
export const saveTenant = (data) => request.post('/system/tenant/save', data)
export const updateTenant = (data) => request.put('/system/tenant/update', data)
export const updateTenantStatus = (id, status) => request.put(`/system/tenant/${id}/status`, null, { params: { status } })
export const deleteTenant = (id) => request.delete(`/system/tenant/delete/${id}`)

// ===== 技能组与数据权限（base-service /system/skill-group）=====
export const listSkillGroups = () => request.get('/system/skill-group/list')
export const listDataScopes = () => request.get('/system/skill-group/data-scopes')
export const saveSkillGroup = (data) => request.post('/system/skill-group/save', data)
export const updateSkillGroup = (data) => request.put('/system/skill-group/update', data)
export const deleteSkillGroup = (id) => request.delete(`/system/skill-group/delete/${id}`)

// ===== 排队与转接（base-service /queue）=====
export const listQueue = () => request.get('/queue/list')
export const getQueueMonitor = () => request.get('/queue/monitor')
export const assignQueueSession = (sessionId, agentId) => request.put(`/queue/${sessionId}/assign`, null, { params: { agentId } })
export const transferQueueSession = (sessionId, skillGroup, agentId) => request.put(`/queue/${sessionId}/transfer`, null, { params: { skillGroup, agentId } })
export const consultQueueSession = (sessionId, agentId) => request.put(`/queue/${sessionId}/consult`, null, { params: { agentId } })
export const removeQueueSession = (sessionId) => request.delete(`/queue/${sessionId}`)

// ===== 工单自定义字段（workorder /workorder/field）=====
export const listWorkOrderFields = () => request.get('/workorder/field/list')
export const listWorkOrderFieldTypes = () => request.get('/workorder/field/types')
export const saveWorkOrderField = (data) => request.post('/workorder/field/save', data)
export const updateWorkOrderField = (data) => request.put('/workorder/field/update', data)
export const deleteWorkOrderField = (id) => request.delete(`/workorder/field/delete/${id}`)

// ===== Widget 隐私与白标（open /open/widget-config）=====
/** 后台维护用：可配多套，按租户/渠道各一份 */
export const listWidgetConfigs = () => request.get('/open/widget-config/list')
/** 对外初始化用：取当前启用的那套 */
export const getWidgetConfig = () => request.get('/open/widget-config/get')
export const saveWidgetConfig = (data) => request.post('/open/widget-config/save', data)
export const updateWidgetConfig = (data) => request.put('/open/widget-config/update', data)
export const setWidgetConfigEnabled = (id, enabled) => request.put(`/open/widget-config/${id}/enabled`, null, { params: { enabled } })
export const deleteWidgetConfig = (id) => request.delete(`/open/widget-config/delete/${id}`)

// ===== 报表中心（base-service /report）=====
export const getReportOverview = () => request.get('/report/overview')
export const getReportCsat = () => request.get('/report/csat')
export const getReportSla = () => request.get('/report/sla')
export const getReportTrend = () => request.get('/report/trend')

// ===== 接入向导（open /onboarding）=====
export const getOnboardingSteps = () => request.get('/onboarding/steps')
export const getOnboardingProgress = () => request.get('/onboarding/progress')

// ===== 知识图谱（knowledge /api/knowledge-graph）=====
export const listKnowledgeGraphNodes = (params) => request.get('/knowledge-graph/nodes', { params })
/** 构建/抽取都是 @RequestParam 接收文本，需表单编码 */
export const buildKnowledgeGraph = (data) => request.post('/knowledge-graph/build', toFormParams(data))
export const extractKnowledgeGraph = (data) => request.post('/knowledge-graph/extract', toFormParams(data))
// 图谱基础：节点/关系 CRUD、问答、搜索、可视化
export const saveGraphNode = (data) => request.post('/api/knowledge-graph/node', data)
export const deleteGraphNode = (nodeId) => request.delete(`/api/knowledge-graph/node/${nodeId}`)
export const saveGraphRelation = (data) => request.post('/api/knowledge-graph/relation', data)
export const deleteGraphRelation = (relationId) => request.delete(`/api/knowledge-graph/relation/${relationId}`)
export const listGraphRelations = (nodeId) => request.get(`/api/knowledge-graph/relations/${nodeId}`)
export const listGraphNodesByLabel = (label) => request.get(`/api/knowledge-graph/nodes/label/${label}`)
export const searchGraph = (params) => request.get('/api/knowledge-graph/search', { params })
export const graphQa = (data) => request.post('/api/knowledge-graph/qa', data)
export const getGraphVisualization = (params) => request.get('/api/knowledge-graph/visualization', { params })
export const getGraphSubgraph = (params) => request.get('/api/knowledge-graph/subgraph', { params })
export const getGraphStatistics = () => request.get('/api/knowledge-graph/statistics')

// ===== 媒体资源：图片 / 音频的基础处理与增强能力 =====
// 基础处理（mediaType: image / audio）
export const getMediaMetadata = (mediaType, fileId) => request.get(`/api/${mediaType}/metadata/${fileId}`)
export const downloadMedia = (mediaType, fileId) => request.get(`/api/${mediaType}/download/${fileId}`)
export const convertMedia = (mediaType, data) => request.post(`/api/${mediaType}/convert`, data)
export const deleteMedia = (mediaType, fileId) => request.delete(`/api/${mediaType}/${fileId}`)
export const vectorizeMedia = (mediaType, data) => request.post(`/api/${mediaType}/vectorize`, data)
export const searchMedia = (mediaType, params) => request.get(`/api/${mediaType}/search`, { params })
export const deleteMediaVector = (mediaType, vectorId) => request.delete(`/api/${mediaType}/vector/${vectorId}`)
// 图片专有
export const resizeImage = (data) => request.post('/api/image/resize', data)
export const cropImage = (data) => request.post('/api/image/crop', data)
// 音频专有
export const adjustAudioBitrate = (data) => request.post('/api/audio/adjust-bitrate', data)
export const getAudioDuration = (data) => request.post('/api/audio/duration', data)
export const transcribeAndVectorize = (data) => request.post('/api/audio/transcribe-and-vectorize', data)

// 增强：批量上传
export const batchUploadMedia = (mediaType, data) => request.post(`/api/${mediaType}/enhance/batch/upload`, data)
export const getBatchProgress = (mediaType, batchId) => request.get(`/api/${mediaType}/enhance/batch/progress/${batchId}`)
// 增强：去重
export const checkMediaDedup = (mediaType, data) => request.post(`/api/${mediaType}/enhance/dedup/check`, data)
export const listSimilarMedia = (mediaType, params) => request.get(`/api/${mediaType}/enhance/dedup/similar`, { params })
// 增强：版本
export const listMediaVersions = (mediaType, fileId) => request.get(`/api/${mediaType}/enhance/version/${fileId}`)
export const switchMediaVersion = (mediaType, fileId, data) => request.post(`/api/${mediaType}/enhance/version/${fileId}/switch`, data)
export const rollbackMediaVersion = (mediaType, fileId, data) => request.post(`/api/${mediaType}/enhance/version/${fileId}/rollback`, data)
export const diffMediaVersion = (mediaType, fileId) => request.get(`/api/${mediaType}/enhance/version/${fileId}/diff`)
// 增强：访问统计
export const getMediaStats = (mediaType, fileId) => request.get(`/api/${mediaType}/enhance/stats/${fileId}`)
export const getHotMedia = (mediaType) => request.get(`/api/${mediaType}/enhance/stats/hot`)
export const getMediaStatsSummary = (mediaType) => request.get(`/api/${mediaType}/enhance/stats/summary`)
export const recordMediaAction = (mediaType, fileId, action) => request.post(`/api/${mediaType}/enhance/stats/${fileId}/${action}`)
// 增强：图片防盗链
export const createHotlinkToken = (data) => request.post('/api/image/enhance/hotlink/token', data)
export const createSignedUrl = (data) => request.post('/api/image/enhance/hotlink/signed-url', data)
export const listHotlinkWhitelist = () => request.get('/api/image/enhance/hotlink/whitelist')
export const addHotlinkWhitelist = (data) => request.post('/api/image/enhance/hotlink/whitelist', data)
export const removeHotlinkWhitelist = (data) => request.delete('/api/image/enhance/hotlink/whitelist', { data })
// 增强：智能标签
export const autoTagMedia = (mediaType, data) => request.post(`/api/${mediaType}/enhance/tags/auto`, data)
export const listMediaTags = (mediaType, fileId) => request.get(`/api/${mediaType}/enhance/tags/${fileId}`)
export const saveMediaTags = (mediaType, fileId, data) => request.post(`/api/${mediaType}/enhance/tags/${fileId}`, data)
export const deleteMediaTag = (mediaType, fileId, data) => request.delete(`/api/${mediaType}/enhance/tags/${fileId}`, { data })
export const searchMediaTags = (mediaType, params) => request.get(`/api/${mediaType}/enhance/tags/search`, { params })
// 增强：识别与审核
export const ocrImage = (data) => request.post('/api/image/enhance/ocr', data)
export const moderateMedia = (mediaType, data) => request.post(`/api/${mediaType}/enhance/moderate`, data)
export const asrAudio = (data) => request.post('/api/audio/enhance/asr', data)
export const getAudioWaveform = (fileId, data) => request.post(`/api/audio/enhance/waveform/${fileId}`, data)
export const getImageStorageUrl = (fileId) => request.get(`/api/image/enhance/storage/url/${fileId}`)
export const listPredefinedTags = () => request.get('/api/image/enhance/tags/predefined')
export const suggestTags = (mediaType, fileId) => request.get(`/api/${mediaType}/enhance/tags/suggest/${fileId}`)

// ===== 文件管理（knowledge /api/file）=====
export const previewFile = (params) => request.get('/api/file/preview', { params })
export const previewFileByStorage = (storageType, fileId) => request.get(`/api/file/preview/${storageType}/${fileId}`)
export const getFileInfo = (params) => request.get('/api/file/info', { params })
export const listFiles = (params) => request.get('/api/file/list', { params })
/** 普通下载：返回文件地址，前端直接跳转或在新窗口打开 */
export const downloadFile = (params) => request.get('/api/file/download', { params })
/** range 为 HTTP Range 头，用于断点续传与音视频拖拽 */
export const downloadFileRange = (params, range) => request.get('/api/file/download/range', { params, headers: { Range: range } })
export const batchDownloadFiles = (data) => request.post('/api/file/download/batch', data)
export const decompressFile = (data) => request.post('/api/file/decompress', data)
export const decompressLocal = (data) => request.post('/api/file/decompress/local', data)
export const listDecompressContents = (data) => request.post('/api/file/decompress/contents', data)
export const previewDecompressed = (taskId, params) => request.get(`/api/file/decompressed/preview/${taskId}`, { params })
export const listDecompressed = (taskId, params) => request.get(`/api/file/decompressed/list/${taskId}`, { params })

// ===== AI 对话扩展（agent /ai）=====
export const chatImage = (data) => request.post('/ai/chat/image', data, { timeout: 60000 })
export const speechToText = (data) => request.post('/ai/chat/speech-to-text', data, { timeout: 60000 })
export const textToSpeech = (data) => request.post('/ai/chat/text-to-speech', data, { timeout: 60000 })
/** 智能体运行时可调用的工具清单（与 /system/ai-tool 的工具注册表不是一回事） */
export const listAgentTools = () => request.get('/ai/tools/list')
export const runToolChain = (data) => request.post('/ai/tools/chain', data, { timeout: 60000 })
export const clearToolCache = () => request.post('/ai/tools/cache/clear')
export const assistRecommend = (data) => request.post('/ai/assist/recommend', data)
export const assistSummary = (data) => request.post('/ai/assist/summary', data)
export const assistNextSentence = (data) => request.post('/ai/assist/next-sentence', data)
export const aiFlowStep = (data) => request.post('/ai/flow/step', data)
export const getIndustryPack = (packCode) => request.get(`/ai/industry-pack/${packCode}`)

// ===== 统计补充（base /statistics）=====
export const getStatisticsOverview = () => request.get('/statistics/overview')
export const getChatTrend = (data) => request.post('/statistics/chat-trend', data)
export const getWorkOrderStats = () => request.get('/statistics/workorder-stats')
export const getCustomerTrend = (data) => request.post('/statistics/customer-trend', data)

// ===== 提示词预览 =====
export const getPresetDetail = (name) => request.get(`/api/prompt/presets/${name}`)
export const previewSystemPrompt = () => request.get('/api/prompt/preview/system')
export const previewFullPrompt = (data) => request.post('/api/prompt/preview/full', data)
export const previewMessages = (data) => request.post('/api/prompt/preview/messages', data)

// ===== 工单流程（workorder /workorder/flow）=====
export const startWorkOrderFlow = (workOrderId, data) => request.post(`/workorder/flow/start/${workOrderId}`, data)
export const getWorkOrderFlowStatus = (workOrderId) => request.get(`/workorder/flow/status/${workOrderId}`)
export const completeFlowTask = (taskId, data) => request.post(`/workorder/flow/complete/${taskId}`, data)
export const listFlowTasks = (params) => request.get('/workorder/flow/tasks', { params })
export const listFlowDefinitions = () => request.get('/workorder/flow/definitions')
export const getFlowHistory = (workOrderId) => request.get(`/workorder/flow/history/${workOrderId}`)
export const cancelWorkOrderFlow = (workOrderId, data) => request.post(`/workorder/flow/cancel/${workOrderId}`, data)

// ===== 多模态问答与检索 =====
export const multimodalImageQa = (data) => request.post('/api/multimodal-knowledge/image/qa', data)
export const multimodalAudioQa = (data) => request.post('/api/multimodal-knowledge/audio/qa', data)
export const multimodalVideoQa = (data) => request.post('/api/multimodal-knowledge/video/qa', data)
export const multimodalMixedQa = (data) => request.post('/api/multimodal-knowledge/mixed/qa', data)
export const multimodalHybridSearch = (data) => request.post('/api/multimodal-knowledge/hybrid-search', data)
export const multimodalChat = (data) => request.post('/api/multimodal-knowledge/multimodal-chat', data)
export const crossModalSearch = (data) => request.post('/api/multimodal-knowledge/cross-modal-search', data)
export const getMultimodalDetail = (knowledgeId) => request.get(`/api/multimodal-knowledge/detail/${knowledgeId}`)
export const deleteMultimodal = (knowledgeId) => request.delete(`/api/multimodal-knowledge/delete/${knowledgeId}`)
export const analyzeVideo = (data) => request.post('/api/multimodal-knowledge/video/analyze', data)
export const multimodalSearch = (params) => request.get('/api/multimodal/search', { params })
export const multimodalCrossSearch = (params) => request.get('/api/multimodal/cross-search', { params })
export const multimodalQa = (params) => request.get('/api/multimodal/qa', { params })
export const getMultimodalSearchStats = () => request.get('/api/multimodal/stats')

// ===== 零散单条详情与补充能力 =====
export const getCustomerDetail = (id) => request.get(`/customer/${id}`)
export const getWorkOrderDetail = (id) => request.get(`/workorder/info/${id}`)
export const pageWorkOrderFields = (params) => request.get('/workorder/field/page', { params })
export const getIntentDetail = (id) => request.get(`/system/intent/${id}`)
export const getSlotDetail = (id) => request.get(`/system/slot/${id}`)
export const getFaqDetail = (id) => request.get(`/knowledge/faq/${id}`)
export const incrementVectorize = (data) => request.post('/knowledge/vectorize/increment', data)
export const getSessionSnapshot = (sessionId) => request.get(`/session/${sessionId}/snapshot`)
export const mergeAnonymousSession = (data) => request.post('/session/merge-anonymous', data)
export const getSelfHealth = () => request.get('/ops/health/self')
export const getSysConfigByKey = (key) => request.get(`/system/config/get/${key}`)
export const getDataRetention = () => request.get('/system/data-retention/get')
export const getCurrentDocVersion = (documentId) => request.get(`/api/document/version/current/${documentId}`)
export const clearUserMemory = (data) => request.post('/api/rag/memory/clear/user', data)
export const clearAllMemory = () => request.post('/api/rag/memory/clear/all')
export const batchSaveConnectorMapping = (data) => request.post('/open/connector/mapping/batchSave', data)
export const listOpenApiByTenant = (params) => request.get('/open/openapi/listByTenant', { params })
export const listCardsByTenant = (params) => request.get('/open/card/listByTenant', { params })
export const listCardsByPack = (params) => request.get('/open/card/listByPack', { params })
export const listInboundByTenant = (params) => request.get('/open/webhook/inbound/listByTenant', { params })
export const listOutboundByTenant = (params) => request.get('/open/webhook/outbound/listByTenant', { params })
export const listPromptPacksByPack = (params) => request.get('/open/prompt/pack/listByPack', { params })
export const listPromptPacksByPackAndType = (params) => request.get('/open/prompt/pack/listByPackAndType', { params })
export const batchImportPromptPacks = (data) => request.post('/open/prompt/pack/batchImport', data)
export const pageSkillGroups = (params) => request.get('/system/skill-group/page', { params })
export const setSkillGroupDataScope = (id, dataScope) =>
  request.put(`/system/skill-group/${id}/data-scope`, null, { params: { dataScope } })

// ===== 图谱增强：3D 模型 / Neo4j / 推理 / 时序 / 多语言 / 图嵌入 / GraphRAG / 演化 =====
// 3D 模型
export const import3dModel = (data) => request.post('/api/knowledge-enhanced/3d-model/import', data)
export const batchImport3dModel = (data) => request.post('/api/knowledge-enhanced/3d-model/batch-import', data)
export const search3dModel = (params) => request.get('/api/knowledge-enhanced/3d-model/search', { params })
export const list3dModels = () => request.get('/api/knowledge-enhanced/3d-model/all')
export const list3dModelsByTag = (tag) => request.get(`/api/knowledge-enhanced/3d-model/tag/${tag}`)
export const similar3dModels = (knowledgeId) => request.get(`/api/knowledge-enhanced/3d-model/similar/${knowledgeId}`)
export const get3dModelReport = (knowledgeId) => request.get(`/api/knowledge-enhanced/3d-model/report/${knowledgeId}`)
export const compare3dModels = (data) => request.post('/api/knowledge-enhanced/3d-model/compare', data)
export const qa3dModel = (data) => request.post('/api/knowledge-enhanced/3d-model/qa', data)
export const get3dConversionAdvice = (knowledgeId) => request.get(`/api/knowledge-enhanced/3d-model/conversion-advice/${knowledgeId}`)
// Neo4j 图分析
export const getNeo4jStatus = () => request.get('/api/knowledge-enhanced/neo4j/status')
export const runNeo4jCypher = (data) => request.post('/api/knowledge-enhanced/neo4j/cypher', data)
export const syncNeo4j = (data) => request.post('/api/knowledge-enhanced/neo4j/sync', data)
export const getNeo4jShortestPath = (params) => request.get('/api/knowledge-enhanced/neo4j/shortest-path', { params })
export const getNeo4jKHop = (nodeId, params) => request.get(`/api/knowledge-enhanced/neo4j/k-hop/${nodeId}`, { params })
export const getNeo4jCommunities = () => request.get('/api/knowledge-enhanced/neo4j/communities')
export const getNeo4jPageRank = () => request.get('/api/knowledge-enhanced/neo4j/pagerank')
export const getNeo4jBridgeNodes = () => request.get('/api/knowledge-enhanced/neo4j/bridge-nodes')
export const getNeo4jCircularDeps = () => request.get('/api/knowledge-enhanced/neo4j/circular-deps')
export const getNeo4jStats = () => request.get('/api/knowledge-enhanced/neo4j/stats')
// 图谱推理
export const listReasoningRules = () => request.get('/api/knowledge-enhanced/reasoning/rules')
export const executeAllReasoning = (data) => request.post('/api/knowledge-enhanced/reasoning/execute-all', data)
export const executeReasoningRule = (ruleName, data) => request.post(`/api/knowledge-enhanced/reasoning/execute/${ruleName}`, data)
export const listReasoningPaths = (params) => request.get('/api/knowledge-enhanced/reasoning/paths', { params })
export const getReasoningHierarchy = () => request.get('/api/knowledge-enhanced/reasoning/hierarchy')
export const getReasoningDisambiguation = (params) => request.get('/api/knowledge-enhanced/reasoning/disambiguation', { params })
export const mergeEntities = (data) => request.post('/api/knowledge-enhanced/reasoning/merge-entities', data)
export const getReasoningChain = (params) => request.get('/api/knowledge-enhanced/reasoning/chain', { params })
// 时序图谱
export const saveTemporalRelation = (data) => request.post('/api/knowledge-enhanced/temporal/relation', data)
export const getTemporalSnapshot = (params) => request.get('/api/knowledge-enhanced/temporal/snapshot', { params })
export const getTemporalChanges = (params) => request.get('/api/knowledge-enhanced/temporal/changes', { params })
export const getNodeTimeline = (nodeId) => request.get(`/api/knowledge-enhanced/temporal/timeline/${nodeId}`)
export const getGlobalTimeline = () => request.get('/api/knowledge-enhanced/temporal/global-timeline')
export const getNodeEvolution = (nodeId) => request.get(`/api/knowledge-enhanced/temporal/evolution/${nodeId}`)
export const predictTemporalTrend = (params) => request.get('/api/knowledge-enhanced/temporal/predict-trend', { params })
export const validateTemporal = () => request.get('/api/knowledge-enhanced/temporal/validate')
// 多语言融合
export const getMultilingualEquivalents = (params) => request.get('/api/knowledge-enhanced/multilingual/equivalents', { params })
export const linkMultilingual = (data) => request.post('/api/knowledge-enhanced/multilingual/link', data)
export const fuseMultilingual = (data) => request.post('/api/knowledge-enhanced/multilingual/fuse', data)
export const getCrossLingualRelations = () => request.get('/api/knowledge-enhanced/multilingual/cross-lingual-relations')
export const getLanguageDistribution = () => request.get('/api/knowledge-enhanced/multilingual/language-distribution')
export const tagLanguage = (data) => request.post('/api/knowledge-enhanced/multilingual/tag-language', data)
export const saveAliases = (data) => request.post('/api/knowledge-enhanced/multilingual/aliases', data)
// 图嵌入
export const generateEmbedding = (data) => request.post('/api/knowledge-enhanced/embedding/generate', data)
export const predictLink = (params) => request.get('/api/knowledge-enhanced/embedding/predict-link', { params })
export const getMissingLinks = () => request.get('/api/knowledge-enhanced/embedding/missing-links')
export const getSimilarNodes = (nodeId) => request.get(`/api/knowledge-enhanced/embedding/similar-nodes/${nodeId}`)
export const clusterNodes = (data) => request.post('/api/knowledge-enhanced/embedding/cluster', data)
export const getNodeEmbedding = (nodeId) => request.get(`/api/knowledge-enhanced/embedding/node/${nodeId}`)
export const exportEmbeddings = (params) => request.get('/api/knowledge-enhanced/embedding/export', { params })
// GraphRAG
export const graphRagQa = (data) => request.post('/api/knowledge-enhanced/graphrag/qa', data)
export const graphRagEntityRetrieval = (data) => request.post('/api/knowledge-enhanced/graphrag/entity-enhanced-retrieval', data)
export const graphRagGuidedRetrieval = (data) => request.post('/api/knowledge-enhanced/graphrag/guided-retrieval', data)
export const graphRagEnhancedRanking = (data) => request.post('/api/knowledge-enhanced/graphrag/enhanced-ranking', data)
export const getGraphRagStats = () => request.get('/api/knowledge-enhanced/graphrag/stats')
// 图谱演化
export const incrementalUpdateGraph = (data) => request.post('/api/knowledge-enhanced/evolution/incremental-update', data)
export const recalculateGraphCore = () => request.post('/api/knowledge-enhanced/evolution/recalculate-core')
export const cleanupGraphRelations = (data) => request.post('/api/knowledge-enhanced/evolution/cleanup-relations', data)
export const cleanupIsolatedNodes = () => request.post('/api/knowledge-enhanced/evolution/cleanup-isolated')
export const getGraphHealth = () => request.get('/api/knowledge-enhanced/evolution/health')
export const getEvolutionLog = (params) => request.get('/api/knowledge-enhanced/evolution/log', { params })
export const getEvolutionTrend = () => request.get('/api/knowledge-enhanced/evolution/trend')
export const triggerFullEvolution = () => request.post('/api/knowledge-enhanced/evolution/trigger-full')

// ===== 提示词与检索参数（knowledge /api/prompt）=====
export const getPromptConfig = () => request.get('/prompt/config')
export const savePromptConfig = (data) => request.post('/prompt/config', data)
export const listPromptPresets = () => request.get('/prompt/presets')
export const resetPromptConfig = () => request.post('/prompt/reset')
/** RAG 检索参数配置（TopK / 阈值 / 策略 / 引用模板，后端当前为占位实现） */
export const getRetrievalConfig = () => request.get('/prompt/retrieval-config')
export const saveRetrievalConfig = (data) => request.post('/prompt/retrieval-config', data)

// 提示词模板：可命名保存多套配置并随时套用（后端当前为占位实现）
export const listPromptTemplates = (params) => request.get('/prompt/templates', { params })
export const savePromptTemplate = (data) => request.post('/prompt/templates/save', data)
export const deletePromptTemplate = (id) => request.delete(`/prompt/templates/${id}`)

// 提示词版本与发布：草稿 → 灰度 → 全量，线上出问题可一键回滚（后端待实现，见 docs/提示词版本与评测-接口约定.md）
export const listPromptVersions = (params) => request.get('/prompt/version/list', { params })
export const getPromptVersion = (id) => request.get(`/prompt/version/${id}`)
export const savePromptVersion = (data) => request.post('/prompt/version/save', data)
/** grayScale 为生效流量百分比，100 表示全量发布，0~99 表示灰度 */
export const publishPromptVersion = (id, grayScale) =>
  request.put(`/prompt/version/${id}/publish`, null, { params: { grayScale } })
export const rollbackPromptVersion = (id) => request.put(`/prompt/version/${id}/rollback`)
export const deletePromptVersion = (id) => request.delete(`/prompt/version/delete/${id}`)

// 提示词效果评测：评测集 / 用例 / 跑分报告（后端待实现，见 docs/提示词版本与评测-接口约定.md）
export const listEvalSets = () => request.get('/prompt/eval/set/list')
export const saveEvalSet = (data) => request.post('/prompt/eval/set/save', data)
export const updateEvalSet = (data) => request.put('/prompt/eval/set/update', data)
export const deleteEvalSet = (id) => request.delete(`/prompt/eval/set/delete/${id}`)
export const listEvalCases = (setId) => request.get('/prompt/eval/case/list', { params: { setId } })
export const saveEvalCase = (data) => request.post('/prompt/eval/case/save', data)
export const deleteEvalCase = (id) => request.delete(`/prompt/eval/case/delete/${id}`)
/** 以某个评测集为基准跑一次评测，返回报告 */
export const runPromptEval = (data) => request.post('/prompt/eval/run', data)
export const listEvalRuns = (params) => request.get('/prompt/eval/run/list', { params })
export const getEvalRunReport = (id) => request.get(`/prompt/eval/run/${id}`)

// ===== 多模态知识（knowledge /api/multimodal-knowledge）=====
export const listMultimodalKnowledge = (params) => request.get('/multimodal-knowledge/list', { params })
export const getMultimodalStatistics = () => request.get('/multimodal-knowledge/statistics')

/** 入库接口用 @RequestParam 接收，统一转成表单编码 */
const toFormParams = (data) => {
  const params = new URLSearchParams()
  Object.entries(data || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null) params.append(k, v)
  })
  return params
}

/** 上传图片资源（/api/image/upload），返回 fileId、storagePath 供入库使用 */
export const uploadImageResource = (formData) => request.post('/image/upload', formData)
/** 上传音频资源（/api/audio/upload） */
export const uploadAudioResource = (formData) => request.post('/audio/upload', formData)
/** 上传视频资源（/api/video/upload，后端当前为占位实现） */
export const uploadVideoResource = (formData) => request.post('/video/upload', formData)

/** 图片知识入库 */
export const indexMultimodalImage = (data) => request.post('/multimodal-knowledge/image/index', toFormParams(data))
/** 音频知识入库 */
export const indexMultimodalAudio = (data) => request.post('/multimodal-knowledge/audio/index', toFormParams(data))
/** 视频知识入库 */
export const indexMultimodalVideo = (data) => request.post('/multimodal-knowledge/video/index', toFormParams(data))

// ===== 文档知识库（RAG 入库 + 文档版本管理）=====
/** 上传文档入库（带版本记录）：PDF 走 pdf 接口，其他格式走 file 接口 */
export const uploadPdfVersioned = (formData) => request.post('/rag/upload/pdf/versioned', formData)
export const uploadFileVersioned = (formData) => request.post('/rag/upload/file/versioned', formData)
/** 上传文档入库（快速导入，不建版本记录） */
export const uploadPdfPlain = (formData) => request.post('/rag/upload/pdf', formData)
export const uploadFilePlain = (formData) => request.post('/rag/upload/file', formData)
/** 文档切片预览（后端当前为占位实现，恒返回空集合） */
export const listDocumentChunks = (params) => request.get('/rag/chunks', { params })
/** RAG 问答（旁路接口，按 userId 隔离会话记忆） */
export const ragChat = (data) => request.post('/rag/chat', toFormParams(data))

/** 文档列表（每个文档的当前版本） */
export const listDocuments = (params) => request.get('/document/version/list', { params })
/** 某文档的全部历史版本 */
export const listDocumentVersions = (documentId) => request.get(`/document/version/versions/${documentId}`)
/** 版本详情 */
export const getDocumentVersionById = (id) => request.get(`/document/version/detail/${id}`)
/** 回退到指定版本 */
export const rollbackDocumentVersion = (data) => request.post('/document/version/rollback', toFormParams(data))
/** 比较两个版本 */
export const compareDocumentVersions = (params) => request.get('/document/version/compare', { params })
/** 删除指定版本 */
export const deleteDocumentVersion = (id) => request.delete(`/document/version/delete/${id}`)
/** 通过 MD5 检查文件是否已入库 */
export const existsDocumentByMd5 = (params) => request.get('/document/version/exists', { params })

// ===== 文档切片策略与重切（knowledge /api/document/split）=====
/** 切片配置：内置预设 + 自定义策略 + 默认策略（后端当前为占位实现） */
export const getSplitConfig = (params) => request.get('/document/split/config', { params })
/** 新建或更新切片策略 */
export const saveSplitProfile = (data) => request.post('/document/split/save', data)
/** 删除切片策略 */
export const deleteSplitProfile = (id) => request.delete(`/document/split/delete/${id}`)
/** 设为默认切片策略 */
export const setDefaultSplitProfile = (id) => request.put(`/document/split/default/${id}`)
/** 试切预览：按策略试算，不落库 */
export const previewSplit = (data) => request.post('/document/split/preview', data)
/** 提交重切任务（单文档或批量） */
export const rechunkDocuments = (data) => request.post('/document/split/rechunk', data)
/** 重切任务列表 */
export const listRechunkTasks = (params) => request.get('/document/split/tasks', { params })
/** 重试失败的重切任务 */
export const retryRechunkTask = (id) => request.post(`/document/split/tasks/${id}/retry`)
