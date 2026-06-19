import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 5000
})

request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('请求失败:', error)
    return Promise.reject(error)
  }
)

export default request