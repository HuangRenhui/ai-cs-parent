const app = getApp()
const { post } = require('../../utils/request')
const config = require('../../utils/config')

Page({
  data: {
    username: '',
    password: '',
    loading: false
  },

  onUsername(e) {
    this.setData({ username: e.detail.value })
  },

  onPassword(e) {
    this.setData({ password: e.detail.value })
  },

  submit() {
    const { username, password } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请填写账号与密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    post('/auth/login', { username, password, tenantCode: config.TENANT_CODE })
      .then((res) => {
        const d = res.data || {}
        app.setLogin(d.token, d)
        wx.showToast({ title: '登录成功', icon: 'success' })
        setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 500)
      })
      .catch(() => {})
      .then(() => this.setData({ loading: false }))
  },

  /** 一键体验：直接走演示账号 */
  guest() {
    this.setData({ username: 'guest', password: 'guest' })
    this.submit()
  },

  /** 微信一键授权登录：wx.login 拿 code，交给后端换 token */
  wxLogin() {
    wx.login({
      success: (res) => {
        if (!res.code) {
          wx.showToast({ title: '获取微信授权失败', icon: 'none' })
          return
        }
        this.setData({ loading: true })
        post('/auth/login/wechat', { code: res.code, tenantCode: config.TENANT_CODE })
          .then((r) => {
            const d = r.data || {}
            app.setLogin(d.token, d)
            wx.showToast({ title: '登录成功', icon: 'success' })
            setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 500)
          })
          .catch(() => {})
          .then(() => this.setData({ loading: false }))
      },
      fail: () => wx.showToast({ title: '微信登录不可用，请用账号登录', icon: 'none' })
    })
  }
})
