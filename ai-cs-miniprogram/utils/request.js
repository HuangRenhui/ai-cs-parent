/**
 * 请求封装：与 Web 端 utils/request 对齐（token 注入、Result 解包、401 跳登录）。
 * USE_MOCK=true 时直接走本地假数据，不发网络。
 */
const config = require('./config')
const mock = require('./mock')

function request(options) {
  const method = String(options.method || 'GET').toUpperCase()
  const url = options.url
  const data = options.data || {}
  const silent = !!options.silent

  if (config.USE_MOCK) {
    return mock.handle(method, url, data)
  }

  const app = getApp()
  const token = app && app.globalData ? app.globalData.token : wx.getStorageSync('token')

  return new Promise((resolve, reject) => {
    wx.request({
      url: config.BASE_URL + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401 || body.code === 401) {
          const a = getApp()
          if (a && a.logout) a.logout()
          if (!silent) wx.showToast({ title: '登录已过期', icon: 'none' })
          setTimeout(() => wx.navigateTo({ url: '/pages/login/login' }), 400)
          reject(body)
          return
        }
        if (typeof body.code === 'number' && body.code !== 200) {
          if (!silent) wx.showToast({ title: body.msg || '请求失败', icon: 'none' })
          reject(body)
          return
        }
        resolve(body)
      },
      fail(err) {
        if (!silent) wx.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        reject(err)
      }
    })
  })
}

const get = (url, data, options) => request(Object.assign({ url, method: 'GET', data }, options))
const post = (url, data, options) => request(Object.assign({ url, method: 'POST', data }, options))
const put = (url, data, options) => request(Object.assign({ url, method: 'PUT', data }, options))
const del = (url, data, options) => request(Object.assign({ url, method: 'DELETE', data }, options))

/**
 * 上传文件（multipart/form-data）。USE_MOCK=true 时不发网络，直接返回假结果。
 * @param {string} url 接口路径
 * @param {string} filePath 本地临时文件路径
 * @param {object} formData 附加表单字段
 * @param {string} name 文件字段名，默认 file
 */
function upload(url, filePath, formData, name) {
  if (config.USE_MOCK) {
    const fileName = String(filePath || '').split('/').pop()
    return mock.handle('POST', url, Object.assign({ file: { name: fileName } }, formData || {}))
  }

  const app = getApp()
  const token = app && app.globalData ? app.globalData.token : wx.getStorageSync('token')

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: config.BASE_URL + url,
      filePath,
      name: name || 'file',
      formData: formData || {},
      header: { Authorization: token ? 'Bearer ' + token : '' },
      success(res) {
        let body = res.data
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          /* 非 JSON 响应保持原样 */
        }
        if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 200) {
          wx.showToast({ title: body.msg || '上传失败', icon: 'none' })
          reject(body)
          return
        }
        resolve(body)
      },
      fail(err) {
        wx.showToast({ title: '上传失败，请稍后重试', icon: 'none' })
        reject(err)
      }
    })
  })
}

module.exports = { request, get, post, put, del, upload }
