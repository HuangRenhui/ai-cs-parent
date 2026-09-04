import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 35000
})

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
  if (!isWidgetPage()) {
    window.location.href = '/login'
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
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
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
    const status = error.response?.status
    const body = error.response?.data
    if (status === 401) {
      redirectLogin()
    } else if (!error.config?.silent) {
      if (body && body.msg) {
        ElMessage.error(body.msg)
      } else {
        ElMessage.error('请求失败: ' + (error.message || '网络错误'))
      }
    }
    return Promise.reject(error)
  }
)

export default request
