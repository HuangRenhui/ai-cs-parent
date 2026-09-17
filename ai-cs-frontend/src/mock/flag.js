/**
 * 前端演示开关：默认走内存假数据，不请求后端。
 * 联通真接口时：localStorage.setItem('aiCsUseRealApi','1') 后刷新，
 * 或启动前设置环境变量 VITE_USE_REAL_API=true。
 */
export const isMockEnabled = () => {
  if (typeof localStorage !== 'undefined' && localStorage.getItem('aiCsUseRealApi') === '1') {
    return false
  }
  return import.meta.env.VITE_USE_REAL_API !== 'true'
}
