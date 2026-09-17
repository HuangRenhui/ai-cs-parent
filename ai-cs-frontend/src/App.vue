<template>
  <el-config-provider :locale="zhCn">
  <div v-if="isBlank" class="blank-shell">
    <router-view />
  </div>
  <div class="app-container" :class="{ 'user-shell': !isAdmin }" v-else-if="isLoggedIn">
    <el-container>
      <!-- 普通用户只聊天，不展示运营侧栏 -->
      <el-aside v-if="isAdmin" width="220px" class="sidebar desktop-sidebar">
        <div class="logo" @click="$router.push(isAdmin ? '/dashboard' : '/chat')">
          <span class="logo-mark">客</span>
          <span class="logo-text">智能客服</span>
        </div>
          <!-- 菜单由 config/menus.js 按角色生成，与移动端抽屉共用同一份定义 -->
          <el-menu :default-active="menuActive" mode="vertical" router background-color="#1c2b4a"
          text-color="#c5d0e0" active-text-color="#8eb4ff">
          <template v-for="m in menus" :key="m.path">
            <el-sub-menu v-if="m.children" :index="m.path">
              <template #title>
                <el-icon><component :is="MENU_ICONS[m.icon]" /></el-icon>
                <span>{{ m.label }}</span>
              </template>
              <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">{{ c.label }}</el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="m.path">
              <el-icon><component :is="MENU_ICONS[m.icon]" /></el-icon>
              <span>{{ m.label }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="top-header">
          <div class="header-left">
            <el-button v-if="isAdmin" class="mobile-menu-btn" @click="mobileMenuVisible = !mobileMenuVisible" text>
              <el-icon :size="22"><Expand v-if="!mobileMenuVisible" /><Fold v-else /></el-icon>
            </el-button>
            <span v-if="isAdmin" class="mobile-title">智能客服</span>
            <!-- 普通用户没有侧栏，用吉祥物和问候填满顶栏左侧空白 -->
            <div v-if="!isAdmin" class="user-brand">
              <span class="user-mascot" aria-hidden="true"></span>
              <div class="user-brand-copy">
                <strong>智能小客</strong>
                <span>{{ userGreeting }}</span>
              </div>
              <span class="hdr-sparkle s1" aria-hidden="true">✦</span>
              <span class="hdr-sparkle s2" aria-hidden="true">✧</span>
            </div>
          </div>
          <div class="header-right">
            <span v-if="demoMode" class="hd-pill demo"><i class="hd-dot"></i>演示数据</span>
            <!-- 帮助中心是 C 端自助入口：只给普通用户 -->
            <el-button v-if="!isAdmin" class="hd-help" text @click="openHelpCenter">
              <el-icon><Reading /></el-icon>
              帮助中心
            </el-button>
            <!-- 管理端对应的是「功能指南」：讲每个模块怎么用，两套内容不共用 -->
            <el-button v-if="isAdmin" class="hd-help" text @click="$router.push('/guide')">
              <el-icon><QuestionFilled /></el-icon>
              功能指南
            </el-button>
            <span class="hd-pill" :class="'role-' + roleKey">{{ roleText }}</span>
            <div class="hd-user">
              <span class="hd-avatar">{{ userInitial }}</span>
              <span class="hd-name">{{ userInfo?.realName || userInfo?.username || '用户' }}</span>
            </div>
            <button type="button" class="hd-logout" @click="handleLogout">退出</button>
          </div>
        </el-header>

        <el-drawer v-if="isAdmin" v-model="mobileMenuVisible" direction="ltr" size="220px" :with-header="false" class="mobile-drawer">
          <div class="logo" @click="mobileMenuVisible = false; $router.push(isAdmin ? '/dashboard' : '/chat')">
            <span class="logo-mark">客</span>
            <span class="logo-text">智能客服</span>
          </div>
          <el-menu :default-active="menuActive" mode="vertical" router background-color="#1c2b4a"
            text-color="#c5d0e0" active-text-color="#8eb4ff" @select="mobileMenuVisible = false">
            <template v-for="m in menus" :key="m.path">
              <el-sub-menu v-if="m.children" :index="m.path">
                <template #title>
                  <el-icon><component :is="MENU_ICONS[m.icon]" /></el-icon>
                  <span>{{ m.label }}</span>
                </template>
                <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">{{ c.label }}</el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else :index="m.path">
                <el-icon><component :is="MENU_ICONS[m.icon]" /></el-icon>
                <span>{{ m.label }}</span>
              </el-menu-item>
            </template>
          </el-menu>
        </el-drawer>

        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
  <router-view v-else />
  <!-- 表格长文本点击后的全文弹窗，全站共用 -->
  <TextPeekDialog />
  <!-- 帮助中心弹窗，全站共用，不新开标签页 -->
  <HelpCenterDialog />
  </el-config-provider>
</template>

<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { Expand, Fold, QuestionFilled, Reading } from '@element-plus/icons-vue'
import { logout } from './api'
import { isMockEnabled } from './mock'
import { ROLE, clearSession, getRole, getRoleCodes, getRoleText } from './utils/auth'
import { MENU_ICONS, menusOfRoles } from './config/menus'
import { openHelpCenter } from './composables/useHelpCenter'
import TextPeekDialog from './components/TextPeekDialog.vue'
import HelpCenterDialog from './components/HelpCenterDialog.vue'

const router = useRouter()
const route = useRoute()
const demoMode = isMockEnabled()

const isBlank = computed(() => !!route.meta.blank)
const isLoginRoute = computed(() => route.path === '/login' || route.path.startsWith('/login/'))
const isLoggedIn = computed(() => {
  return !isBlank.value && !isLoginRoute.value && route.path !== '/' && !!localStorage.getItem('token')
})
const userInfo = ref(null)
/** 当前角色档位：登录 / 退出 / 换账号后由 loadUserInfo 重新求值 */
const roleKey = ref(ROLE.ADMIN)
/** 岗位角色编码集合：菜单按它过滤，一个账号可挂多个角色，权限取并集 */
const roleCodes = ref([])
/** 顶栏胶囊：显示岗位名（坐席 / 客服主管 / 知识运营 / 运营管理员 / 超级管理员） */
const roleText = ref('')
/** 侧栏菜单按岗位角色过滤，桌面与移动端共用这份结果 */
const menus = computed(() => menusOfRoles(roleCodes.value))
const isAdmin = computed(() => roleKey.value !== ROLE.PLATFORM)
/** 工具工作台路径仍高亮侧栏「AI 工具」 */
const menuActive = computed(() => {
  const p = route.path
  // 工具工作台是 /ai/tools/:code，高亮回工具箱菜单项
  if (p.startsWith('/ai/tools')) return '/ai/tools'
  return p
})
const mobileMenuVisible = ref(false)
/** 顶栏头像取姓名首字，没有姓名再用账号 */
const userInitial = computed(() => {
  const n = userInfo.value?.realName || userInfo.value?.username || '用'
  return String(n).slice(0, 1)
})

/** 按时刻换一句软一点的招呼，让顶栏左侧不空 */
const userGreeting = computed(() => {
  const name = userInfo.value?.realName || userInfo.value?.username || '你好'
  const h = new Date().getHours()
  if (h < 11) return `${name}，早上好呀，我一直在～`
  if (h < 14) return `${name}，中午好，慢慢聊就好`
  if (h < 18) return `${name}，下午好，有事随时叫我`
  return `${name}，晚上好，今晚也陪着你`
})

const loadUserInfo = () => {
  try {
    const info = localStorage.getItem('userInfo')
    userInfo.value = info ? JSON.parse(info) : null
  } catch {
    userInfo.value = null
  }
  // 角色与登录信息同源刷新，避免换账号后菜单还停在上一个人的权限
  roleKey.value = getRole()
  roleCodes.value = getRoleCodes()
  roleText.value = getRoleText()
}

watch([isLoggedIn, () => route.path], loadUserInfo, { immediate: true })
onMounted(loadUserInfo)

/** 先调后端注销（失败也不挡本地清 token），再回登录页 */
const handleLogout = async () => {
  try {
    await logout()
  } catch {
    /* 令牌已失效时仍清本地会话 */
  }
  clearSession()
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
.app-container > .el-container {
  height: 100%;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  background: #152238;
  cursor: pointer;
  letter-spacing: 0.2px;
}
.logo-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #2f6bff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
}
.logo-text {
  color: #fff;
}
.sidebar {
  background-color: #1c2b4a;
  overflow-y: auto;
}
/* 侧栏菜单做成圆角条目，避免一整列硬切的选中态 */
.sidebar .el-menu {
  border-right: none;
  padding: 6px 0 16px;
}
.sidebar .el-menu-item,
.sidebar .el-sub-menu__title {
  height: 42px;
  line-height: 42px;
  margin: 2px 10px;
  width: calc(100% - 20px);
  border-radius: 8px;
}
.sidebar .el-menu-item:hover,
.sidebar .el-sub-menu__title:hover {
  background: rgba(255, 255, 255, 0.08) !important;
}
.sidebar .el-menu-item.is-active {
  background: rgba(47, 107, 255, 0.28) !important;
  color: #fff !important;
}
.sidebar .el-sub-menu .el-menu-item {
  min-width: auto;
}
.top-header {
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
  border-bottom: 1px solid #e7edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 58px;
  padding: 0 20px;
  padding-top: var(--safe-top);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  position: relative;
  z-index: 1;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
/* 普通用户顶栏：浅色光斑 + 吉祥物问候，避免去侧栏后左侧空一块 */
.app-container.user-shell .top-header {
  height: 64px;
  overflow: hidden;
  background:
    radial-gradient(120px 48px at 8% 120%, rgba(47, 107, 255, 0.12), transparent 70%),
    radial-gradient(160px 56px at 36% -20%, rgba(255, 176, 197, 0.22), transparent 68%),
    linear-gradient(90deg, #f4f8ff 0%, #ffffff 48%, #fff7f2 100%);
}
.user-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  position: relative;
}
.user-mascot {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(160deg, #8ec0ff 0%, #2f6bff 58%, #7b6cff 100%);
  box-shadow: 0 6px 14px rgba(47, 107, 255, 0.28), inset 0 -6px 0 rgba(255, 255, 255, 0.12);
  position: relative;
}
.user-mascot::before {
  content: "";
  position: absolute;
  left: 10px;
  top: 13px;
  width: 5px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 13px 0 0 #fff, -3px 8px 0 2px rgba(255, 168, 186, 0.7), 16px 8px 0 2px rgba(255, 168, 186, 0.7);
}
.user-mascot::after {
  content: "";
  position: absolute;
  left: 13px;
  top: 22px;
  width: 12px;
  height: 7px;
  border: 2px solid #fff;
  border-top: none;
  border-radius: 0 0 12px 12px;
}
.user-brand-copy {
  min-width: 0;
}
.user-brand-copy strong {
  display: block;
  font-size: 14px;
  font-weight: 750;
  color: #1c2b4a;
  letter-spacing: 0.2px;
  line-height: 1.2;
}
.user-brand-copy span {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #667085;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: min(420px, 46vw);
}
.hdr-sparkle {
  position: absolute;
  color: #9ab6ff;
  font-size: 11px;
  pointer-events: none;
  animation: sparkle-float 2.8s ease-in-out infinite;
}
.hdr-sparkle.s1 {
  left: 34px;
  top: -2px;
}
.hdr-sparkle.s2 {
  left: 176px;
  top: 18px;
  color: #ffb4c8;
  animation-delay: 1.1s;
}
@keyframes sparkle-float {
  0%, 100% { transform: translateY(0) scale(1); opacity: 0.55; }
  50% { transform: translateY(-3px) scale(1.15); opacity: 1; }
}
/* 顶栏状态胶囊：演示 / 角色，不用默认 Tag 的描边感 */
.hd-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.2px;
  white-space: nowrap;
}
.hd-pill.demo {
  background: linear-gradient(180deg, #fff8eb 0%, #ffefd0 100%);
  color: #b54708;
  box-shadow: inset 0 0 0 1px rgba(247, 144, 9, 0.18);
}
.hd-pill.role-admin {
  background: linear-gradient(180deg, #eef2ff 0%, #e0e7ff 100%);
  color: #3538cd;
  box-shadow: inset 0 0 0 1px rgba(53, 56, 205, 0.12);
}
.hd-pill.role-plat {
  background: linear-gradient(180deg, #ecfdf3 0%, #d1fadf 100%);
  color: #067647;
  box-shadow: inset 0 0 0 1px rgba(18, 183, 106, 0.16);
}
/* 超级管理员：比管理员更深一档，一眼能区分 */
.hd-pill.role-super {
  background: linear-gradient(180deg, #f4f0ff 0%, #e4dcff 100%);
  color: #5a2bb8;
  box-shadow: inset 0 0 0 1px rgba(122, 90, 248, 0.18);
}
/* 普通用户顶栏的帮助中心入口 */
.hd-help.el-button {
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: #fff;
  color: #344054;
  font-size: 13px;
  font-weight: 650;
  box-shadow: inset 0 0 0 1px #e7edf5;
}
.hd-help.el-button:hover {
  color: #2f6bff;
  background: #f4f8ff;
  box-shadow: inset 0 0 0 1px #cddcff;
}
.hd-help .el-icon {
  margin-right: 4px;
}
.hd-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f79009;
  box-shadow: 0 0 0 3px rgba(247, 144, 9, 0.22);
}
.hd-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px 0 4px;
  margin-left: 4px;
  background: #f4f7fb;
  border-radius: 999px;
}
.hd-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(160deg, #4d86ff 0%, #2f6bff 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.hd-name {
  font-size: 13px;
  font-weight: 650;
  color: #1c2b4a;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hd-logout {
  height: 34px;
  padding: 0 14px;
  margin-left: 2px;
  border: none;
  border-radius: 999px;
  background: #fff;
  color: #344054;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px #e7edf5;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}
.hd-logout:hover {
  color: #b42318;
  background: #fef3f2;
  box-shadow: inset 0 0 0 1px #fecdca;
}
.user-info {
  color: #606266;
  font-size: 14px;
}
.el-main {
  background-color: var(--cs-bg, #eef2f7);
}
/* 普通用户无侧栏，聊天区铺满 */
.app-container.user-shell .el-main {
  padding: 12px 16px 16px;
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
  .user-brand-copy span {
    display: none;
  }
  .hdr-sparkle {
    display: none;
  }
  .app-container.user-shell .top-header {
    height: calc(56px + var(--safe-top));
  }
  .top-header {
    padding: 0 12px;
    padding-top: var(--safe-top);
    height: calc(50px + var(--safe-top));
  }
  .hd-user,
  .hd-pill.role-admin,
  .hd-pill.role-super,
  .hd-pill.role-plat {
    display: none;
  }
  /* 手机上帮助中心入口保留，普通用户没有侧栏可依附 */
  .hd-help.el-button {
    padding: 0 10px;
    font-size: 12px;
  }
  .hd-logout {
    height: 30px;
    padding: 0 12px;
    font-size: 12px;
  }
  .el-main {
    padding: 12px;
  }
  .chat-container {
    border-radius: 10px;
  }
  .mobile-drawer .el-menu {
    border-right: none;
    padding: 6px 0 16px;
  }
  .mobile-drawer .el-menu-item,
  .mobile-drawer .el-sub-menu__title {
    height: 42px;
    line-height: 42px;
    margin: 2px 10px;
    width: calc(100% - 20px);
    border-radius: 8px;
  }
  .mobile-drawer .el-menu-item.is-active {
    background: rgba(47, 107, 255, 0.28) !important;
    color: #fff !important;
  }
  .mobile-drawer .el-drawer__body {
    padding: 0;
    background: #1c2b4a;
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
