/**
 * 小程序全局配置
 * 微信开发者工具需勾选「详情 → 本地设置 → 不校验合法域名」；
 * 真机调试请把 BASE_URL 换成本机局域网 IP，上线换已备案 https 域名。
 */
module.exports = {
  BASE_URL: 'http://localhost:8080',
  /** true = 走本地假数据（演示）；false = 调真实网关 */
  USE_MOCK: true,
  /** 默认租户 */
  TENANT_CODE: 'default'
}
