import { ElMessage } from 'element-plus'

/**
 * 写入类动作的统一占位：页面可操作，真正落库等后端。
 */
export const pendingBackend = (action = '该操作') => {
  ElMessage.warning(`${action}的后端接口尚未接通，当前仅页面预览`)
}
