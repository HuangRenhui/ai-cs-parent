/**
 * 前端演示开关：默认走内存假数据，不请求后端。
 * 联通真接口时：localStorage.setItem('aiCsUseRealApi','1') 后刷新，
 * 或启动前设置环境变量 VITE_USE_REAL_API=true。
 *
 * 发布正式环境请使用 `npm run build:release`（对应 .env.release），
 * 普通 `npm run build` 仍保留演示模式，供未建库的演示环境使用。
 */
import { getItem } from '../utils/safeStorage'

const env = import.meta.env

export const isMockEnabled = () => {
  // 隐私模式下直接访问 localStorage 会抛 SecurityError，safeStorage 已做内存降级
  if (getItem('aiCsUseRealApi') === '1') {
    return false
  }
  return env.VITE_USE_REAL_API !== 'true'
}

// 生产构建仍走 mock 时给出显式告警，避免「以为上线了，实际全是假数据」
if (env.PROD && isMockEnabled() && typeof console !== 'undefined') {
  console.warn(
    '[ai-cs] 当前为演示构建：所有接口走本地假数据。正式发布请执行 npm run build:release'
  )
}
