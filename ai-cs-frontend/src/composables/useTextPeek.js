import { reactive } from 'vue'

/**
 * 表格长文本点击后弹窗看全文。全站共用一份状态，避免每一格都挂一个对话框。
 */
export const textPeek = reactive({
  /** 弹窗是否打开 */
  visible: false,
  /** 弹窗标题，一般用列名 */
  title: '完整内容',
  /** 格式化后的全文 */
  content: ''
})

/**
 * 像 JSON 的字符串就缩进展示，普通文本原样返回。
 * @param {*} raw 单元格原始值
 * @returns {string}
 */
export function formatPeekContent(raw) {
  if (raw == null) return ''
  const s = String(raw).trim()
  if (!s) return ''
  const looksJson = (s.startsWith('{') && s.endsWith('}')) || (s.startsWith('[') && s.endsWith(']'))
  if (looksJson) {
    try {
      return JSON.stringify(JSON.parse(s), null, 2)
    } catch {
      return String(raw)
    }
  }
  return String(raw)
}

/**
 * 打开全文弹窗；空内容不弹。
 * @param {string} title 列名
 * @param {*} raw 单元格原始值
 */
export function peekText(title, raw) {
  const content = formatPeekContent(raw)
  if (!content) return
  textPeek.title = title || '完整内容'
  textPeek.content = content
  textPeek.visible = true
}
