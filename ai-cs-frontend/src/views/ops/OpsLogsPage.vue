<template>
  <div class="page-card">
    <p class="hint">{{ page.hint }}</p>
    <div class="page-toolbar">
      <el-form :inline="true" :model="query" @submit.prevent="search">
        <el-form-item label="requestId">
          <el-input v-model="query.requestId" clearable placeholder="请求号" />
        </el-form-item>
        <el-form-item label="sessionId">
          <el-input v-model="query.sessionId" clearable placeholder="会话号" />
        </el-form-item>
        <el-form-item label="traceId">
          <el-input v-model="query.traceId" clearable placeholder="链路号" />
        </el-form-item>
        <el-form-item label="服务">
          <el-input v-model="query.service" clearable placeholder="如 ai-cs-ops" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" clearable placeholder="全部" style="width: 110px">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="已脱敏文本" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </div>
    <el-table :data="page.list" stripe empty-text="暂无日志。先启动服务产生 logs/*.log，或用 requestId 精确查。">
      <el-table-column prop="timestamp" label="时间" width="180" />
      <el-table-column prop="level" label="级别" width="80" />
      <el-table-column prop="service" label="服务" width="130" />
      <el-table-column prop="requestId" label="requestId" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button v-if="row.requestId" link type="primary" @click="openRequest(row.requestId)">{{ row.requestId }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="内容" min-width="280" show-overflow-tooltip />
    </el-table>
    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="page.total"
        :page-size="query.size"
        :current-page="query.page"
        @current-change="onPage"
      />
    </div>
    <el-drawer v-model="drawer" title="同一 requestId 时间线" size="50%">
      <el-timeline>
        <el-timeline-item v-for="(item, idx) in timeline" :key="idx" :timestamp="item.timestamp">
          <div><el-tag size="small">{{ item.service || 'unknown' }}</el-tag> {{ item.level }}</div>
          <p class="line">{{ item.message }}</p>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getOpsLogs, getOpsLogsByRequestId } from '../../api'

const route = useRoute()

const query = reactive({
  requestId: '',
  sessionId: '',
  traceId: '',
  service: '',
  level: '',
  keyword: '',
  page: 1,
  size: 20
})
const page = ref({ list: [], total: 0, hint: '' })
const drawer = ref(false)
const timeline = ref([])

const search = async () => {
  query.page = 1
  await load()
}

const load = async () => {
  const res = await getOpsLogs(query)
  page.value = res.data || { list: [], total: 0 }
}

const onPage = async (num) => {
  query.page = num
  await load()
}

const openRequest = async (requestId) => {
  const res = await getOpsLogsByRequestId(requestId)
  timeline.value = res.data || []
  drawer.value = true
}

onMounted(() => {
  if (route.query.requestId) {
    query.requestId = String(route.query.requestId)
  }
  load()
})
</script>

<style scoped>
.hint { color: #6b7280; line-height: 1.6; font-size: 13px; margin-bottom: 12px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.line { margin-top: 6px; color: #374151; word-break: break-all; }
</style>
