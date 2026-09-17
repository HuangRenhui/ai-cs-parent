import axios from 'axios'
import { ElLoading, ElMessage } from 'element-plus'
import { isMockEnabled, mockAdapter } from '../mock'

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

const redirectLogin = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('loginType')
  if (!isWidgetPage()) {
    window.location.href = '/login'
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
    const token = localStorage.getItem('token')
    if (token && !config.headers.Authorization && !config.headers.authorization) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    const requestId = newRequestId()
    config.headers['X-Request-Id'] = requestId
    sessionStorage.setItem('lastRequestId', requestId)
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
      sessionStorage.setItem('lastRequestId', rid)
    }
    const data = response.data
    if (data && data.code === 401) {
      redirectLogin()
      return Promise.reject(new Error(data.msg || '未授权'))
    }
    if (data && typeof data.code === 'number' && data.code !== 200) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(data)
    }
    return data
  },
  error => {
    endSlowHint(error.config)
    const status = error.response?.status
    const body = error.response?.data
    if (status === 401) {
      redirectLogin()
    } else if (!error.config?.silent) {
      if (body && body.msg) {
        ElMessage.error(body.msg)
      } else {
        ElMessage.error('请求失败：' + (error.message || '网络错误'))
      }
    }
    return Promise.reject(error)
  }
)

export default request
