<template>
  <!-- 登录页外壳：左品牌说明 + 右表单，窄屏时品牌区收起 -->
  <div class="login-shell" :class="`is-${variant}`">
    <aside class="login-aside">
      <div class="aside-inner">
        <div class="aside-brand">
          <span class="aside-logo">{{ logo }}</span>
          <div>
            <h1 class="aside-title">{{ title }}</h1>
            <p class="aside-sub">{{ subtitle }}</p>
          </div>
        </div>
        <ul class="aside-features">
          <li v-for="f in features" :key="f">
            <el-icon :size="14"><Select /></el-icon>
            <span>{{ f }}</span>
          </li>
        </ul>
      </div>
    </aside>

    <main class="login-main">
      <!-- 右上角快捷入口 -->
      <div class="login-corner">
        <router-link v-if="showHelp" class="login-corner-btn" to="/help-center" title="帮助中心">
          <el-icon :size="16"><Headset /></el-icon>
        </router-link>
        <router-link class="login-corner-btn" to="/widget" title="在线客服">
          <el-icon :size="16"><Phone /></el-icon>
        </router-link>
      </div>
      <div class="login-inner">
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup>
/**
 * 登录页外壳：左侧放品牌与能力说明，右侧放表单。
 *
 * 两个入口共用同一套布局、不同主色：C 端亮蓝、运营后台深蓝黑，
 * 让人一眼知道自己在哪个入口，避免把后台当业务页操作。
 */
import { Headset, Phone, Select } from '@element-plus/icons-vue'

defineProps({
  /** light = 普通用户（默认）；dark = 运营后台 */
  variant: { type: String, default: 'light' },
  /** 系统名，左侧大字 */
  title: { type: String, default: '智能客服系统' },
  /** 一句话定位，系统名下方 */
  subtitle: { type: String, default: '全渠道接入 · 知识库驱动 · 人机协同' },
  /** 左上角标识字 */
  logo: { type: String, default: '客' },
  /** 左侧能力清单，用 ✓ 逐条列出 */
  features: {
    type: Array,
    default: () => [
      '网页、小程序、在线客服多渠道统一接待',
      '知识库自动应答，答不好自动转人工',
      '客户、会话、工单全流程留痕可追溯'
    ]
  },
  /** 帮助中心是 C 端自助页，运营后台不需要 */
  showHelp: { type: Boolean, default: true }
})
</script>
