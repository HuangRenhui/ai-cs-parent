/**
 * 客户 CSV 导入（纯前端）。后端批量接口未就绪时，用现有单条保存接口逐条写入。
 * 导出走 listCsv，可按勾选字段裁列。
 */
import { downloadTextFile } from './listCsv'
export { downloadTextFile }

/** CSV 表头，与导入模板、导出文件保持一致 */
const HEADERS = ['手机号', '邮箱', '昵称', '性别', '标签']

/** 库内性别码转中文，导出给业务填写 */
const genderText = (g) => {
  if (g === 1 || g === '1') return '男'
  if (g === 2 || g === '2') return '女'
  return ''
}

/** 导入时把「男/女/1/2」收成库内码，其它视为未选 */
const genderCode = (v) => {
  const s = String(v || '').trim()
  if (s === '男' || s === '1') return 1
  if (s === '女' || s === '2') return 2
  return 0
}

/** CSV 单元格转义 */
const cell = (v) => {
  const s = v == null ? '' : String(v)
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

/** 把客户列表编成带 BOM 的 CSV，Excel 可直接打开中文 */
export const customersToCsv = (list) => {
  const lines = [HEADERS.join(',')]
  for (const row of list) {
    lines.push([
      cell(row.phone),
      cell(row.email),
      cell(row.nickname),
      cell(genderText(row.gender)),
      cell(row.customerTag)
    ].join(','))
  }
  return '\uFEFF' + lines.join('\r\n')
}

/** 带一行示例的空模板，下载后改数即可导入 */
export const customerTemplateCsv = () => customersToCsv([
  { phone: '13800138000', email: 'demo@example.com', nickname: '示例客户', gender: 1, customerTag: 'VIP' }
])

/** 简易 CSV 分行（支持双引号包裹） */
const splitCsvLine = (line) => {
  const out = []
  let cur = ''
  let inQuote = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (inQuote) {
      if (ch === '"' && line[i + 1] === '"') {
        cur += '"'
        i++
      } else if (ch === '"') {
        inQuote = false
      } else {
        cur += ch
      }
    } else if (ch === '"') {
      inQuote = true
    } else if (ch === ',') {
      out.push(cur)
      cur = ''
    } else {
      cur += ch
    }
  }
  out.push(cur)
  return out
}

/** 按中文表头名定位列，缺列则返回 -1 */
const headerIndex = (headers, name) => headers.findIndex((h) => String(h).trim() === name)

/**
 * 解析客户 CSV 文本为待写入对象。缺手机号的行跳过。
 */
export const parseCustomerCsv = (text) => {
  const raw = String(text || '').replace(/^\uFEFF/, '')
  const lines = raw.split(/\r?\n/).filter((l) => l.trim())
  if (!lines.length) return { rows: [], errors: ['文件为空'] }
  const headers = splitCsvLine(lines[0]).map((h) => h.trim())
  const iPhone = headerIndex(headers, '手机号')
  if (iPhone < 0) {
    return { rows: [], errors: ['缺少表头「手机号」，请先下载模板'] }
  }
  const iEmail = headerIndex(headers, '邮箱')
  const iName = headerIndex(headers, '昵称')
  const iGender = headerIndex(headers, '性别')
  const iTag = headerIndex(headers, '标签')
  const rows = []
  const errors = []
  for (let n = 1; n < lines.length; n++) {
    const cols = splitCsvLine(lines[n])
    const phone = (cols[iPhone] || '').trim()
    if (!phone) {
      errors.push(`第 ${n + 1} 行缺少手机号，已跳过`)
      continue
    }
    rows.push({
      phone,
      email: iEmail >= 0 ? (cols[iEmail] || '').trim() : '',
      nickname: iName >= 0 ? (cols[iName] || '').trim() : '',
      gender: iGender >= 0 ? genderCode(cols[iGender]) : 0,
      customerTag: iTag >= 0 ? (cols[iTag] || '').trim() : ''
    })
  }
  return { rows, errors }
}

