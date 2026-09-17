<template>
  <!-- 运维：与开放接入同一套下划线标签 + 单卡片 -->
  <div class="ops-layout">
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
const active = computed(() => route.path)
const tabOptions = [
  { value: '/ops', label: '总览' },
  { value: '/ops/logs', label: '日志查询' },
  { value: '/ops/traces', label: '链路追踪' },
  // 审计与流量本质也是「看记录」，和日志/链路同类，因此归在运维而不是系统管理
  { value: '/ops/audit', label: '操作审计' },
  { value: '/ops/traffic', label: '流量分析' },
  { value: '/ops/alerts', label: '告警' },
  { value: '/ops/health', label: '服务健康' }
]
const go = (path) => router.push(path)
</script>
