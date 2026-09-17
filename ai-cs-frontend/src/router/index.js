import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { checkAccess, getRoleCodes, homeAfterLogin, isAdminSession, isSuperAdmin, isSuperOnlyPath } from '../utils/auth'
import { canVisitPath } from '../config/menus'
import { openHelpCenter } from '../composables/useHelpCenter'
import UserLoginPage from '../views/UserLoginPage.vue'
import AdminLoginPage from '../views/AdminLoginPage.vue'
import DashboardPage from '../views/DashboardPage.vue'
import AdminGuidePage from '../views/AdminGuidePage.vue'
import GraphPlusPage from '../views/knowledge/GraphPlusPage.vue'
import MediaPage from '../views/knowledge/MediaPage.vue'
import FilePage from '../views/knowledge/FilePage.vue'
import MultimodalQaPage from '../views/knowledge/MultimodalQaPage.vue'
import GraphManagePage from '../views/knowledge/GraphManagePage.vue'
import AiAssistPage from '../views/AiAssistPage.vue'
import WorkOrderFlowPage from '../views/WorkOrderFlowPage.vue'
import ChatPage from '../views/ChatPage.vue'
import CustomerPage from '../views/CustomerPage.vue'
import WorkOrderPage from '../views/WorkOrderPage.vue'
import KnowledgeLayout from '../views/KnowledgeLayout.vue'
import KnowledgePage from '../views/KnowledgePage.vue'
import KnowledgeDocumentPage from '../views/KnowledgeDocumentPage.vue'
import SessionPage from '../views/SessionPage.vue'
import AgentPage from '../views/AgentPage.vue'
import AiLayout from '../views/ai/AiLayout.vue'
import AiModelPage from '../views/AiModelPage.vue'
import IntentConfigPage from '../views/IntentConfigPage.vue'
import SlotFillingPage from '../views/SlotFillingPage.vue'
import DataRetentionPage from '../views/DataRetentionPage.vue'
import OpenLayout from '../views/OpenLayout.vue'
import OpenPage from '../views/OpenPage.vue'
import OpenWebhookPage from '../views/OpenWebhookPage.vue'
import OpenCardPage from '../views/OpenCardPage.vue'
import OpenSecurityPage from '../views/OpenSecurityPage.vue'
import OpenPromptPage from '../views/OpenPromptPage.vue'
import OpenOpenApiPage from '../views/OpenOpenApiPage.vue'
import HelpCenterPage from '../views/HelpCenterPage.vue'
import WidgetPage from '../views/WidgetPage.vue'
import OpsLayout from '../views/ops/OpsLayout.vue'
import OpsOverviewPage from '../views/ops/OpsOverviewPage.vue'
import OpsLogsPage from '../views/ops/OpsLogsPage.vue'
import OpsTracesPage from '../views/ops/OpsTracesPage.vue'
import OpsAlertsPage from '../views/ops/OpsAlertsPage.vue'
import OpsHealthPage from '../views/ops/OpsHealthPage.vue'
import SystemLayout from '../views/system/SystemLayout.vue'
import UserPage from '../views/system/UserPage.vue'
import RolePage from '../views/system/RolePage.vue'
import MenuPage from '../views/system/MenuPage.vue'
import SysConfigPage from '../views/system/SysConfigPage.vue'
import OperationLogPage from '../views/system/OperationLogPage.vue'
import TrafficAnalysisPage from '../views/system/TrafficAnalysisPage.vue'
import AiToolsPage from '../views/ai-tools/AiToolsPage.vue'
import AiToolWorkspacePage from '../views/ai-tools/AiToolWorkspacePage.vue'
import WorkspacePage from '../views/WorkspacePage.vue'
import QueuePage from '../views/QueuePage.vue'
import WorkOrderFieldPage from '../views/WorkOrderFieldPage.vue'
import OpenWidgetConfigPage from '../views/OpenWidgetConfigPage.vue'
import TenantPage from '../views/system/TenantPage.vue'
import SkillGroupPage from '../views/system/SkillGroupPage.vue'
import ReportPage from '../views/ReportPage.vue'
import OnboardingPage from '../views/OnboardingPage.vue'
import KnowledgeGraphPage from '../views/KnowledgeGraphPage.vue'
import KnowledgeSettingsPage from '../views/KnowledgeSettingsPage.vue'
import MultimodalKnowledgePage from '../views/MultimodalKnowledgePage.vue'
import NotFoundPage from '../views/NotFoundPage.vue'

const routes = [
  // 两个登录入口是独立页面，不再有「选择入口」中转页
  { path: '/login', name: 'UserLogin', component: UserLoginPage, meta: { title: '普通用户登录' } },
  { path: '/login/admin', name: 'AdminLogin', component: AdminLoginPage, meta: { title: '运营后台登录' } },
  // 旧路径兼容：站内收藏与外链仍可用
  { path: '/login/platform', redirect: '/login' },
  { path: '/', redirect: '/login' },
  { path: '/dashboard', name: 'Dashboard', component: DashboardPage, meta: { requiresAuth: true, title: '数据概览' } },
  { path: '/chat', name: 'Chat', component: ChatPage, meta: { requiresAuth: true, platformOnly: true, title: '智能聊天' } },
  { path: '/session', name: 'Session', component: SessionPage, meta: { requiresAuth: true, title: '会话记录' } },
  { path: '/customer', name: 'Customer', component: CustomerPage, meta: { requiresAuth: true, title: '客户管理' } },
  { path: '/workorder', name: 'WorkOrder', component: WorkOrderPage, meta: { requiresAuth: true, title: '工单管理' } },
  { path: '/workorder/field', name: 'WorkOrderField', component: WorkOrderFieldPage, meta: { requiresAuth: true, title: '工单字段' } },
  { path: '/workorder/flow', name: 'WorkOrderFlow', component: WorkOrderFlowPage, meta: { requiresAuth: true, title: '工单流程' } },
  { path: '/workspace', name: 'Workspace', component: WorkspacePage, meta: { requiresAuth: true, title: '坐席工作台' } },
  { path: '/queue', name: 'Queue', component: QueuePage, meta: { requiresAuth: true, title: '排队监控' } },
  {
    path: '/knowledge',
    component: KnowledgeLayout,
    meta: { requiresAuth: true, title: '知识库' },
    children: [
      { path: '', name: 'Knowledge', component: KnowledgePage, meta: { requiresAuth: true, title: '知识库管理' } },
      { path: 'document', name: 'KnowledgeDocument', component: KnowledgeDocumentPage, meta: { requiresAuth: true, title: '文档知识库' } },
      { path: 'graph', name: 'KnowledgeGraph', component: KnowledgeGraphPage, meta: { requiresAuth: true, title: '知识图谱' } },
      { path: 'graph-plus', name: 'GraphPlus', component: GraphPlusPage, meta: { requiresAuth: true, title: '图谱增强' } },
      { path: 'multimodal', name: 'MultimodalKnowledge', component: MultimodalKnowledgePage, meta: { requiresAuth: true, title: '多模态知识' } },
      { path: 'media', name: 'MediaResource', component: MediaPage, meta: { requiresAuth: true, title: '媒体资源' } },
      { path: 'file', name: 'FileManage', component: FilePage, meta: { requiresAuth: true, title: '文件管理' } },
      { path: 'multimodal-qa', name: 'MultimodalQa', component: MultimodalQaPage, meta: { requiresAuth: true, title: '多模态问答' } },
      { path: 'graph-manage', name: 'GraphManage', component: GraphManagePage, meta: { requiresAuth: true, title: '图谱管理' } },
      { path: 'settings', name: 'KnowledgeSettings', component: KnowledgeSettingsPage, meta: { requiresAuth: true, title: '提示词与检索' } }
    ]
  },
  { path: '/guide', name: 'AdminGuide', component: AdminGuidePage, meta: { requiresAuth: true, title: '功能指南' } },
  { path: '/report', name: 'Report', component: ReportPage, meta: { requiresAuth: true, title: '报表中心' } },
  { path: '/onboarding', name: 'Onboarding', component: OnboardingPage, meta: { requiresAuth: true, title: '接入向导' } },
  { path: '/agent', name: 'Agent', component: AgentPage, meta: { requiresAuth: true, title: '坐席管理' } },
  {
    path: '/ai',
    component: AiLayout,
    meta: { requiresAuth: true, title: 'AI 能力' },
    children: [
      { path: '', redirect: '/ai/model' },
      { path: 'model', name: 'AiModel', component: AiModelPage, meta: { requiresAuth: true, title: '模型管理' } },
      { path: 'intent', name: 'IntentConfig', component: IntentConfigPage, meta: { requiresAuth: true, title: '意图配置' } },
      { path: 'slot', name: 'SlotConfig', component: SlotFillingPage, meta: { requiresAuth: true, title: '填槽配置' } },
      { path: 'tools', name: 'AiTools', component: AiToolsPage, meta: { requiresAuth: true, title: 'AI 工具' } },
      { path: 'assist', name: 'AiAssist', component: AiAssistPage, meta: { requiresAuth: true, title: '坐席辅助' } },
      { path: 'tools/:code', name: 'AiToolWorkspace', component: AiToolWorkspacePage, meta: { requiresAuth: true, title: 'AI 工具' } }
    ]
  },
  { path: '/data-retention', name: 'DataRetention', component: DataRetentionPage, meta: { requiresAuth: true, title: '数据保留' } },
  {
    path: '/open',
    component: OpenLayout,
    meta: { requiresAuth: true, title: '开放接入' },
    children: [
      { path: '', name: 'Open', component: OpenPage, meta: { requiresAuth: true, title: '行业包与工具' } },
      { path: 'webhook', name: 'OpenWebhook', component: OpenWebhookPage, meta: { requiresAuth: true, title: '回调通知' } },
      { path: 'card', name: 'OpenCard', component: OpenCardPage, meta: { requiresAuth: true, title: '卡片模板' } },
      { path: 'security', name: 'OpenSecurity', component: OpenSecurityPage, meta: { requiresAuth: true, title: '出站安全' } },
      { path: 'prompt', name: 'OpenPrompt', component: OpenPromptPage, meta: { requiresAuth: true, title: '提示词包' } },
      { path: 'openapi', name: 'OpenOpenApi', component: OpenOpenApiPage, meta: { requiresAuth: true, title: '接口导入' } },
      { path: 'widget-config', name: 'OpenWidgetConfig', component: OpenWidgetConfigPage, meta: { requiresAuth: true, title: 'Widget 隐私与白标' } }
    ]
  },
  {
    path: '/ops',
    component: OpsLayout,
    meta: { requiresAuth: true, title: '运维' },
    children: [
      { path: '', name: 'OpsOverview', component: OpsOverviewPage, meta: { requiresAuth: true, title: '运维总览' } },
      { path: 'logs', name: 'OpsLogs', component: OpsLogsPage, meta: { requiresAuth: true, title: '日志查询' } },
      { path: 'traces', name: 'OpsTraces', component: OpsTracesPage, meta: { requiresAuth: true, title: '链路追踪' } },
      // 操作审计与流量分析从 /system 移来：本质也是「看记录」，与日志/链路同类
      { path: 'audit', name: 'OpsAudit', component: OperationLogPage, meta: { requiresAuth: true, title: '操作审计' } },
      { path: 'traffic', name: 'OpsTraffic', component: TrafficAnalysisPage, meta: { requiresAuth: true, title: '流量分析' } },
      { path: 'alerts', name: 'OpsAlerts', component: OpsAlertsPage, meta: { requiresAuth: true, title: '告警' } },
      { path: 'health', name: 'OpsHealth', component: OpsHealthPage, meta: { requiresAuth: true, title: '服务健康' } }
    ]
  },
  {
    path: '/system',
    component: SystemLayout,
    meta: { requiresAuth: true, title: '系统管理' },
    children: [
      // 本组现已全是超管专属；管理员进来会被守卫挡回落，给运维首页
      { path: '', redirect: () => (isSuperAdmin() ? '/system/user' : '/ops') },
      { path: 'user', name: 'SystemUser', component: UserPage, meta: { requiresAuth: true, title: '用户管理' } },
      { path: 'role', name: 'SystemRole', component: RolePage, meta: { requiresAuth: true, title: '角色管理' } },
      { path: 'menu', name: 'SystemMenu', component: MenuPage, meta: { requiresAuth: true, title: '菜单管理' } },
      { path: 'config', name: 'SystemConfig', component: SysConfigPage, meta: { requiresAuth: true, title: '系统配置' } },
      { path: 'tenant', name: 'SystemTenant', component: TenantPage, meta: { requiresAuth: true, title: '多租户管理' } },
      { path: 'skill-group', name: 'SystemSkillGroup', component: SkillGroupPage, meta: { requiresAuth: true, title: '技能组与数据权限' } }
    ]
  },
  // 旧路径兼容：操作审计与流量分析已归到运维分组，保留重定向避免收藏失效
  { path: '/system/log', redirect: '/ops/audit' },
  { path: '/system/traffic', redirect: '/ops/traffic' },
  // 旧路径兼容：AI 能力已收进 /ai 分组，保留重定向避免收藏与外链失效
  { path: '/ai-model', redirect: '/ai/model' },
  { path: '/intent-config', redirect: '/ai/intent' },
  { path: '/slot-config', redirect: '/ai/slot' },
  { path: '/ai-tools', redirect: '/ai/tools' },
  { path: '/ai-tools/:code', redirect: (to) => `/ai/tools/${to.params.code}` },
  { path: '/help-center', name: 'HelpCenter', component: HelpCenterPage, meta: { title: '帮助中心', blank: true } },
  { path: '/widget', name: 'Widget', component: WidgetPage, meta: { title: '在线客服', blank: true } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFoundPage, meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  // 应用内点帮助中心：当前页弹窗，不跳走、不新开标签（直接打开 /help-center 仍可看独立页）
  if (to.path === '/help-center' && from.matched.length) {
    openHelpCenter()
    next(false)
    return
  }
  const token = localStorage.getItem('token')
  if (to.meta.blank) {
    next()
    return
  }
  const isLoginPath = to.path === '/login' || to.path.startsWith('/login/')
  // 已登录访问首页或任一登录页，按入口类型进工作台 / 运营后台
  if (token && (to.path === '/' || isLoginPath)) {
    next(homeAfterLogin(isAdminSession() ? 'admin' : 'platform'))
    return
  }
  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }
  // 三档角色统一判权：普通用户只看聊天与帮助中心，超管专属页挡住管理员
  if (token && to.meta.requiresAuth) {
    const { ok, redirect } = checkAccess(to.path)
    if (!ok) {
      // 管理员误入超管专属页时给个明确原因，避免看起来像点击没反应
      if (isSuperOnlyPath(to.path)) ElMessage.warning('该功能仅超级管理员可用')
      next(redirect)
      return
    }
    // 岗位角色：菜单里看不到的路径同样不放行。
    // 与侧栏共用 config/menus.js 里那份角色矩阵，不会出现「菜单藏了但手敲地址能进」
    if (!canVisitPath(to.path, getRoleCodes())) {
      ElMessage.warning('当前岗位没有该功能的权限')
      next(homeAfterLogin('admin'))
      return
    }
  }
  next()
})

/** 浏览器标题跟当前页中文名走，避免一直显示英文站点名 */
router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · 智能客服` : '智能客服'
})

export default router
