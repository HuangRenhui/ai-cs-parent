/**
 * 媒体资源（图片 / 音频）的演示数据与拦截逻辑。
 *
 * 图片与音频的基础处理、去重、版本、统计、标签结构高度一致，
 * 因此统一用 mediaType 区分，避免写两套几乎一样的处理器。
 */

const ok = (data, msg = '操作成功') => ({ code: 200, msg, data })
const fail = (msg, code = 400) => ({ code, msg, data: null })

/** 路径末段取参 */
const tail = (path) => decodeURIComponent(path.split('/').pop() || '')

/** 从 /api/{type}/... 里取类型 */
const typeOf = (path) => (path.startsWith('/audio') ? 'audio' : 'image')

const TYPE_LABEL = { image: '图片', audio: '音频' }

// ===================== 资源库 =====================
const mediaFiles = [
  // ---- 图片 ----
  { fileId: 'img-2001', mediaType: 'image', name: '商品主图-智能音箱.png', ext: 'png', sizeKb: 486, width: 1200, height: 1200, duration: null, tags: ['商品', '主图'], vectorized: 1, createTime: '2026-09-10 09:12:00' },
  { fileId: 'img-2002', mediaType: 'image', name: '退款流程示意图.jpg', ext: 'jpg', sizeKb: 312, width: 1080, height: 720, duration: null, tags: ['售后', '示意图'], vectorized: 1, createTime: '2026-09-11 14:30:00' },
  { fileId: 'img-2003', mediaType: 'image', name: '物流时效对照表.png', ext: 'png', sizeKb: 524, width: 960, height: 1280, duration: null, tags: ['物流'], vectorized: 0, createTime: '2026-09-12 11:05:00' },
  { fileId: 'img-2004', mediaType: 'image', name: '发票样式.jpg', ext: 'jpg', sizeKb: 198, width: 800, height: 600, duration: null, tags: ['财务'], vectorized: 1, createTime: '2026-09-13 16:40:00' },
  // ---- 音频 ----
  { fileId: 'aud-3001', mediaType: 'audio', name: '退款政策播报.mp3', ext: 'mp3', sizeKb: 1240, width: null, height: null, duration: 86, tags: ['售后', '播报'], vectorized: 1, createTime: '2026-09-10 10:20:00' },
  { fileId: 'aud-3002', mediaType: 'audio', name: '客服开场白.wav', ext: 'wav', sizeKb: 3860, width: null, height: null, duration: 34, tags: ['话术'], vectorized: 1, createTime: '2026-09-11 15:10:00' },
  { fileId: 'aud-3003', mediaType: 'audio', name: '物流查询指引.m4a', ext: 'm4a', sizeKb: 980, width: null, height: null, duration: 62, tags: ['物流'], vectorized: 0, createTime: '2026-09-12 09:55:00' }
]

/** 去重：按内容指纹算出的重复对，演示固定几组 */
const dedupPairs = {
  image: [
    { fileId: 'img-2002', dupFileId: 'img-2004', similarity: 0.96, reason: '视觉指纹几乎一致，疑似同图不同裁剪', dupName: '发票样式.jpg' },
    { fileId: 'img-2001', dupFileId: 'img-2003', similarity: 0.81, reason: '主体区域相似，背景不同', dupName: '物流时效对照表.png' }
  ],
  audio: [
    { fileId: 'aud-3002', dupFileId: 'aud-3001', similarity: 0.88, reason: '音频指纹相似度较高，可能是同一段录音的两种编码', dupName: '退款政策播报.mp3' }
  ]
}

/** 版本：每份资源的历史版本 */
const mediaVersions = {
  'img-2001': [
    { versionId: 'v3', version: 3, note: '替换为 1200×1200 高清图', sizeKb: 486, current: 1, operator: '运营管理员', createTime: '2026-09-10 09:12:00' },
    { versionId: 'v2', version: 2, note: '裁掉底部促销标签', sizeKb: 402, current: 0, operator: '运营管理员', createTime: '2026-09-05 16:20:00' },
    { versionId: 'v1', version: 1, note: '初始上传', sizeKb: 358, current: 0, operator: '超级管理员', createTime: '2026-09-01 10:00:00' }
  ],
  'aud-3001': [
    { versionId: 'v2', version: 2, note: '降噪并统一响度', sizeKb: 1240, current: 1, operator: '运营管理员', createTime: '2026-09-10 10:20:00' },
    { versionId: 'v1', version: 1, note: '初始上传', sizeKb: 1580, current: 0, operator: '超级管理员', createTime: '2026-09-02 11:30:00' }
  ]
}

/** 访问统计 */
const mediaStats = {
  image: {
    summary: { totalViews: 18420, totalDownloads: 3260, hotCount: 4, avgLoadMs: 128 },
    hot: [
      { fileId: 'img-2001', name: '商品主图-智能音箱.png', views: 8620, downloads: 1420, trend: 'up' },
      { fileId: 'img-2002', name: '退款流程示意图.jpg', views: 5240, downloads: 980, trend: 'up' },
      { fileId: 'img-2004', name: '发票样式.jpg', views: 2810, downloads: 620, trend: 'flat' },
      { fileId: 'img-2003', name: '物流时效对照表.png', views: 1750, downloads: 240, trend: 'down' }
    ]
  },
  audio: {
    summary: { totalViews: 6420, totalDownloads: 1180, hotCount: 3, avgLoadMs: 340 },
    hot: [
      { fileId: 'aud-3001', name: '退款政策播报.mp3', views: 3240, downloads: 620, trend: 'up' },
      { fileId: 'aud-3002', name: '客服开场白.wav', views: 2180, downloads: 380, trend: 'flat' },
      { fileId: 'aud-3003', name: '物流查询指引.m4a', views: 1000, downloads: 180, trend: 'down' }
    ]
  }
}

/** 防盗链白名单 */
const hotlinkWhitelist = [
  { id: 1, domain: 'shop.example.com', remark: '官方商城', createTime: '2026-08-20 10:00:00' },
  { id: 2, domain: 'm.example.com', remark: '移动站', createTime: '2026-08-20 10:02:00' }
]

/** 预置标签 */
const predefinedTags = ['商品', '主图', '详情图', '售后', '物流', '财务', '话术', '证件', '示意图']

/**
 * 媒体资源拦截逻辑。
 * @returns 命中返回响应对象，未命中返回 null。
 */
export function handleMedia(method, path, p) {
  const isMediaPath = /^\/(image|audio)\//.test(path)
  if (!isMediaPath) return null
  // 增强域之外的其它 image/audio 路径（如 /api/image/upload 已由主 mock 处理）不在这里接
  const isBase = /^\/(image|audio)\/(metadata|download|convert|resize|crop|adjust-bitrate|duration|vectorize|vector|transcribe-and-vectorize|search|enhance|(img|aud)-)/.test(path)
  if (!isBase) return null

  const type = typeOf(path)
  const typeLabel = TYPE_LABEL[type]

  // ---------- 资源列表与检索 ----------
  if (path === `/${type}/search`) {
    const kw = String(p.keyword || '').trim().toLowerCase()
    const rows = mediaFiles.filter((f) => f.mediaType === type)
    return ok(kw ? rows.filter((f) => `${f.name}${(f.tags || []).join('')}`.toLowerCase().includes(kw)) : rows)
  }

  // ---------- 基础：元数据 / 下载 / 转码 / 向量化 / 删除 ----------
  if (path.match(new RegExp(`^/${type}/metadata/`))) {
    const row = mediaFiles.find((f) => f.fileId === tail(path))
    if (!row) return fail(`${typeLabel}不存在`)
    return ok({
      ...row,
      format: row.ext,
      md5: 'd41d8cd98f00b204e9800998ecf8427e',
      storage: 'local',
      path: `/data/media/${row.mediaType}/${row.fileId}.${row.ext}`
    })
  }
  if (path.match(new RegExp(`^/${type}/download/`))) {
    return ok({ url: `/api/file/download?fileId=${tail(path)}`, expiresIn: 3600 })
  }
  if (path === `/${type}/convert`) {
    return ok({ fileId: `${type.slice(0, 3)}-${3100 + Math.floor(Math.random() * 100)}`, targetFormat: p.targetFormat || (type === 'image' ? 'webp' : 'mp3') },
      `已转换为 ${p.targetFormat || (type === 'image' ? 'webp' : 'mp3')}（演示）`)
  }
  if (path === `/${type}/vectorize` || path === '/audio/transcribe-and-vectorize') {
    const row = mediaFiles.find((f) => f.fileId === p.fileId)
    if (row) row.vectorized = 1
    return ok({ vectorId: `vec-${Date.now() % 100000}`, dimension: 1024, transcribed: path.includes('transcribe') }, '已向量化（演示）')
  }
  if (method === 'delete' && path.match(new RegExp(`^/${type}/(img|aud)-`))) {
    const id = tail(path)
    const i = mediaFiles.findIndex((f) => f.fileId === id)
    if (i >= 0) mediaFiles.splice(i, 1)
    return ok('删除成功')
  }
  if (method === 'delete' && path.includes('/vector/')) {
    return ok('向量已删除')
  }
  if (path === '/image/resize') {
    return ok({ width: p.width || 800, height: p.height || 800, sizeKb: 268 }, '已缩放（演示）')
  }
  if (path === '/image/crop') {
    return ok({ width: p.width || 600, height: p.height || 400, sizeKb: 142 }, '已裁剪（演示）')
  }
  if (path === '/audio/adjust-bitrate') {
    return ok({ bitrate: p.bitrate || 128, sizeKb: 986 }, '码率已调整（演示）')
  }
  if (path === '/audio/duration') {
    return ok({ duration: 86, format: 'mp3' })
  }

  // ---------- 增强：批量上传 ----------
  if (path === `/${type}/enhance/batch/upload`) {
    return ok({ batchId: `batch-${Date.now() % 100000}`, total: 5, accepted: 5 }, '批量上传任务已创建（演示）')
  }
  if (path.includes('/enhance/batch/progress/')) {
    return ok({ batchId: tail(path), total: 5, done: 4, failed: 1, percent: 80, status: 'running' })
  }

  // ---------- 增强：去重 ----------
  if (path === `/${type}/enhance/dedup/check`) {
    const pairs = dedupPairs[type] || []
    return ok({ duplicated: pairs.length > 0, matches: pairs, checkedCount: 1 })
  }
  if (path === `/${type}/enhance/dedup/similar`) {
    return ok(dedupPairs[type] || [])
  }

  // ---------- 增强：版本 ----------
  if (path.match(new RegExp(`^/${type}/enhance/version/[^/]+$`))) {
    const fileId = tail(path)
    return ok(mediaVersions[fileId] || [])
  }
  if (path.endsWith('/switch') || path.endsWith('/rollback')) {
    // 路径是 /image/enhance/version/{fileId}/switch，fileId 在下标 4（不能按带 /api 的旧写法取 5）
    const fileId = path.split('/')[4]
    const rows = mediaVersions[fileId]
    if (rows) rows.forEach((v) => { v.current = String(v.versionId) === String(p.versionId) ? 1 : 0 })
    return ok(path.endsWith('/switch') ? '已切换到该版本（演示）' : '已回滚（演示）')
  }
  if (path.endsWith('/diff')) {
    return ok({
      from: 'v2', to: 'v3',
      changes: [
        { field: '尺寸', before: '1080 × 1080', after: '1200 × 1200' },
        { field: '体积', before: '402 KB', after: '486 KB' },
        { field: '说明', before: '裁掉底部促销标签', after: '替换为 1200×1200 高清图' }
      ]
    })
  }

  // ---------- 增强：统计 ----------
  if (path === `/${type}/enhance/stats/hot`) return ok(mediaStats[type].hot)
  if (path === `/${type}/enhance/stats/summary`) return ok(mediaStats[type].summary)
  if (path.match(new RegExp(`^/${type}/enhance/stats/[^/]+$`))) {
    const row = mediaFiles.find((f) => f.fileId === tail(path))
    return ok({
      fileId: tail(path), name: row ? row.name : '未知资源',
      views: 3240, downloads: 620, lastAccessTime: '2026-09-15 11:20:00',
      daily: [120, 186, 154, 210, 268, 302, 246].map((v, i) => ({ date: `2026-09-0${i + 9}`, views: v }))
    })
  }
  if (path.endsWith('/view') || path.endsWith('/download') || path.endsWith('/play')) {
    return ok('已记录')
  }

  // ---------- 增强：图片防盗链 ----------
  if (path === '/image/enhance/hotlink/whitelist') {
    if (method === 'get') return ok(hotlinkWhitelist)
    if (method === 'post') {
      if (!p.domain) return fail('请填写域名')
      hotlinkWhitelist.push({ id: Date.now() % 100000, domain: p.domain, remark: p.remark || '', createTime: '2026-09-15 12:00:00' })
      return ok('已加入白名单')
    }
    const i = hotlinkWhitelist.findIndex((x) => x.domain === p.domain)
    if (i >= 0) hotlinkWhitelist.splice(i, 1)
    return ok('已移出白名单')
  }
  if (path === '/image/enhance/hotlink/token') {
    return ok({ token: `tk_${Date.now().toString(36)}`, expiresIn: 7200 })
  }
  if (path === '/image/enhance/hotlink/signed-url') {
    const fileId = p.fileId || 'img-2001'
    return ok({ url: `/api/image/download/${fileId}?sign=demo-signature`, expiresIn: 3600 })
  }

  // ---------- 增强：智能标签 ----------
  if (path === `/${type}/enhance/tags/auto`) {
    return ok({ tagged: 3, tags: type === 'image' ? ['商品', '主图', '白底'] : ['售后', '播报', '中文'] }, '标签已生成（演示）')
  }
  if (path === '/image/enhance/tags/predefined') return ok(predefinedTags)
  if (path.match(new RegExp(`^/${type}/enhance/tags/suggest/[^/]+$`))) {
    return ok([
      { tag: type === 'image' ? '商品' : '话术', score: 0.91 },
      { tag: type === 'image' ? '主图' : '售后', score: 0.84 },
      { tag: '高置信', score: 0.62 }
    ])
  }
  if (path === `/${type}/enhance/tags/search`) {
    const kw = String(p.tag || '').trim()
    const rows = mediaFiles.filter((f) => f.mediaType === type && (!kw || (f.tags || []).some((t) => t.includes(kw))))
    return ok(rows)
  }
  if (path.match(new RegExp(`^/${type}/enhance/tags/[^/]+$`))) {
    const fileId = tail(path)
    if (method === 'get') {
      const row = mediaFiles.find((f) => f.fileId === fileId)
      return ok((row && row.tags) || [])
    }
    if (method === 'delete') {
      const row = mediaFiles.find((f) => f.fileId === fileId)
      if (row && p.tag) row.tags = row.tags.filter((t) => t !== p.tag)
      return ok('标签已移除')
    }
    const row = mediaFiles.find((f) => f.fileId === fileId)
    if (row && p.tags) row.tags = Array.isArray(p.tags) ? p.tags : String(p.tags).split(',').filter(Boolean)
    return ok('标签已保存')
  }

  // ---------- 增强：识别与审核 ----------
  if (path === '/image/enhance/ocr') {
    return ok({
      text: '订单编号：SO10086　退款金额：￥128.00　申请时间：2026-09-12',
      confidence: 0.93,
      blocks: [
        { text: '订单编号：SO10086', box: [10, 20, 320, 60] },
        { text: '退款金额：￥128.00', box: [10, 80, 300, 120] }
      ]
    }, '识别完成（演示）')
  }
  if (path === '/audio/enhance/asr') {
    return ok({
      text: '您好，我想问一下退款大概多久能到账？',
      duration: 6,
      confidence: 0.91,
      segments: [
        { start: 0, end: 3, text: '您好，我想问一下' },
        { start: 3, end: 6, text: '退款大概多久能到账？' }
      ]
    }, '转写完成（演示）')
  }
  if (path === `/${type}/enhance/moderate`) {
    return ok({
      conclusion: 'pass',
      riskLevel: '低',
      categories: [
        { name: '涉政', risk: 0.01 },
        { name: '暴恐', risk: 0.00 },
        { name: '色情', risk: 0.02 },
        { name: type === 'image' ? '广告法违禁词' : '辱骂', risk: type === 'image' ? 0.08 : 0.03 }
      ]
    }, '审核通过（演示）')
  }
  if (path.startsWith('/audio/enhance/waveform/')) {
    return ok({
      fileId: tail(path),
      duration: 86,
      // 演示波形：一段平滑的正弦叠加，前端可直接画柱状
      peaks: Array.from({ length: 60 }, (_, i) => Number((Math.abs(Math.sin(i / 4)) * 0.8 + 0.1).toFixed(2)))
    })
  }
  if (path.startsWith('/image/enhance/storage/url/')) {
    return ok({ url: `/api/image/download/${tail(path)}`, storage: 'local' })
  }

  return null
}
