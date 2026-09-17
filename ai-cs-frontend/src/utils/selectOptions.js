/**
 * 后台表单里反复出现的枚举选项。能从接口拉的（行业包、会话、客户）不放这里。
 */

/** 演示/单租户默认编码，下拉可选，避免手填 */
export const TENANT_OPTIONS = [
  { label: '默认租户', value: 'default' }
]

/** 客户常用标签，表单允许再新建 */
export const CUSTOMER_TAG_OPTIONS = ['贵宾', '普通', '售后']

/** 系统配置类型 */
export const CONFIG_TYPE_OPTIONS = [
  { label: '系统', value: 'system' },
  { label: '业务', value: 'business' }
]

/** 操作审计模块筛选项：和后台 URI 第一/二段对齐 */
export const LOG_MODULE_OPTIONS = [
  { label: '登录', value: 'auth' },
  { label: '用户', value: 'user' },
  { label: '角色', value: 'role' },
  { label: '菜单', value: 'menu' },
  { label: '配置', value: 'config' },
  { label: '客户', value: 'customer' },
  { label: '工单', value: 'workorder' },
  { label: '会话', value: 'session' },
  { label: '知识库', value: 'knowledge' },
  { label: '坐席', value: 'agent' },
  { label: '开放接入', value: 'open' },
  { label: '模型', value: 'model' },
  { label: '意图', value: 'intent' },
  { label: '填槽', value: 'slot' },
  { label: '数据保留', value: 'retention' },
  { label: '运维', value: 'ops' }
]

/** Widget / 场景配置常用编码 */
export const SCENE_CODE_OPTIONS = [
  { label: '订单入口', value: 'ORDER' },
  { label: '产品入口', value: 'PRODUCT' },
  { label: '售后入口', value: 'AFTER_SALE' }
]

/** 提示词场景，可再新建 */
export const PROMPT_SCENE_OPTIONS = [
  { label: '订单', value: 'order' },
  { label: '退款', value: 'refund' },
  { label: '售后', value: 'after_sale' }
]

/** 开放工具请求方法（协议词保留原文） */
export const HTTP_METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

/** 日志级别：存英文值，界面显示中文 */
export const LOG_LEVEL_OPTIONS = [
  { label: '信息', value: 'INFO' },
  { label: '警告', value: 'WARN' },
  { label: '错误', value: 'ERROR' }
]

/** 把存储值翻成中文，没有映射则原样返回 */
export const displayText = (map, value, fallback = '—') => {
  if (value == null || value === '') return fallback
  return map[value] ?? map[String(value)] ?? value
}

export const LOG_LEVEL_TEXT = { INFO: '信息', WARN: '警告', ERROR: '错误' }
export const AUTH_TYPE_TEXT = { none: '无', token: '令牌', signature: '签名' }
export const IMPORT_STATUS_TEXT = { success: '成功', failed: '失败', pending: '待处理' }
export const CONNECTOR_TYPE_TEXT = { MOCK: '演示', REST: '接口' }
export const RISK_TEXT = { read: '只读', write: '写入', critical: '高风险', low: '低' }
export const HEALTH_UP_TEXT = { UP: '正常', DOWN: '异常' }
export const ALERT_STATUS_TEXT = { open: '未恢复', closed: '已恢复', resolved: '已恢复' }
export const FIELD_TYPE_TEXT = { string: '文本', number: '数字', date: '日期', json: '对象' }
export const CARD_TYPE_TEXT = { entity: '实体卡', button: '按钮', form: '表单' }
export const CONFIG_TYPE_TEXT = { system: '系统', business: '业务' }

/** 选项数组转成 displayText 用的映射 */
const textFromOptions = (opts) => Object.fromEntries(opts.map((o) => [o.value, o.label]))

export const TENANT_TEXT = textFromOptions(TENANT_OPTIONS)
export const LOG_MODULE_TEXT = textFromOptions(LOG_MODULE_OPTIONS)
export const SCENE_CODE_TEXT = textFromOptions(SCENE_CODE_OPTIONS)
export const PROMPT_SCENE_TEXT = textFromOptions(PROMPT_SCENE_OPTIONS)

/** 行业包编码：存英文值，列表显示中文名 */
export const PACK_CODE_TEXT = { ecommerce: '电商', finance: '金融' }
/** 提示词类型 */
export const PROMPT_TYPE_TEXT = { persona: '人设', rejection: '拒答', slot: '槽位', system: '系统' }
/** 运维服务名：进程名仍是英文，界面显示中文 */
export const SERVICE_NAME_TEXT = {
  gateway: '网关',
  'ai-cs-agent': '坐席服务',
  'ai-agent': '坐席服务',
  'ai-cs-open': '开放服务',
  job: '定时任务'
}
/** 链路步骤名 */
export const SPAN_NAME_TEXT = {
  receive: '接入',
  auth: '鉴权',
  http: '转发',
  session: '会话',
  prompt: '提示词',
  intent: '意图',
  slot: '填槽',
  retrieve: '检索',
  tool: '工具',
  retry: '重试',
  chat: '生成',
  stream: '流式写出',
  persist: '落库',
  respond: '回写',
  callback: '回调'
}
/** 日志适配器 */
export const ADAPTER_TEXT = { file: '文件', mock: '演示' }
/** 告警通道 */
export const CHANNEL_TEXT = { log: '日志', mail: '邮件', sms: '短信' }
/** 回调事件类型 */
export const WEBHOOK_EVENT_TEXT = {
  logistics_update: '物流更新',
  refund_result: '退款结果',
  workorder_progress: '工单进展',
  session_start: '会话开始',
  transfer_agent: '转人工',
  session_end: '会话结束',
  rating: '评价'
}
