const app = getApp()
const { get } = require('../../utils/request')

Page({
  data: {
    list: [],
    loading: false
  },

  onShow() {
    if (!app.isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.load()
  },

  onPullDownRefresh() {
    this.load(() => wx.stopPullDownRefresh())
  },

  load(done) {
    this.setData({ loading: true })
    get('/session/list')
      .then((res) => {
        const d = res.data
        this.setData({ list: Array.isArray(d) ? d : (d && d.records) || [] })
      })
      .catch(() => this.setData({ list: [] }))
      .then(() => {
        this.setData({ loading: false })
        if (done) done()
      })
  },

  open(e) {
    const sid = e.currentTarget.dataset.sid
    wx.setStorageSync('activeSessionId', sid)
    wx.switchTab({ url: '/pages/chat/chat' })
  }
})
