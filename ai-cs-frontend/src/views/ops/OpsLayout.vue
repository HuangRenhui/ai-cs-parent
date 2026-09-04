<template>
  <div class="ops-layout">
    <el-tabs :model-value="active" class="ops-tabs" @tab-change="go">
      <el-tab-pane label="总览" name="/ops" />
      <el-tab-pane label="日志查询" name="/ops/logs" />
      <el-tab-pane label="链路追踪" name="/ops/traces" />
      <el-tab-pane label="告警" name="/ops/alerts" />
      <el-tab-pane label="服务健康" name="/ops/health" />
    </el-tabs>
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const active = computed(() => route.path)
const go = (path) => router.push(path)
</script>

<style scoped>
.ops-tabs { margin-bottom: 8px; }
</style>
