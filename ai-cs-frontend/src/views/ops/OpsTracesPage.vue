<template>
  <div class="page-card">
    <p class="hint">{{ page.hint }}</p>
    <div class="page-toolbar">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="traceId">
          <el-input v-model="query.traceId" clearable />
        </el-form-item>
        <el-form-item label="sessionId">
          <el-input v-model="query.sessionId" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </div>
    <el-table :data="page.list" stripe empty-text="暂无链路。接入 Zipkin 前仅在日志带 traceId 时聚合。">
      <el-table-column prop="traceId" label="traceId" min-width="180" />
      <el-table-column prop="sessionId" label="sessionId" min-width="140" />
      <el-table-column prop="spanCount" label="span" width="80" />
      <el-table-column prop="startTime" label="开始" width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="open(row)">瀑布图</el-button>
          <el-button v-if="row.spans?.[0]?.requestId" size="small" link type="primary" @click="$router.push({ path: '/ops/logs', query: { requestId: row.spans[0].requestId } })">日志</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-drawer v-model="drawer" title="span 瀑布（日志聚合）" size="40%">
      <el-timeline>
        <el-timeline-item v-for="span in current.spans" :key="span.spanId" :timestamp="span.startTime">
          <strong>{{ span.service }}</strong>
          <div class="muted">{{ span.name }} · requestId {{ span.requestId || '-' }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getOpsTraces } from '../../api'

const query = reactive({ traceId: '', sessionId: '', page: 1, size: 20 })
const page = ref({ list: [], hint: '' })
const drawer = ref(false)
const current = ref({ spans: [] })

const search = async () => {
  const res = await getOpsTraces(query)
  page.value = res.data || { list: [] }
}

const open = (row) => {
  current.value = row
  drawer.value = true
}

onMounted(search)
</script>

<style scoped>
.hint { color: #6b7280; line-height: 1.6; font-size: 13px; margin-bottom: 12px; }
.muted { color: #6b7280; margin-top: 4px; font-size: 12px; }
</style>
