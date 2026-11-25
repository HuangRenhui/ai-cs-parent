import axios from 'axios'
import { ElLoading, ElMessage } from 'element-plus'
import { isMockEnabled, mockAdapter } from '../mock'
import { clearSession } from './auth'
import { getItem, sessionGetItem, sessionSetItem } from './safeStorage'

const axiosOptions = { baseURL: '/api', timeout: 35000 }
if (isMockEnabled()) {
  axiosOptions.adapter = mockAdapter
}
const request = axios.create(axiosOptions)

const newRequestId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now()}${Math.random().toString(16).slice(2)}`
}

const isWidgetPage = () => typeof location !== 'undefined' && location.pathname.startsWith('/widget')

/** 业务错误统一成 Error：调用方用 e.message / e.code 都能读，不再抛裸对象 */
const toBizError = (data) => Object.assign(new Error((data && data.msg) || '请求失败'), {
  code: data && data.code,
  payload: data
})

/**
 * 同一 GET 的并发请求复用同一个 Promise：
 * 不取消任何调用方，避免组件收到「取消异常」后误报错误；也不会让旧响应覆盖新结果。
 */
const inflightGets = new Map()

/** 超时/断网/网关错误统一中文文案，不把 axios 英文原文抛给用户 */
const friendlyMessage = (error) => {
  const status = error.response?.status
  const body = error.response?.data
  if (body && body.msg) return body.msg
  if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
    return '请求超时，请稍后重试'
  }
  if (error.code === 'ERR_NETWORK' || error.message === 'Network Error') {
    return '网络连接失败，请检查网络后重试'
  }
  if (status === 500) return '服务开小差了，请稍后重试'
  if (status === 502 || status === 503 || status === 504) return '服务暂不可用，请稍后重试'
  return '请求失败，请稍后重试'
}

const redirectLogin = () => {
  // 统一走 clearSession，避免以后新增会话字段时这里漏清
  clearSession()
  if (!isWidgetPage()) {
    window.location.href = '/login'
  }
}

/** 记录请求 ID：保留最近 10 个，避免并发请求互相覆盖导致排障时拿不到链路 */
const rememberRequestId = (rid) => {
  if (!rid) return
  sessionSetItem('lastRequestId', rid)
  try {
    const prev = JSON.parse(sessionGetItem('recentRequestIds') || '[]')
    const list = Array.isArray(prev) ? prev : []
    const next = [rid, ...list.filter(item => item !== rid)].slice(0, 10)
    sessionSetItem('recentRequestIds', JSON.stringify(next))
  } catch {
    sessionSetItem('recentRequestIds', JSON.stringify([rid]))
  }
}

/** 请求超过该时长仍未返回，才弹出全屏「请等待」，避免快请求闪一下 */
const SLOW_HINT_MS = 400
let slowHintPending = 0
let slowHintTimer = null
let slowHintInst = null

/** 对话流式已有「正在思考」，登录按钮自己转圈，这两类不再盖全屏 */
const shouldSkipSlowHint = (config) => {
  if (!config || config.skipLoading || config.silent) return true
  const url = String(config.url || '')
  if (url.includes('/ai/chat') || url.includes('/ai/stream') || url.includes('/chat/interrupt')) return true
  if (url.includes('/auth/login')) return true
  return false
}

/** 开始计时：慢了再出遮罩，多个请求共用一层 */
const beginSlowHint = (config) => {
  if (shouldSkipSlowHint(config)) return
  config.__slowHint = true
  slowHintPending += 1
  if (slowHintPending !== 1 || slowHintTimer || slowHintInst) return
  slowHintTimer = setTimeout(() => {
    slowHintTimer = null
    if (slowHintPending > 0 && !slowHintInst) {
      slowHintInst = ElLoading.service({
        lock: true,
        text: '数据加载中，请等待',
        background: 'rgba(255, 255, 255, 0.62)'
      })
    }
  }, SLOW_HINT_MS)
}

/** 请求结束：计数归零后关掉遮罩 */
const endSlowHint = (config) => {
  if (!config?.__slowHint) return
  config.__slowHint = false
  slowHintPending = Math.max(0, slowHintPending - 1)
  if (slowHintPending > 0) return
  if (slowHintTimer) {
    clearTimeout(slowHintTimer)
    slowHintTimer = null
  }
  if (slowHintInst) {
    slowHintInst.close()
    slowHintInst = null
  }
}

request.interceptors.request.use(
  config => {
    const token = getItem('token')
    if (token && !config.headers.Authorization && !config.headers.authorization) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    const requestId = newRequestId()
    config.headers['X-Request-Id'] = requestId
    rememberRequestId(requestId)

    beginSlowHint(config)
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    endSlowHint(response.config)
    const rid = response.headers?.['x-request-id']
    if (rid) {
      rememberRequestId(rid)
    }
    const data = response.data
    if (data && data.code === 401) {
      redirectLogin()
      return Promise.reject(toBizError(data))
    }
    if (data && typeof data.code === 'number' && data.code !== 200) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(toBizError(data))
    }
    return data
  },
  error => {
    endSlowHint(error.config)
    // 被去重逻辑主动取消的请求：不提示、不当作失败上报
    if (error.code === 'ERR_CANCELED' || error.name === 'CanceledError') {
      error.__canceled = true
      return Promise.reject(error)
    }
    const status = error.response?.status
    if (status === 401) {
      redirectLogin()
    } else if (status === 403) {
      // 无权限（如岗位角色不匹配）单独提示，避免落到泛化的「请求失败」
      if (!error.config?.silent) {
        ElMessage.error(error.response?.data?.msg || '没有权限执行该操作')
      }
    } else if (!error.config?.silent) {
      ElMessage.error(friendlyMessage(error))
    }
    return Promise.reject(error)
  }
)

/**
 * GET 去重：相同 url + params 的并发请求共享同一个 Promise。
 * 需要各自独立请求时传 `{ allowDuplicate: true }`。
 */
const rawGet = request.get.bind(request)
request.get = (url, config) => {
  const options = config || {}
  if (options.allowDuplicate === true) {
    return rawGet(url, options)
  }
  const key = `${url}?${JSON.stringify(options.params || {})}|${options.responseType || ''}`
  const existing = inflightGets.get(key)
  if (existing) {
    return existing
  }
  const pending = rawGet(url, options).finally(() => {
    if (inflightGets.get(key) === pending) {
      inflightGets.delete(key)
    }
  })
  inflightGets.set(key, pending)
  return pending
}

export default request
