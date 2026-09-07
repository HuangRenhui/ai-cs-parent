import request from '../utils/request'

export const sendChat = (data, config) => request.post('/ai/chat/send', data, config)

export const uploadAvatar = (file) => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/file/avatar', data)
}

export const listCustomers = (keyword) => request.get('/customer/list', { params: { keyword } })
export const saveCustomer = (data) => request.post('/customer/save', data)
export const updateCustomer = (data) => request.put('/customer/update', data)
export const deleteCustomer = (id) => request.delete(`/customer/delete/${id}`)

export const listWorkOrders = (params) => request.get('/workorder/page', { params })
export const createWorkOrder = (data) => request.post('/workorder/create', data)
export const updateWorkOrderStatus = (id, status) =>
  request.put(`/workorder/status/${id}`, null, { params: { status } })

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
export const listKnowledgeMiss = (params) => request.get('/knowledge/miss', { params })
export const importFaqs = (data) => request.post('/knowledge/import', data)
export const getKnowledgeHealth = () => request.get('/knowledge/health')

export const listSessions = () => request.get('/session/list')
export const ensureSession = (data) => request.post('/session/ensure', data)
export const saveSessionMessage = (data) => request.post('/session/message', data)
export const listSessionMessages = (sessionId) => request.get(`/session/${sessionId}/messages`)
export const endSession = (sessionId) => request.put(`/session/${sessionId}/end`)

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
export const setPackEnabled = (code, enabled) => request.put(`/open/pack/${code}/enabled`, null, { params: { enabled } })
export const activatePack = (code) => request.put(`/open/pack/${code}/activate`)

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
