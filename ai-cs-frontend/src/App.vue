<template>
  <div class="app-container" v-if="isLoggedIn">
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
          <el-menu-item index="/customer">
            <el-icon><User /></el-icon>
            <span>客户管理</span>
          </el-menu-item>
          <el-menu-item index="/workorder">
            <el-icon><Tickets /></el-icon>
            <span>工单管理</span>
          </el-menu-item>
          <el-menu-item index="/knowledge">
            <el-icon><Reading /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="top-header">
          <!-- 移动端汉堡菜单按钮 -->
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

        <!-- 移动端抽屉菜单 -->
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
            <el-menu-item index="/customer">
              <el-icon><User /></el-icon>
              <span>客户管理</span>
            </el-menu-item>
            <el-menu-item index="/workorder">
              <el-icon><Tickets /></el-icon>
              <span>工单管理</span>
            </el-menu-item>
            <el-menu-item index="/knowledge">
              <el-icon><Reading /></el-icon>
              <span>知识库管理</span>
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
import { Odometer, ChatDotRound, User, Tickets, Reading, Expand, Fold } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isLoggedIn = computed(() => {
  return route.path !== '/login' && !!localStorage.getItem('token')
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
.app-container {
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
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user-info {
  color: #606266;
  font-size: 14px;
}
.el-main {
  background-color: #f0f2f5;
}

/* 移动端汉堡菜单按钮（桌面端隐藏） */
.mobile-menu-btn {
  display: none !important;
}
.mobile-title {
  display: none;
}

/* ====== 移动端适配 ====== */
@media (max-width: 768px) {
  /* 隐藏桌面侧边栏 */
  .desktop-sidebar {
    display: none !important;
  }

  /* 显示移动端菜单按钮 */
  .mobile-menu-btn {
    display: inline-flex !important;
  }
  .mobile-title {
    display: inline;
    font-size: 16px;
    font-weight: bold;
    color: #2a3f5f;
  }

  /* 移动端头部 */
  .top-header {
    padding: 0 12px;
    height: 50px;
  }
  .user-info {
    display: none;
  }
  .header-right .el-button {
    font-size: 12px;
    padding: 5px 10px;
  }

  /* 主内容区域 */
  .el-main {
    padding: 12px;
  }

  /* 移动端抽屉样式 */
  .mobile-drawer .el-menu {
    border-right: none;
  }
}

/* 平板适配 */
@media (min-width: 769px) and (max-width: 1024px) {
  .desktop-sidebar {
    width: 180px !important;
  }
  .el-main {
    padding: 16px;
  }
}

/* 移动端触摸优化 */
@media (hover: none) and (pointer: coarse) {
  .el-menu-item {
    min-height: 48px;
    line-height: 48px;
  }
  .el-button {
    min-height: 40px;
  }
}
</style>
