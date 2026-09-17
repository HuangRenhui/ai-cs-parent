/**
 * 通用 CSV 导出：按勾选字段把行编成 Excel 能打开的中文表格。
 */

/** CSV 单元格转义，含逗号或换行时加引号 */
const csvCell = (v) => {
  const s = v == null ? '' : String(v)
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

/**
 * 按列定义把行数据编成带 BOM 的 CSV。
 * @param {Array} rows 数据行
 * @param {{ key: string, label: string, value?: Function }[]} columns 勾选后的字段
 */
export const rowsToCsv = (rows, columns) => {
  const header = columns.map((c) => csvCell(c.label)).join(',')
  const lines = [header]
  for (const row of rows) {
    lines.push(columns.map((c) => {
      const raw = typeof c.value === 'function' ? c.value(row) : row[c.key]
      return csvCell(raw)
    }).join(','))
  }
  return '\uFEFF' + lines.join('\r\n')
}

/** 触发浏览器下载文本文件 */
export const downloadTextFile = (filename, content, mime = 'text/csv;charset=utf-8') => {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
