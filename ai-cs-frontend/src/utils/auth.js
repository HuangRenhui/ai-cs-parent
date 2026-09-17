/**
 * 登录会话与角色：三档角色，入口隔离。
 *
 * - 普通用户（platform）：C 端客户，只进聊天与帮助中心
 * - 管理员（admin）    ：业务运营，进运营后台的业务模块
 * - 超级管理员（super）：平台管理，额外拥有账号权限类配置（用户/角色/菜单/配置/租户/技能组）
 *
 * 角色来源：登录返回的 roles 编码；种子账号 admin 直接视为超级管理员。
 */
export const LOGIN_TYPE_ADMIN = 'admin'
export const LOGIN_TYPE_PLATFORM = 'platform'

/** 角色档位标识 */
export const ROLE = {
  PLATFORM: 'platform',
  ADMIN: 'admin',
  SUPER: 'super'
}

/**
 * 管理端岗位角色编码，与后端 `cs_role.role_code` 对齐。
 *
 * 档位（ROLE）与角色码是两个层次，别混：
 * - **档位**管入口隔离与粗粒度准入（平台账号只能进聊天、超管专属页挡住管理员）
 * - **角色码**管岗位职责，即「侧栏看到哪些菜单、能进哪些路径」
 * 一个账号可挂多个角色码，权限取并集。
 */
export const ROLE_CODE = {
  AGENT: 'AGENT',
  SUPERVISOR: 'SUPERVISOR',
  KNOWLEDGE_OPS: 'KNOWLEDGE_OPS',
  ADMIN: 'ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN'
}

/** 岗位角色的展示名与职责，登录页演示账号与顶栏胶囊共用同一份 */
export const ROLE_CODE_INFO = {
  AGENT: { name: '一线坐席', desc: '只进工作台、会话与工单' },
  SUPERVISOR: { name: '客服主管', desc: '再加排队调度、全局会话、客户与报表' },
  KNOWLEDGE_OPS: { name: '知识运营', desc: '数据概览与知识库' },
  ADMIN: { name: '运营管理员', desc: '业务运营全部' },
  SUPER_ADMIN: { name: '超级管理员', desc: '全部 + 系统管理' }
}

/** 权限从高到低，用于多角色时挑一个展示 */
export const ROLE_CODE_ORDER = ['SUPER_ADMIN', 'ADMIN', 'SUPERVISOR', 'KNOWLEDGE_OPS', 'AGENT']

const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'
const TYPE_KEY = 'loginType'

/** 后端角色编码 → 超管档位（大小写不敏感） */
const SUPER_ROLE_CODES = ['SUPER_ADMIN', 'SUPERADMIN']
/** 种子超管账号，演示数据里没有角色行时兜底 */
const SEED_SUPER_USERNAME = 'admin'

/** 写入登录结果，并记下入口类型供菜单与退出跳转使用 */
export const saveSession = (data) => {
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data))
  localStorage.setItem(TYPE_KEY, data.loginType === LOGIN_TYPE_PLATFORM ? LOGIN_TYPE_PLATFORM : LOGIN_TYPE_ADMIN)
}

/** 退出时清掉令牌与入口标记 */
export const clearSession = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(TYPE_KEY)
}

export const getLoginType = () => localStorage.getItem(TYPE_KEY) || ''

export const getUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

/**
 * 当前账号的角色编码集合（大写）。
 * 一个账号可以挂多个角色，权限取并集；信息缺失时按运营管理员处理
 * （路由守卫会先拦未登录，这里只是兜底）。
 */
export const getRoleCodes = () => {
  const u = getUserInfo()
  // 种子超管账号兜底：演示数据里可能没有它的角色行
  if (String(u?.username || '').toLowerCase() === SEED_SUPER_USERNAME) return [ROLE_CODE.SUPER_ADMIN]
  const codes = (Array.isArray(u?.roles) ? u.roles : []).map((r) => String(r).toUpperCase())
  return codes.length ? codes : [ROLE_CODE.ADMIN]
}

/**
 * 当前角色档位。
 * 未登录或信息缺失时按管理员处理（路由守卫会先拦未登录）。
 */
export const getRole = () => {
  if (getLoginType() === LOGIN_TYPE_PLATFORM) return ROLE.PLATFORM
  if (getRoleCodes().some((r) => SUPER_ROLE_CODES.includes(r))) return ROLE.SUPER
  return ROLE.ADMIN
}

export const ROLE_TEXT = {
  [ROLE.PLATFORM]: '普通用户',
  [ROLE.ADMIN]: '管理员',
  [ROLE.SUPER]: '超级管理员'
}

/**
 * 顶栏角色胶囊文案：优先显示岗位角色名（坐席 / 客服主管 / 知识运营…），
 * 多角色取权限最高的那个，避免胶囊里挤一串。
 */
export const getRoleText = () => {
  const role = getRole()
  if (role === ROLE.PLATFORM) return ROLE_TEXT[ROLE.PLATFORM]
  const codes = getRoleCodes()
  const top = ROLE_CODE_ORDER.find((c) => codes.includes(c))
  return ROLE_CODE_INFO[top]?.name || ROLE_TEXT[role] || '未知角色'
}

export const isPlatformUser = () => getRole() === ROLE.PLATFORM

/** 是否最高超级管理员：账号权限类配置、审计删除、全量时间范围只给这类账号 */
export const isSuperAdmin = () => getRole() === ROLE.SUPER

/** 是否管理员会话（平台账号不得进运维/系统等后台） */
export const isAdminSession = () => getRole() !== ROLE.PLATFORM

/** 各岗位的落地页：坐席直接进工作台，知识运营进知识库 */
const ROLE_HOME = {
  AGENT: '/workspace',
  KNOWLEDGE_OPS: '/knowledge'
}

/**
 * 登录成功后的落地页。
 * 按岗位给首页：坐席落到工作台、知识运营落到知识库，主管与管理员落数据概览。
 * 否则低权限角色会被自己的首页权限挡一次，看着像登录失败。
 */
export const homeAfterLogin = (loginType) => {
  if (loginType === LOGIN_TYPE_PLATFORM) return '/chat'
  const codes = getRoleCodes()
  if (codes.some((c) => [ROLE_CODE.SUPER_ADMIN, ROLE_CODE.ADMIN, ROLE_CODE.SUPERVISOR].includes(c))) {
    return '/dashboard'
  }
  for (const c of ['KNOWLEDGE_OPS', 'AGENT']) {
    if (codes.includes(c)) return ROLE_HOME[c]
  }
  return '/dashboard'
}

/** 平台账号禁止进入的后台路径前缀（含会话/客户等运营页） */
export const ADMIN_ONLY_PREFIXES = [
  '/dashboard',
  '/report',
  '/onboarding',
  '/session',
  '/customer',
  '/workorder',
  '/knowledge',
  '/agent',
  '/queue',
  '/workspace',
  '/ai',
  '/open',
  '/data-retention',
  '/ops',
  '/system'
]

/** 仅普通用户使用的页面：管理员访问应回后台首页 */
export const PLATFORM_ONLY_PREFIXES = ['/chat']

/** 普通用户可访问的页面前缀（帮助中心是 C 端自助入口，普通用户也能直接进） */
export const PLATFORM_ALLOWED_PREFIXES = ['/chat', '/help-center']

/**
 * 超管专属路径：账号、权限与平台级配置。
 * 「系统管理」整组都是超管专属，管理员看不到这个分组（空分组会被自动隐藏）；
 * 审计与流量已移到运维，因此不在这个清单里。
 */
export const SUPER_ONLY_PREFIXES = [
  '/system/user',
  '/system/role',
  '/system/menu',
  '/system/config',
  '/system/tenant',
  '/system/skill-group'
]

const matchPrefix = (path, list) => list.some((p) => path === p || path.startsWith(p + '/'))

export const isAdminOnlyPath = (path) => matchPrefix(path, ADMIN_ONLY_PREFIXES)

/** 判断是否普通用户专属页（管理员访问应回数据概览） */
export const isPlatformOnlyPath = (path) => matchPrefix(path, PLATFORM_ONLY_PREFIXES)

/** 普通用户登录后允许访问的页面 */
export const isPlatformAllowedPath = (path) => matchPrefix(path, PLATFORM_ALLOWED_PREFIXES)

/** 是否超管专属页（管理员访问应被挡回） */
export const isSuperOnlyPath = (path) => matchPrefix(path, SUPER_ONLY_PREFIXES)

/**
 * 统一的可访问性判断，供路由守卫与菜单过滤共用。
 * @param {string} path 目标路径
 * @returns {{ok: boolean, redirect: string}} 不可访问时给出建议跳转
 */
export const checkAccess = (path) => {
  const role = getRole()
  if (role === ROLE.PLATFORM) {
    return isPlatformAllowedPath(path) ? { ok: true } : { ok: false, redirect: '/chat' }
  }
  if (isPlatformOnlyPath(path)) return { ok: false, redirect: '/dashboard' }
  if (role !== ROLE.SUPER && isSuperOnlyPath(path)) {
    return { ok: false, redirect: '/ops/audit' }
  }
  return { ok: true }
}
