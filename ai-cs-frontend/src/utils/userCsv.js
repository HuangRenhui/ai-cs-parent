/**
 * 后台账号 CSV 导入导出（纯前端）。
 * 批量接口未就绪时，导入由前端逐条调用现有保存接口写入。
 * 导出走 listCsv，可按勾选字段裁列。
 */
import { downloadTextFile } from './listCsv'
export { downloadTextFile }

/** CSV 表头，与导入模板、导出文件保持一致 */
const HEADERS = ['用户名', '姓名', '密码', '邮箱', '手机', '性别', '状态']

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

const statusText = (s) => (s === 1 || s === '1' ? '启用' : '停用')

/** 状态留空按启用处理，避免业务只填必填列时把账号建成停用 */
const statusCode = (v) => {
  const s = String(v ?? '').trim()
  if (s === '停用' || s === '禁用' || s === '否' || s === '0') return 0
  return 1
}

/** CSV 单元格转义 */
const cell = (v) => {
  const s = v == null ? '' : String(v)
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

/** 把用户列表编成带 BOM 的 CSV，Excel 可直接打开中文 */
export const usersToCsv = (list) => {
  const lines = [HEADERS.join(',')]
  for (const row of list) {
    lines.push([
      cell(row.username),
      cell(row.realName),
      // 密码不回显，导出只留空列位，避免明文口令落盘
      '',
      cell(row.email),
      cell(row.phone),
      cell(genderText(row.gender)),
      cell(statusText(row.status))
    ].join(','))
  }
  return '\uFEFF' + lines.join('\r\n')
}

/** 带一行示例的空模板，下载后改数即可导入 */
export const userTemplateCsv = () => usersToCsv([
  { username: 'zhangsan', realName: '张三', email: 'zhangsan@example.com', phone: '13800138000', gender: 1, status: 1 }
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
 * 解析用户 CSV 文本为待写入对象。
 * 缺用户名的行跳过；缺密码的行也跳过，避免建出无法登录的账号。
 */
export const parseUserCsv = (text) => {
  const raw = String(text || '').replace(/^\uFEFF/, '')
  const lines = raw.split(/\r?\n/).filter((l) => l.trim())
  if (!lines.length) return { rows: [], errors: ['文件为空'] }
  const headers = splitCsvLine(lines[0]).map((h) => h.trim())
  const iUser = headerIndex(headers, '用户名')
  if (iUser < 0) {
    return { rows: [], errors: ['缺少表头「用户名」，请先下载模板'] }
  }
  const iName = headerIndex(headers, '姓名')
  const iPwd = headerIndex(headers, '密码')
  const iEmail = headerIndex(headers, '邮箱')
  const iPhone = headerIndex(headers, '手机')
  const iGender = headerIndex(headers, '性别')
  const iStatus = headerIndex(headers, '状态')
  const rows = []
  const errors = []
  const seen = new Set()
  for (let n = 1; n < lines.length; n++) {
    const cols = splitCsvLine(lines[n])
    const username = (cols[iUser] || '').trim()
    if (!username) {
      errors.push(`第 ${n + 1} 行缺少用户名，已跳过`)
      continue
    }
    if (seen.has(username)) {
      errors.push(`第 ${n + 1} 行用户名「${username}」与前面重复，已跳过`)
      continue
    }
    const password = iPwd >= 0 ? (cols[iPwd] || '').trim() : ''
    if (!password) {
      errors.push(`第 ${n + 1} 行缺少密码，已跳过`)
      continue
    }
    seen.add(username)
    rows.push({
      username,
      password,
      realName: iName >= 0 ? (cols[iName] || '').trim() : '',
      email: iEmail >= 0 ? (cols[iEmail] || '').trim() : '',
      phone: iPhone >= 0 ? (cols[iPhone] || '').trim() : '',
      gender: iGender >= 0 ? genderCode(cols[iGender]) : 0,
      status: iStatus >= 0 ? statusCode(cols[iStatus]) : 1
    })
  }
  return { rows, errors }
}
