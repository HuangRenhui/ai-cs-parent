import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '../views/LoginPage.vue'
import DashboardPage from '../views/DashboardPage.vue'
import ChatPage from '../views/ChatPage.vue'
import CustomerPage from '../views/CustomerPage.vue'
import WorkOrderPage from '../views/WorkOrderPage.vue'
import KnowledgePage from '../views/KnowledgePage.vue'
import SessionPage from '../views/SessionPage.vue'
import AgentPage from '../views/AgentPage.vue'
import AiModelPage from '../views/AiModelPage.vue'
import OpenPage from '../views/OpenPage.vue'
import WidgetPage from '../views/WidgetPage.vue'
import OpsLayout from '../views/ops/OpsLayout.vue'
import OpsOverviewPage from '../views/ops/OpsOverviewPage.vue'
import OpsLogsPage from '../views/ops/OpsLogsPage.vue'
import OpsTracesPage from '../views/ops/OpsTracesPage.vue'
import OpsAlertsPage from '../views/ops/OpsAlertsPage.vue'
import OpsHealthPage from '../views/ops/OpsHealthPage.vue'
import NotFoundPage from '../views/NotFoundPage.vue'

const routes = [
  { path: '/login', name: 'Login', component: LoginPage },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: DashboardPage, meta: { requiresAuth: true, title: '数据概览' } },
  { path: '/chat', name: 'Chat', component: ChatPage, meta: { requiresAuth: true, title: 'AI聊天' } },
  { path: '/session', name: 'Session', component: SessionPage, meta: { requiresAuth: true, title: '会话记录' } },
  { path: '/customer', name: 'Customer', component: CustomerPage, meta: { requiresAuth: true, title: '客户管理' } },
  { path: '/workorder', name: 'WorkOrder', component: WorkOrderPage, meta: { requiresAuth: true, title: '工单管理' } },
  { path: '/knowledge', name: 'Knowledge', component: KnowledgePage, meta: { requiresAuth: true, title: '知识库管理' } },
  { path: '/agent', name: 'Agent', component: AgentPage, meta: { requiresAuth: true, title: '坐席管理' } },
  { path: '/ai-model', name: 'AiModel', component: AiModelPage, meta: { requiresAuth: true, title: 'AI模型管理' } },
  { path: '/open', name: 'Open', component: OpenPage, meta: { requiresAuth: true, title: '开放接入' } },
  {
    path: '/ops',
    component: OpsLayout,
    meta: { requiresAuth: true, title: '运维' },
    children: [
      { path: '', name: 'OpsOverview', component: OpsOverviewPage, meta: { requiresAuth: true, title: '运维总览' } },
      { path: 'logs', name: 'OpsLogs', component: OpsLogsPage, meta: { requiresAuth: true, title: '日志查询' } },
      { path: 'traces', name: 'OpsTraces', component: OpsTracesPage, meta: { requiresAuth: true, title: '链路追踪' } },
      { path: 'alerts', name: 'OpsAlerts', component: OpsAlertsPage, meta: { requiresAuth: true, title: '告警' } },
      { path: 'health', name: 'OpsHealth', component: OpsHealthPage, meta: { requiresAuth: true, title: '服务健康' } }
    ]
  },
  { path: '/widget', name: 'Widget', component: WidgetPage, meta: { title: '在线客服', blank: true } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFoundPage, meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.blank) {
    next()
    return
  }
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
