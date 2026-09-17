const app = getApp()
const { get } = require('../../utils/request')

Page({
  data: {
    greeting: '你好',
    userInfo: null,
    recent: []
  },

  onShow() {
    if (!app.isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    const h = new Date().getHours()
    const greet = h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
    this.setData({ greeting: greet, userInfo: app.globalData.userInfo })
    this.loadRecent()
  },

  loadRecent() {
    get('/session/list')
      .then((res) => {
        const list = Array.isArray(res.data) ? res.data : (res.data && res.data.records) || []
        this.setData({ recent: list.slice(0, 3) })
      })
      .catch(() => this.setData({ recent: [] }))
  },

  goChat() {
    wx.switchTab({ url: '/pages/chat/chat' })
  },
  goHelp() {
    wx.switchTab({ url: '/pages/help/help' })
  },
  goWorkOrder() {
    wx.navigateTo({ url: '/pages/workorder/workorder' })
  },
  goSessions() {
    wx.navigateTo({ url: '/pages/sessions/sessions' })
  },
  goProfile() {
    wx.switchTab({ url: '/pages/profile/profile' })
  },
  openSession(e) {
    const sid = e.currentTarget.dataset.sid
    wx.setStorageSync('activeSessionId', sid)
    wx.switchTab({ url: '/pages/chat/chat' })
  },

  onShareAppMessage() {
    return { title: '智能客服 · 有问题随时问我', path: '/pages/index/index' }
  },
  onShareTimeline() {
    return { title: '智能客服 · 有问题随时问我' }
  }
})
