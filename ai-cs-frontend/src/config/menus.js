/**
 * 侧栏菜单单一真源：桌面侧栏与移动抽屉共用同一份配置，按角色过滤。
 *
 * 之前菜单在 App.vue 里手写了两份，导致移动端少了运维的 4 个子页、
 * 开放接入也不成组。改为一处定义、两处渲染。
 */
import {
  Clock, Cpu, DataAnalysis, Document, Files, Guide, Headset, Link,
  Monitor, Odometer, QuestionFilled, Reading, Setting, Share, Tickets, Timer, User
} from '@element-plus/icons-vue'
import { ROLE_CODE } from '../utils/auth'

/** 图标名 → 组件，配置里只存名字，避免把整个图标库打进包 */
export const MENU_ICONS = {
  Clock, Cpu, DataAnalysis, Document, Files, Guide, Headset, Link,
  Monitor, Odometer, QuestionFilled, Reading, Setting, Share, Tickets, Timer, User
}

const { AGENT, SUPERVISOR, KNOWLEDGE_OPS, ADMIN, SUPER_ADMIN } = ROLE_CODE

// ===== 岗位角色组合 =====
// 一眼能看出「这一项谁能看到」，比每条写一长串好维护
/** 全部管理端岗位 */
const ALL_STAFF = [AGENT, SUPERVISOR, KNOWLEDGE_OPS, ADMIN, SUPER_ADMIN]
/** 坐席及以上：接待类页面（坐席自己也要用） */
const AGENT_UP = [AGENT, SUPERVISOR, ADMIN, SUPER_ADMIN]
/** 主管及以上：调度、全局会话、客户 */
const SUPERVISOR_UP = [SUPERVISOR, ADMIN, SUPER_ADMIN]
/** 知识运营及以上：知识库 */
const KNOWLEDGE_UP = [KNOWLEDGE_OPS, ADMIN, SUPER_ADMIN]
/** 看效果数据：主管与知识运营都要，坐席不看全局报表 */
const REPORT_UP = [SUPERVISOR, KNOWLEDGE_OPS, ADMIN, SUPER_ADMIN]
/** 运营管理员及以上：搭建、配置、运维 */
const ADMIN_UP = [ADMIN, SUPER_ADMIN]
/** 仅超管：账号与权限体系 */
const SUPER_ONLY = [SUPER_ADMIN]

/**
 * 菜单定义。
 * roles 缺省表示继承父级；子项 roles 用于「同一个分组下按权限拆分」的场景
 * （例：系统管理分组整组都是超管专属）。
 *
 * 排序依据**使用频率 × 使用角色**，按真实项目运营节奏分段：
 *   看板 → 上手 → 日常运营 → 持续运营 → 定期分析 → 搭建期 → 低频配置 → 平台
 *
 * 为什么不是「按数据流转顺序」排：
 * 配置与搭建类功能（接入向导 / 开放接入 / AI 能力 / 坐席管理 / 工单字段流程）
 * 只在项目上线前后或偶尔改配置时用，之后可能几个月不碰。若按流程排，它们会占据
 * 菜单腰部，把每天都要点的排队监控 / 工作台 / 会话 / 工单挤到下面——
 * 运营人员每天要滚动才能找到高频页，这是最该避免的。
 */
export const MENUS = [
  // ===== 看板：登录后的落地页，每天第一眼 =====
  { path: '/dashboard', label: '数据概览', icon: 'Odometer', roles: ALL_STAFF },
  // 上手：新人打开系统第二眼就该看到。它不参与日常业务流程、熟练后自然跳过，
  // 占一个前排位置成本很低；而放到底部，最需要它的新人恰恰最难找到。
  // 这是对「大厂把帮助放顶栏/角落」的有意偏离：本系统要交付给新接手的人，
  // 顶栏虽已有入口，侧栏再给一个固定位更稳妥
  { path: '/guide', label: '功能指南', icon: 'QuestionFilled', roles: ALL_STAFF },

  // ===== 日常运营：每天都要点，必须在前 7 位内不用滚动就能点到 =====
  // 段内顺序即一次服务的流转：谁在等 → 谁在接 → 聊了什么 → 是谁 → 要跟进什么
  // 坐席看得到工作台与会话工单；排队调度与客户档案是主管的活
  { path: '/queue', label: '排队监控', icon: 'Clock', roles: SUPERVISOR_UP },
  { path: '/workspace', label: '坐席工作台', icon: 'Monitor', roles: AGENT_UP },
  { path: '/session', label: '会话记录', icon: 'Tickets', roles: AGENT_UP },
  { path: '/customer', label: '客户管理', icon: 'User', roles: SUPERVISOR_UP },
  { path: '/workorder', label: '工单管理', icon: 'Document', roles: AGENT_UP },

  // ===== 持续运营：知识库不是一次性配置 =====
  // 知识运营是日常岗位：每天看未命中、补问答、调召回参数，所以留在运营段而不是配置段
  {
    path: '/knowledge',
    label: '知识库',
    icon: 'Reading',
    roles: KNOWLEDGE_UP,
    children: [
      { path: '/knowledge', label: '知识库管理' },
      { path: '/knowledge/document', label: '文档库' },
      { path: '/knowledge/graph', label: '知识图谱' },
      { path: '/knowledge/graph-manage', label: '图谱管理' },
      { path: '/knowledge/graph-plus', label: '图谱增强' },
      { path: '/knowledge/multimodal', label: '多模态' },
      { path: '/knowledge/multimodal-qa', label: '多模态问答' },
      { path: '/knowledge/media', label: '媒体资源' },
      { path: '/knowledge/file', label: '文件管理' },
      { path: '/knowledge/settings', label: '提示词与检索' }
    ]
  },

  // ===== 定期分析：周报月报与复盘用 =====
  // 「打开看一眼」已由数据概览覆盖；报表是深挖与汇报工具，不必占前排
  // 主管看服务质量，知识运营看答得准不准，坐席不看全局报表
  { path: '/report', label: '报表中心', icon: 'DataAnalysis', roles: REPORT_UP },

  // ===== 搭建期：项目上线前后才用，之后可能几个月不碰 =====
  // 先把系统接起来（接入向导 → 开放接入）→ 再配大脑（AI 能力）
  { path: '/onboarding', label: '接入向导', icon: 'Guide', roles: ADMIN_UP },
  {
    path: '/open',
    label: '开放接入',
    icon: 'Link',
    roles: ADMIN_UP,
    children: [
      { path: '/open', label: '行业包与工具' },
      { path: '/open/webhook', label: '回调通知' },
      { path: '/open/card', label: '卡片模板' },
      { path: '/open/security', label: '出站安全' },
      { path: '/open/prompt', label: '提示词包' },
      { path: '/open/openapi', label: '接口导入' },
      { path: '/open/widget-config', label: 'Widget 隐私与白标' }
    ]
  },
  {
    path: '/ai',
    label: 'AI 能力',
    icon: 'Cpu',
    roles: ADMIN_UP,
    children: [
      { path: '/ai/model', label: '模型管理' },
      { path: '/ai/intent', label: '意图配置' },
      { path: '/ai/slot', label: '填槽配置' },
      { path: '/ai/tools', label: 'AI 工具' },
      { path: '/ai/assist', label: '坐席辅助' }
    ]
  },

  // ===== 低频配置：偶尔改一次，不该占日常区的位置 =====
  // 注意「坐席管理」是加人 / 改状态，不是坐席每天干活的地方（那是坐席工作台），
  // 所以它属于配置段，与工作台分开是刻意的
  { path: '/agent', label: '坐席管理', icon: 'Headset', roles: ADMIN_UP },
  { path: '/workorder/field', label: '工单字段', icon: 'Files', roles: ADMIN_UP },
  { path: '/workorder/flow', label: '工单流程', icon: 'Share', roles: ADMIN_UP },
  { path: '/data-retention', label: '数据保留', icon: 'Timer', roles: ADMIN_UP },

  // ===== 平台：IT 与超管的领域，且有角色不可见的项，放最底 =====
  {
    path: '/ops',
    label: '运维',
    icon: 'Monitor',
    roles: ADMIN_UP,
    children: [
      { path: '/ops', label: '总览' },
      { path: '/ops/logs', label: '日志查询' },
      { path: '/ops/traces', label: '链路追踪' },
      // 操作审计与流量分析同属「看记录」的观测类，从系统管理移来
      { path: '/ops/audit', label: '操作审计' },
      { path: '/ops/traffic', label: '流量分析' },
      { path: '/ops/alerts', label: '告警' },
      { path: '/ops/health', label: '服务健康' }
    ]
  },
  {
    path: '/system',
    label: '系统管理',
    icon: 'Setting',
    roles: SUPER_ONLY,
    children: [
      // 账号与权限体系与平台配置：全组都是超管专属。
      // 因为没有子项对管理员开放，管理员看不到「系统管理」这个分组
      { path: '/system/user', label: '用户', roles: SUPER_ONLY },
      { path: '/system/role', label: '角色', roles: SUPER_ONLY },
      { path: '/system/menu', label: '菜单', roles: SUPER_ONLY },
      { path: '/system/config', label: '配置', roles: SUPER_ONLY },
      { path: '/system/tenant', label: '多租户', roles: SUPER_ONLY },
      { path: '/system/skill-group', label: '技能组', roles: SUPER_ONLY }
    ]
  }
]


const allows = (item, codes) => !item.roles || item.roles.some((r) => codes.includes(r))

/**
 * 按角色编码集合过滤出可见菜单（多角色取并集）。
 * 子项全部被过滤掉的分组会被整组隐藏，避免出现点不进去的空菜单。
 */
export const menusOfRoles = (codes = []) =>
  MENUS.filter((m) => allows(m, codes))
    .map((m) => {
      if (!m.children) return m
      const children = m.children.filter((c) => allows(c, codes))
      return { ...m, children }
    })
    .filter((m) => !m.children || m.children.length > 0)

/**
 * 路径 → 该项需要的角色。子项有独立 roles 时以子项为准，否则继承分组。
 * 供路由守卫复用菜单里的同一份权限配置——两侧共用一份定义，不会出现
 * 「菜单里看不到但手敲地址能进」的偏差。
 */
const PATH_ROLES = (() => {
  const map = {}
  const walk = (list, inherited) => list.forEach((m) => {
    const own = m.roles || inherited
    if (own) map[m.path] = own
    if (m.children) walk(m.children, own)
  })
  walk(MENUS, null)
  return map
})()

/**
 * 当前角色能否进某个后台路径。
 * 与原 checkAccess 的分工：checkAccess 管**入口档位**（平台账号 / 超管专属），
 * 这里管**岗位角色**，两者都在路由守卫里跑。
 * 未登记进菜单的路径（如 /guide 之外的散页）一律放行，避免误拦。
 */
export const canVisitPath = (path, codes = []) => {
  if (!codes.length || codes.includes(SUPER_ADMIN)) return true
  // 取最长匹配，保证 /workorder/field 优先于 /workorder
  const hit = Object.keys(PATH_ROLES)
    .filter((p) => path === p || path.startsWith(p + '/'))
    .sort((a, b) => b.length - a.length)[0]
  if (!hit) return true
  return PATH_ROLES[hit].some((r) => codes.includes(r))
}
