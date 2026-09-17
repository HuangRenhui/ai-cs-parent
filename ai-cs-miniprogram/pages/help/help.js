const { get, post } = require('../../utils/request')
const config = require('../../utils/config')

Page({
  data: {
    keyword: '',
    categories: [],
    activeCategory: '',
    faqs: [],
    reply: '',
    citations: [],
    searching: false,
    expandedId: null
  },

  onShow() {
    this.loadCategories()
    this.loadFaqs()
  },

  loadCategories() {
    get('/help-center/categories', { tenantCode: config.TENANT_CODE })
      .then((res) => this.setData({ categories: res.data || [] }))
      .catch(() => {})
  },

  loadFaqs() {
    get('/help-center/faqs', { tenantCode: config.TENANT_CODE, category: this.data.activeCategory || undefined })
      .then((res) => {
        const d = res.data
        this.setData({ faqs: Array.isArray(d) ? d : (d && d.records) || [] })
      })
      .catch(() => this.setData({ faqs: [] }))
  },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    const q = (this.data.keyword || '').trim()
    if (!q) {
      wx.showToast({ title: '请输入问题', icon: 'none' })
      return
    }
    this.setData({ searching: true, reply: '', citations: [] })
    get('/help-center/search', { question: q, tenantCode: config.TENANT_CODE })
      .then((res) => {
        const d = res.data || {}
        this.setData({ reply: d.reply || '', citations: d.citations || [] })
      })
      .catch(() => {})
      .then(() => this.setData({ searching: false }))
  },

  switchCategory(e) {
    const cat = e.currentTarget.dataset.cat || ''
    this.setData({ activeCategory: cat === this.data.activeCategory ? '' : cat }, () => this.loadFaqs())
  },

  toggle(e) {
    const id = e.currentTarget.dataset.id
    const willOpen = this.data.expandedId !== id
    this.setData({ expandedId: willOpen ? id : null })
    if (willOpen) {
      post('/help-center/view/' + id).catch(() => {})
    }
  },

  like(e) {
    this.feedback(e.currentTarget.dataset.id, 'like')
  },
  dislike(e) {
    this.feedback(e.currentTarget.dataset.id, 'dislike')
  },
  feedback(id, type) {
    post('/help-center/feedback/' + id + '?type=' + type).catch(() => {})
    wx.showToast({ title: type === 'like' ? '感谢反馈' : '我们会继续改进', icon: 'none' })
  },

  onShareAppMessage() {
    return { title: '智能客服帮助中心 · 常见问题一看就懂', path: '/pages/help/help' }
  }
})
