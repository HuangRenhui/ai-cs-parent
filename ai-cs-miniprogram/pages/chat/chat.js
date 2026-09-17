const app = getApp()
const { get, post } = require('../../utils/request')

Page({
  data: {
    messages: [],
    input: '',
    sessionId: '',
    sending: false,
    scrollIntoView: ''
  },

  onShow() {
    if (!app.isLoggedIn()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    const sid = wx.getStorageSync('activeSessionId') || app.globalData.sessionId || ''
    if (sid && sid !== this.data.sessionId) {
      this.setData({ sessionId: sid })
      this.loadMessages(sid)
    }
  },

  loadMessages(sid) {
    get('/session/' + sid + '/messages')
      .then((res) => {
        const msgs = (res.data || []).map((m) => Object.assign({}, m, { mine: m.msgType === 1 }))
        this.setData({ messages: msgs })
        this.scrollBottom()
      })
      .catch(() => {})
  },

  onInput(e) {
    this.setData({ input: e.detail.value })
  },

  send() {
    const text = (this.data.input || '').trim()
    if (!text || this.data.sending) return
    const mine = { id: Date.now(), msgContent: text, msgType: 1, mine: true, createTime: '刚刚' }
    this.setData({ messages: this.data.messages.concat(mine), input: '', sending: true })
    this.scrollBottom()

    post('/ai/chat/send', { question: text, sessionId: this.data.sessionId })
      .then((res) => {
        const reply = (res.data && res.data.reply) || '（演示）已收到您的问题，正在为您处理。'
        const sid = (res.data && res.data.sessionId) || this.data.sessionId
        const bot = { id: Date.now() + 1, msgContent: reply, msgType: 2, mine: false, createTime: '刚刚' }
        if (sid) {
          app.globalData.sessionId = sid
          this.setData({ sessionId: sid })
        }
        this.setData({ messages: this.data.messages.concat(bot) })
        this.scrollBottom()
      })
      .catch(() => {})
      .then(() => this.setData({ sending: false }))
  },

  scrollBottom() {
    this.setData({ scrollIntoView: 'msg-bottom' })
  },

  goHelp() {
    wx.switchTab({ url: '/pages/help/help' })
  },

  onShareAppMessage() {
    return { title: '智能客服 · 有问题随时问我', path: '/pages/index/index' }
  }
})
