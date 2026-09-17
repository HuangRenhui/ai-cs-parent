const app = getApp()

Page({
  data: {
    userInfo: null
  },

  onShow() {
    if (!app.isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ userInfo: app.globalData.userInfo })
  },

  goSessions() {
    wx.navigateTo({ url: '/pages/sessions/sessions' })
  },
  goWorkOrder() {
    wx.navigateTo({ url: '/pages/workorder/workorder' })
  },
  goHelp() {
    wx.switchTab({ url: '/pages/help/help' })
  },
  goChat() {
    wx.switchTab({ url: '/pages/chat/chat' })
  },

  about() {
    wx.showModal({
      title: '关于智能客服',
      content: '多渠道智能客服小程序，支持智能问答、帮助中心与工单查询。',
      showCancel: false
    })
  },

  logout() {
    wx.showModal({
      title: '提示',
      content: '确认退出登录？',
      success: (r) => {
        if (r.confirm) {
          app.logout()
          wx.reLaunch({ url: '/pages/login/login' })
        }
      }
    })
  }
})
