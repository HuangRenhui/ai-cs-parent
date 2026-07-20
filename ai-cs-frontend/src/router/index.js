import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '../views/LoginPage.vue'
import DashboardPage from '../views/DashboardPage.vue'
import ChatPage from '../views/ChatPage.vue'
import CustomerPage from '../views/CustomerPage.vue'
import WorkOrderPage from '../views/WorkOrderPage.vue'
import KnowledgePage from '../views/KnowledgePage.vue'

const routes = [
  { path: '/login', name: 'Login', component: LoginPage },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: DashboardPage, meta: { requiresAuth: true } },
  { path: '/chat', name: 'Chat', component: ChatPage, meta: { requiresAuth: true } },
  { path: '/customer', name: 'Customer', component: CustomerPage, meta: { requiresAuth: true } },
  { path: '/workorder', name: 'WorkOrder', component: WorkOrderPage, meta: { requiresAuth: true } },
  { path: '/knowledge', name: 'Knowledge', component: KnowledgePage, meta: { requiresAuth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳转到登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
