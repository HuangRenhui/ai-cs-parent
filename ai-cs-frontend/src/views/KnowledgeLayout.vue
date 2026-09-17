<template>
  <!-- 知识库：与开放接入/运维同一套下划线标签 + 单卡片 -->
  <div class="knowledge-layout">
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
/** 知识库各功能域：FAQ / 文档 / 图谱 / 图谱增强 / 多模态 / 提示词，后续新增功能域在此追加 */
const tabOptions = [
  { value: '/knowledge', label: '知识库管理' },
  { value: '/knowledge/document', label: '文档库' },
  { value: '/knowledge/graph', label: '知识图谱' },
  { value: '/knowledge/graph-manage', label: '图谱管理' },
  { value: '/knowledge/graph-plus', label: '图谱增强' },
  { value: '/knowledge/multimodal', label: '多模态' },
  { value: '/knowledge/multimodal-qa', label: '多模态问答' },
  { value: '/knowledge/media', label: '媒体资源' },
  { value: '/knowledge/file', label: '文件管理' },
  { value: '/knowledge/settings', label: '提示词与检索' }
]
const go = (path) => router.push(path)
</script>
