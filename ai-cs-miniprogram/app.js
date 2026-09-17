/**
 * 智能客服小程序入口
 * 与 Web 端共用一套网关接口，登录态用本地缓存 token。
 */
App({
  globalData: {
    token: '',
    userInfo: null,
    tenantCode: 'default',
    sessionId: ''
  },

  onLaunch() {
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
    }
    const userInfo = wx.getStorageSync('userInfo')
    if (userInfo) {
      this.globalData.userInfo = userInfo
    }
  },

  /** 登录成功后保存会话 */
  setLogin(token, userInfo) {
    this.globalData.token = token || ''
    this.globalData.userInfo = userInfo || null
    wx.setStorageSync('token', token || '')
    wx.setStorageSync('userInfo', userInfo || null)
  },

  /** 是否已登录 */
  isLoggedIn() {
    return !!this.globalData.token
  },

  /** 退出登录 */
  logout() {
    this.globalData.token = ''
    this.globalData.userInfo = null
    this.globalData.sessionId = ''
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
  }
})
