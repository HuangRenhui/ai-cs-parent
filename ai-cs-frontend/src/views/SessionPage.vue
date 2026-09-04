<template>
  <div class="session-page">
    <div class="page-card list-pane">
      <div class="page-toolbar">
        <h3>会话列表</h3>
        <el-button @click="loadSessions">刷新</el-button>
      </div>
      <el-table :data="sessions" highlight-current-row @row-click="openSession" v-loading="loading" empty-text="暂无会话">
        <el-table-column prop="sessionId" label="会话ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="customerId" label="客户" width="80" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.sessionType === 2 ? '人工' : 'AI' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.sessionStatus === 1 ? 'success' : 'info'">{{ row.sessionStatus === 1 ? '进行中' : '已结束' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="page-card detail-pane">
      <div class="page-toolbar">
        <h3>{{ currentSession ? '会话详情' : '选择左侧会话查看记录' }}</h3>
        <el-button v-if="currentSession && currentSession.sessionStatus === 1" type="danger" plain @click="closeCurrent">结束</el-button>
      </div>
      <div class="msg-list">
        <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.msgType === 1 ? 'user' : 'ai']">
          <div class="role">{{ msg.msgType === 1 ? '用户' : msg.msgType === 3 ? '坐席' : 'AI' }}</div>
          <div class="body">{{ msg.msgContent }}</div>
          <div class="time">{{ msg.createTime }}</div>
        </div>
        <el-empty v-if="!messages.length" description="暂无消息" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { endSession, listSessionMessages, listSessions } from '../api'

const sessions = ref([])
const messages = ref([])
const currentSession = ref(null)
const loading = ref(false)

const loadSessions = async () => {
  loading.value = true
  try {
    const res = await listSessions()
    sessions.value = res.data || []
  } catch {
    sessions.value = []
  } finally {
    loading.value = false
  }
}

const openSession = async (row) => {
  currentSession.value = row
  try {
    const res = await listSessionMessages(row.sessionId)
    messages.value = res.data || []
  } catch {
    messages.value = []
  }
}

const closeCurrent = async () => {
  try {
    await endSession(currentSession.value.sessionId)
    ElMessage.success('会话已结束')
    currentSession.value.sessionStatus = 2
    loadSessions()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(loadSessions)
</script>

<style scoped>
.session-page { display: flex; gap: 16px; min-height: calc(100vh - 120px); }
.list-pane { width: 46%; }
.detail-pane { flex: 1; }
h3 { font-size: 16px; }
.msg-list { max-height: calc(100vh - 220px); overflow: auto; }
.msg { margin-bottom: 12px; padding: 10px 12px; border-radius: 8px; background: #f7f9fc; }
.msg.user { background: #eef4ff; }
.role { font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.body { line-height: 1.6; white-space: pre-wrap; }
.time { font-size: 12px; color: #9aa3b2; margin-top: 4px; }
</style>
