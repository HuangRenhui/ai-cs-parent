/**
 * 列表勾选与批量操作：表格勾选后，按现有单条接口逐条调用（删除 / 生效 / 测试等）。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useBatchSelect() {
  const selectedRows = ref([])
  const batchRemoving = ref(false)

  /** 表格 selection-change 回调 */
  const onSelect = (rows) => {
    selectedRows.value = rows
  }

  /**
   * 弹窗确认后，对勾选行逐条执行同一动作。
   * @param {(row: any) => Promise} actionFn 单条动作，入参为整行
   * @param {object} opts
   * @param {string} opts.noun 对象名，如「模型」
   * @param {string} opts.verb 动作名，如「测试」「设为生效」
   * @param {string} [opts.title] 弹窗标题
   * @param {() => any} [opts.reload] 做完刷新列表
   * @param {(row: any) => boolean} [opts.filter] 过滤可操作行
   * @param {boolean} [opts.pickOne] 同时只能操作一条时取第一条
   * @param {(row: any) => string} [opts.labelOf] 单条展示名
   */
  const batchAct = async (actionFn, opts) => {
    const {
      noun,
      verb,
      title = '提示',
      reload,
      filter,
      pickOne = false,
      labelOf = (row) => row.modelName || row.name || row.templateName || String(row.id)
    } = opts
    let rows = selectedRows.value
    if (filter) rows = rows.filter(filter)
    if (!rows.length) {
      ElMessage.warning(`请先勾选要${verb}的${noun}`)
      return
    }
    const targets = pickOne ? [rows[0]] : rows
    const label = labelOf(targets[0])
    const message = pickOne
      ? (rows.length > 1
        ? `同时只能${verb}一个${noun}，将${verb}「${label}」，确定继续？`
        : `确定${verb}「${label}」吗？`)
      : `确定${verb}选中的 ${targets.length} 条${noun}吗？`
    try {
      await ElMessageBox.confirm(message, title, { type: 'warning' })
    } catch {
      return
    }
    batchRemoving.value = true
    let ok = 0
    let fail = 0
    try {
      for (const row of targets) {
        try {
          await actionFn(row)
          ok++
        } catch {
          fail++
        }
      }
      if (ok) ElMessage.success(`已${verb} ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
      else ElMessage.error(`${verb}失败`)
      await reload?.()
    } finally {
      batchRemoving.value = false
    }
  }

  /**
   * 确认后按勾选行逐条删除。
   * @param {(id: any) => Promise} deleteFn 单条删除接口，入参为行主键
   * @param {string} noun 确认文案里的对象名，如「意图」
   * @param {() => any} reload 删完刷新列表
   * @param {(row: any) => any} idOf 取主键，默认 row.id
   */
  const batchRemove = async (deleteFn, noun, reload, idOf = (row) => row.id) => {
    const rows = selectedRows.value
    if (!rows.length) return
    try {
      await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 条${noun}吗？`, '批量删除', { type: 'warning' })
    } catch {
      return
    }
    batchRemoving.value = true
    let ok = 0
    let fail = 0
    try {
      for (const row of rows) {
        try {
          await deleteFn(idOf(row))
          ok++
        } catch {
          fail++
        }
      }
      if (ok) ElMessage.success(`已删除 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
      else ElMessage.error('删除失败')
      selectedRows.value = []
      await reload?.()
    } finally {
      batchRemoving.value = false
    }
  }

  return { selectedRows, batchRemoving, onSelect, batchRemove, batchAct }
}
