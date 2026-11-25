/**
 * 小程序全局配置
 * 微信开发者工具需勾选「详情 → 本地设置 → 不校验合法域名」；
 * 真机调试请把 BASE_URL 换成本机局域网 IP，上线换已备案 https 域名。
 *
 * ENV 取值：
 *   mock  本地假数据（演示，默认）
 *   dev   连本机网关
 *   prod  连线上域名
 * 切换环境只改 ENV，避免出现「以为连的是线上、实际还在发假数据」。
 */
const ENV = 'mock'

const BASE_URLS = {
  mock: 'http://localhost:8080',
  dev: 'http://localhost:8080',
  prod: 'https://your-domain.example.com'
}

module.exports = {
  ENV,
  BASE_URL: BASE_URLS[ENV] || BASE_URLS.dev,
  /** 只有 mock 环境才走本地假数据 */
  USE_MOCK: ENV === 'mock',
  /** 网络超时（毫秒） */
  TIMEOUT_MS: 15000,
  /** 默认租户 */
  TENANT_CODE: 'default'
}
