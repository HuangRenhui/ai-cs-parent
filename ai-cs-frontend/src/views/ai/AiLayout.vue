<template>
  <!-- AI 能力：与知识库/运维同一套下划线标签 + 单卡片 -->
  <div class="ai-layout">
    <SectionSwitch :model-value="active" :options="tabOptions" @change="go" />
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SectionSwitch from '../../components/SectionSwitch.vue'

const route = useRoute()
const router = useRouter()
/** /ai/tools/:code 是工具箱的下钻页，标签高亮回工具箱 */
const active = computed(() => (route.path.startsWith('/ai/tools') ? '/ai/tools' : route.path))
/** 与 config/menus.js 的 AI 能力子项一一对应，加子页时两边都要补 */
const tabOptions = [
  { value: '/ai/model', label: '模型管理' },
  { value: '/ai/intent', label: '意图配置' },
  { value: '/ai/slot', label: '填槽配置' },
  { value: '/ai/tools', label: 'AI 工具' },
  { value: '/ai/assist', label: '坐席辅助' }
]
const go = (path) => router.push(path)
</script>
