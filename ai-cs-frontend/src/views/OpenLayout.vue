<template>
  <!-- 开放接入：标签贴在同一张白卡片顶部，子页不再套一层卡片 -->
  <div class="open-layout">
    <SectionSwitch :model-value="active" :options="tabOptions" @change="go" />
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SectionSwitch from '../components/SectionSwitch.vue'

const route = useRoute()
const router = useRouter()
const active = computed(() => route.path)
const tabOptions = [
  { value: '/open', label: '行业包与工具' },
  { value: '/open/webhook', label: '回调通知' },
  { value: '/open/card', label: '卡片模板' },
  { value: '/open/security', label: '出站安全' },
  { value: '/open/prompt', label: '提示词包' },
  { value: '/open/openapi', label: '接口导入' },
  { value: '/open/widget-config', label: 'Widget 隐私与白标' }
]
/** 标签值即路由 path */
const go = (path) => router.push(path)
</script>
