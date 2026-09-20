const app = getApp()
const { get, post, upload } = require('../../utils/request')

/** 附件上传接口（网关 RewritePath 到 base-service 的 /file/chat-attachment） */
const ATTACHMENT_UPLOAD_URL = '/file/chat-attachment'

/** 分类到图标 / 文案的映射，用于文件卡片展示 */
const CATEGORY_ICON = {
  image: '🖼',
  document: '📄',
  audio: '🎵',
  video: '🎬',
  archive: '🗜',
  other: '📎'
}

/** 字节数转可读大小 */
function formatSize(bytes) {
  const n = Number(bytes)
  if (!n || n < 0) return ''
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

/** 组装附件展示字段：补图标、大小文案与完整地址 */
function decorateAttachment(att) {
  const url = att.url || ''
  const base = app.globalData && app.globalData.baseUrl ? app.globalData.baseUrl : ''
  return Object.assign({}, att, {
    icon: CATEGORY_ICON[att.category] || CATEGORY_ICON.other,
    sizeText: formatSize(att.fileSize),
    fullUrl: /^https?:\/\//.test(url) ? url : base + url
  })
}

Page({
  data: {
    messages: [],
    input: '',
    sessionId: '',
    sending: false,
    uploading: false,
    /** 待发送附件 */
    pending: [],
    /** 附件来源选择面板 */
    showAttachPanel: false,
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
        const msgs = (res.data || []).map((m) => {
          const row = Object.assign({}, m, { mine: m.msgType === 1 })
          if (row.attachments && row.attachments.length) {
            row.attachments = row.attachments.map(decorateAttachment)
          }
          return row
        })
        this.setData({ messages: msgs })
        this.scrollBottom()
      })
      .catch(() => {})
  },

  onInput(e) {
    this.setData({ input: e.detail.value })
  },

  /* ===== 附件选择与上传 ===== */

  showAttachMenu() {
    this.setData({ showAttachPanel: true })
  },

  hideAttachMenu() {
    this.setData({ showAttachPanel: false })
  },

  /** 面板内的空操作，阻止点击穿透到遮罩 */
  noop() {},

  /**
   * 按来源选择附件：相册图片 / 拍照 / 任意文件。
   * <p>微信未提供剪贴板图片接口，故图片只能通过相册或拍照获取；
   * 文本粘贴由 textarea 原生支持，无需额外处理。</p>
   */
  chooseAttachment(e) {
    const type = e.currentTarget.dataset.type
    this.setData({ showAttachPanel: false })

    if (type === 'file') {
      wx.chooseMessageFile({
        count: 9,
        type: 'all',
        success: (res) => {
          this.uploadFiles((res.tempFiles || []).map((f) => ({
            path: f.path,
            name: f.name || '未命名文件'
          })))
        },
        fail: () => {}
      })
      return
    }

    wx.chooseMedia({
      count: 9,
      mediaType: ['image'],
      sourceType: type === 'camera' ? ['camera'] : ['album'],
      sizeType: ['compressed'],
      success: (res) => {
        this.uploadFiles((res.tempFiles || []).map((f, i) => ({
          path: f.tempFilePath,
          name: '图片_' + Date.now() + '_' + i + '.jpg'
        })))
      },
      fail: () => {}
    })
  },

  /** 逐个上传附件，全部完成后追加到待发送列表 */
  uploadFiles(files) {
    if (!files.length) return
    this.setData({ uploading: true })

    const tasks = files.map((f) => upload(ATTACHMENT_UPLOAD_URL, f.path, {}, 'file')
      .then((res) => {
        const data = (res && res.data) || null
        if (!data) return null
        // 小程序 wx.uploadFile 不带原始文件名，这里用本地文件名补齐
        return decorateAttachment(Object.assign({}, data, {
          fileName: data.fileName || f.name
        }))
      })
      .catch(() => {
        wx.showToast({ title: '附件上传失败', icon: 'none' })
        return null
      }))

    Promise.all(tasks)
      .then((list) => {
        const ok = list.filter(Boolean)
        if (ok.length) {
          this.setData({ pending: this.data.pending.concat(ok) })
        }
      })
      .then(() => this.setData({ uploading: false }))
  },

  removePending(e) {
    const index = Number(e.currentTarget.dataset.index)
    const pending = this.data.pending.slice()
    pending.splice(index, 1)
    this.setData({ pending })
  },

  /* ===== 消息展示 ===== */

  /** 点击图片放大预览 */
  previewImage(e) {
    const url = e.currentTarget.dataset.url
    const urls = []
    this.data.messages.forEach((m) => {
      (m.attachments || []).forEach((a) => {
        if (a.category === 'image') urls.push(a.fullUrl || a.url)
      })
    })
    const current = urls.find((u) => u.indexOf(url) >= 0) || url
    wx.previewImage({ current, urls: urls.length ? urls : [url] })
  },

  /**
   * 打开非图片附件。
   * <p>小程序无法直接下载任意文件，图片走预览、其余类型复制链接或提示。</p>
   */
  openAttachment(e) {
    const url = e.currentTarget.dataset.url
    wx.setClipboardData({
      data: url,
      success: () => wx.showToast({ title: '附件链接已复制', icon: 'none' })
    })
  },

  /* ===== 发送 ===== */

  send() {
    const text = (this.data.input || '').trim()
    const attachments = this.data.pending || []
    // 允许「纯附件、无文本」发送
    if ((!text && !attachments.length) || this.data.sending || this.data.uploading) return

    const mine = {
      id: Date.now(),
      msgContent: text,
      msgType: 1,
      mine: true,
      attachments,
      createTime: '刚刚'
    }
    this.setData({
      messages: this.data.messages.concat(mine),
      input: '',
      pending: [],
      sending: true
    })
    this.scrollBottom()

    const payloadAttachments = attachments.map((a) => ({
      fileId: a.fileId,
      url: a.url,
      fileName: a.fileName,
      category: a.category,
      contentType: a.contentType,
      fileSize: a.fileSize
    }))

    post('/ai/chat/send', {
      msg: text,
      sessionId: this.data.sessionId,
      attachments: payloadAttachments
    })
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
