<template>
  <div v-if="isBlank" class="blank-shell">
    <router-view />
  </div>
  <div class="app-container" v-else-if="isLoggedIn">
    <el-container>
      <!-- 桌面端侧边栏 -->
      <el-aside width="220px" class="sidebar desktop-sidebar">
        <div class="logo" @click="$router.push('/dashboard')">AI 智能客服</div>
        <el-menu :default-active="$route.path" mode="vertical" router background-color="#2a3f5f"
          text-color="#bfcbd9" active-text-color="#409eff">
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>数据概览</span>
          </el-menu-item>
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>AI聊天</span>
          </el-menu-item>
          <el-menu-item index="/session">
            <el-icon><Tickets /></el-icon>
            <span>会话记录</span>
          </el-menu-item>
          <el-menu-item index="/customer">
            <el-icon><User /></el-icon>
            <span>客户管理</span>
          </el-menu-item>
          <el-menu-item index="/workorder">
            <el-icon><Document /></el-icon>
            <span>工单管理</span>
          </el-menu-item>
          <el-menu-item index="/knowledge">
            <el-icon><Reading /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
          <el-menu-item index="/agent">
            <el-icon><Headset /></el-icon>
            <span>坐席管理</span>
          </el-menu-item>
          <el-menu-item index="/open">
            <el-icon><Link /></el-icon>
            <span>开放接入</span>
          </el-menu-item>
          <el-menu-item index="/ai-model">
            <el-icon><Cpu /></el-icon>
            <span>AI模型管理</span>
          </el-menu-item>
          <el-menu-item index="/intent-config">
            <el-icon><Aim /></el-icon>
            <span>意图配置</span>
          </el-menu-item>
          <el-menu-item index="/slot-config">
            <el-icon><Grid /></el-icon>
            <span>填槽配置</span>
          </el-menu-item>
          <el-menu-item index="/data-retention">
            <el-icon><Timer /></el-icon>
            <span>数据保留</span>
          </el-menu-item>
          <el-sub-menu index="/ops">
            <template #title>
              <el-icon><Monitor /></el-icon>
              <span>运维</span>
            </template>
            <el-menu-item index="/ops">总览</el-menu-item>
            <el-menu-item index="/ops/logs">日志查询</el-menu-item>
            <el-menu-item index="/ops/traces">链路追踪</el-menu-item>
            <el-menu-item index="/ops/alerts">告警</el-menu-item>
            <el-menu-item index="/ops/health">服务健康</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="top-header">
          <div class="header-left">
            <el-button class="mobile-menu-btn" @click="mobileMenuVisible = !mobileMenuVisible" text>
              <el-icon :size="22"><Expand v-if="!mobileMenuVisible" /><Fold v-else /></el-icon>
            </el-button>
            <span class="mobile-title">AI 智能客服</span>
          </div>
          <div class="header-right">
            <span class="user-info">{{ userInfo?.realName || userInfo?.username || '管理员' }}</span>
            <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
          </div>
        </el-header>

        <el-drawer v-model="mobileMenuVisible" direction="ltr" size="220px" :with-header="false" class="mobile-drawer">
          <div class="logo" @click="mobileMenuVisible = false; $router.push('/dashboard')">AI 智能客服</div>
          <el-menu :default-active="$route.path" mode="vertical" router background-color="#2a3f5f"
            text-color="#bfcbd9" active-text-color="#409eff" @select="mobileMenuVisible = false">
            <el-menu-item index="/dashboard">
              <el-icon><Odometer /></el-icon>
              <span>数据概览</span>
            </el-menu-item>
            <el-menu-item index="/chat">
              <el-icon><ChatDotRound /></el-icon>
              <span>AI聊天</span>
            </el-menu-item>
            <el-menu-item index="/session">
              <el-icon><Tickets /></el-icon>
              <span>会话记录</span>
            </el-menu-item>
            <el-menu-item index="/customer">
              <el-icon><User /></el-icon>
              <span>客户管理</span>
            </el-menu-item>
            <el-menu-item index="/workorder">
              <el-icon><Document /></el-icon>
              <span>工单管理</span>
            </el-menu-item>
            <el-menu-item index="/knowledge">
              <el-icon><Reading /></el-icon>
              <span>知识库管理</span>
            </el-menu-item>
            <el-menu-item index="/agent">
              <el-icon><Headset /></el-icon>
              <span>坐席管理</span>
            </el-menu-item>
            <el-menu-item index="/open">
              <el-icon><Link /></el-icon>
              <span>开放接入</span>
            </el-menu-item>
            <el-menu-item index="/ai-model">
              <el-icon><Cpu /></el-icon>
              <span>AI模型管理</span>
            </el-menu-item>
            <el-menu-item index="/intent-config">
              <el-icon><Aim /></el-icon>
              <span>意图配置</span>
            </el-menu-item>
            <el-menu-item index="/slot-config">
              <el-icon><Grid /></el-icon>
              <span>填槽配置</span>
            </el-menu-item>
            <el-menu-item index="/data-retention">
              <el-icon><Timer /></el-icon>
              <span>数据保留</span>
            </el-menu-item>
            <el-menu-item index="/ops">
              <el-icon><Monitor /></el-icon>
              <span>运维总览</span>
            </el-menu-item>
          </el-menu>
        </el-drawer>

        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
  <router-view v-else />
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Odometer, ChatDotRound, User, Tickets, Reading, Expand, Fold, Document, Headset, Link, Monitor, Cpu, Aim, Grid, Timer } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isBlank = computed(() => !!route.meta.blank)
const isLoggedIn = computed(() => {
  return !isBlank.value && route.path !== '/login' && !!localStorage.getItem('token')
})

const userInfo = ref(null)
const mobileMenuVisible = ref(false)

onMounted(() => {
  try {
    const info = localStorage.getItem('userInfo')
    if (info) {
      userInfo.value = JSON.parse(info)
    }
  } catch (e) {
    // ignore
  }
})

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  router.push('/login')
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
html, body {
  height: 100%;
  -webkit-overflow-scrolling: touch;
}
.app-container, .blank-shell {
  height: 100vh;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background-color: #1f2d4a;
  cursor: pointer;
}
.sidebar {
  background-color: #2a3f5f;
  overflow-y: auto;
}
.sidebar .el-menu {
  border-right: none;
}
.top-header {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  padding-top: var(--safe-top);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.user-info {
  color: #606266;
  font-size: 14px;
}
.el-main {
  background-color: #f0f2f5;
}
.mobile-menu-btn {
  display: none !important;
}
.mobile-title {
  display: none;
}

/* 手机：抽屉菜单 + 精简顶栏 */
@media (max-width: 640px) {
  .desktop-sidebar {
    display: none !important;
  }
  .mobile-menu-btn {
    display: inline-flex !important;
  }
  .mobile-title {
    display: inline;
    font-size: 16px;
    font-weight: bold;
    color: #2a3f5f;
  }
  .top-header {
    padding: 0 12px;
    padding-top: var(--safe-top);
    height: calc(50px + var(--safe-top));
  }
  .user-info {
    display: none;
  }
  .header-right .el-button {
    font-size: 12px;
    padding: 5px 10px;
  }
  .el-main {
    padding: 12px;
  }
  .chat-container {
    border-radius: 10px;
  }
  .mobile-drawer .el-menu {
    border-right: none;
  }
}

/* 平板：窄侧栏 */
@media (min-width: 641px) and (max-width: 1024px) {
  .desktop-sidebar {
    width: 180px !important;
  }
  .el-main {
    padding: 16px;
  }
}
</style>
