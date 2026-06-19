import { createRouter, createWebHistory } from 'vue-router'
import ChatPage from '../views/ChatPage.vue'
import CustomerPage from '../views/CustomerPage.vue'
import WorkOrderPage from '../views/WorkOrderPage.vue'
import KnowledgePage from '../views/KnowledgePage.vue'

const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/chat', name: 'Chat', component: ChatPage },
  { path: '/customer', name: 'Customer', component: CustomerPage },
  { path: '/workorder', name: 'WorkOrder', component: WorkOrderPage },
  { path: '/knowledge', name: 'Knowledge', component: KnowledgePage }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router