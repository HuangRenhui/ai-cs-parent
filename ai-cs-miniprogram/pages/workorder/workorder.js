const app = getApp()
const { get } = require('../../utils/request')

const STATUS_TEXT = { 1: '待处理', 2: '处理中', 3: '已完成' }

Page({
  data: {
    list: [],
    status: '',
    loading: false,
    tabs: [
      { value: '', label: '全部' },
      { value: 1, label: '待处理' },
      { value: 2, label: '处理中' },
      { value: 3, label: '已完成' }
    ]
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
    const params = { pageNum: 1, pageSize: 20 }
    if (this.data.status !== '') {
      params.status = this.data.status
    }
    get('/workorder/page', params)
      .then((res) => {
        const d = res.data
        const records = (d && d.records) || (Array.isArray(d) ? d : [])
        this.setData({
          list: records.map((r) => Object.assign({}, r, { statusText: STATUS_TEXT[r.orderStatus] || '未知' }))
        })
      })
      .catch(() => this.setData({ list: [] }))
      .then(() => {
        this.setData({ loading: false })
        if (done) done()
      })
  },

  switchTab(e) {
    const raw = e.currentTarget.dataset.value
    const v = raw === '' || raw === undefined || raw === null ? '' : Number(raw)
    this.setData({ status: v }, () => this.load())
  },

  detail(e) {
    const item = this.data.list[e.currentTarget.dataset.index]
    if (!item) return
    wx.showModal({
      title: item.orderNo,
      content: item.orderContent + '\n状态：' + item.statusText + '\n类型：' + item.orderType,
      showCancel: false
    })
  }
})
