<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div>
        <h3>会话列表</h3>
        <p class="page-desc">查看智能接待与人工会话，点一行即可打开聊天记录。</p>
      </div>
      <div class="toolbar-actions">
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 110px">
          <el-option label="进行中" :value="1" />
          <el-option label="已结束" :value="2" />
        </el-select>
        <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 110px">
          <el-option label="智能" :value="1" />
          <el-option label="人工" :value="2" />
        </el-select>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
        />
        <FilterActions @search="reload" @reset="resetSearch" />
      </div>
    </div>
    <!-- 各列都用 min-width，剩余宽度均分，避免只把后两列拉得很宽 -->
    <el-table :data="sessions" stripe highlight-current-row @row-click="openSession" v-loading="loading" empty-text="暂无会话" table-layout="fixed" max-height="680">
      <el-table-column prop="sessionId" label="会话号" min-width="140" show-overflow-tooltip />
      <el-table-column label="客户" min-width="108" show-overflow-tooltip>
        <template #default="{ row }">{{ customerCell(row.customerId) }}</template>
      </el-table-column>
      <el-table-column label="坐席" min-width="128" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.agentId">{{ agentCell(row.agentId) }}</span>
          <span v-else class="cell-muted">智能接待</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="80">
        <template #default="{ row }">
          <TypeTag :text="row.sessionType === 2 ? '人工' : '智能'" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="88">
        <template #default="{ row }">
          <el-tag size="small" :type="row.sessionStatus === 1 ? 'success' : 'info'">{{ row.sessionStatus === 1 ? '进行中' : '已结束' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ timeCell(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.endTime">{{ timeCell(row.endTime) }}</span>
          <span v-else class="cell-muted">进行中</span>
        </template>
      </el-table-column>
      <el-table-column label="时长" min-width="100">
        <template #default="{ row }">{{ durationCell(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="92">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="view" @click.stop="openSession(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="pageNum" v-model:size="pageSize" :total="total" @change="loadSessions" />

    <!-- 会话详情：点行或查看后弹窗，不再用右侧分栏 -->
    <el-dialog
      :title="detailTitle"
      v-model="detailVisible"
      width="640px"
      top="6vh"
      class="session-detail-dialog"
    >
      <div class="detail-toolbar">
        <el-tag v-if="currentSession?.sessionType === 2 && currentAgentLabel" type="success" effect="light">{{ currentAgentLabel }}</el-tag>
        <el-button v-if="currentSession && currentSession.sessionStatus === 1 && currentSession.sessionType !== 2" type="warning" plain round @click="transferCurrent">转人工</el-button>
        <el-button v-if="currentSession && currentSession.sessionStatus === 1" type="danger" plain round @click="closeCurrent">结束</el-button>
      </div>
      <div class="msg-list">
        <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.msgType === 1 ? 'user' : 'ai']">
          <div class="role">{{ msg.msgType === 1 ? '用户' : msg.msgType === 3 ? '坐席' : '客服' }}</div>
          <div class="body">{{ msg.msgContent }}</div>
          <div class="time">{{ msg.createTime }}</div>
        </div>
        <el-empty v-if="!messages.length" description="暂无消息" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { endSession, listAgents, listCustomers, listSessionMessages, pageSessions, transferSession } from '../api'
import TablePager from '../components/TablePager.vue'

const sessions = ref([])
const messages = ref([])
const currentSession = ref(null)
const detailVisible = ref(false)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref()
const typeFilter = ref()
const timeRange = ref(null)
const agents = ref([])
const customers = ref([])
const lastTransferAgent = ref(null)

const currentAgentLabel = computed(() => {
  if (lastTransferAgent.value) {
    return `工号 ${lastTransferAgent.value.agentNo} ${lastTransferAgent.value.agentName}`
  }
  const row = currentSession.value
  if (!row?.agentId) return ''
  return agentCell(row.agentId)
})

/** 弹窗标题：客户名 + 类型，不把 sess_ 铺在标题上 */
const detailTitle = computed(() => {
  const row = currentSession.value
  if (!row) return '会话详情'
  const who = customerCell(row.customerId)
  const kind = row.sessionType === 2 ? '人工' : '智能'
  return `会话详情 · ${who} · ${kind}`
})

/** 列表把客户 ID 显示成昵称/手机号 */
const customerCell = (customerId) => {
  if (!customerId) return '—'
  const c = customers.value.find((x) => String(x.id) === String(customerId))
  if (!c) return `客户 #${customerId}`
  return c.nickname || c.phone || `客户 #${customerId}`
}
/** 列表/详情共用：把坐席 ID 显示成「工号 A002 张晓梅」 */
const agentCell = (agentId) => {
  const agent = agents.value.find((a) => String(a.id) === String(agentId))
  if (!agent) return `坐席 #${agentId}`
  const no = agent.agentNo || ('A' + String(agent.id).padStart(3, '0'))
  return `工号 ${no} ${agent.agentName}`
}

/** 列表时间去掉秒，列可以收窄，悬停仍看得到完整值 */
const timeCell = (value) => {
  if (!value) return ''
  const text = String(value)
  return text.length >= 19 ? text.slice(0, 16) : text
}

/** 把开始/结束时间收成短文案；超过一天改成「X 天」，避免「225 小时」撑爆列 */
const durationCell = (row) => {
  if (!row?.startTime) return '—'
  const start = Date.parse(String(row.startTime).replace(/-/g, '/'))
  if (Number.isNaN(start)) return '—'
  const endRaw = row.endTime ? Date.parse(String(row.endTime).replace(/-/g, '/')) : Date.now()
  const mins = Math.max(0, Math.round((endRaw - start) / 60000))
  if (mins < 1) return '< 1 分'
  if (mins < 60) return `${mins} 分钟`
  if (mins < 1440) {
    const h = Math.floor(mins / 60)
    const m = mins % 60
    return m ? `${h}小时${m}分` : `${h} 小时`
  }
  const days = Math.floor(mins / 1440)
  const h = Math.floor((mins % 1440) / 60)
  return h ? `${days}天${h}小时` : `${days} 天`
}

/** 本地把当前时刻格式成和列表一致的 `YYYY-MM-DD HH:mm:ss` */
const formatNow = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const reload = () => {
  pageNum.value = 1
  loadSessions()
}

/** 清空状态、类型和时间范围 */
const resetSearch = () => {
  statusFilter.value = undefined
  typeFilter.value = undefined
  timeRange.value = null
  reload()
}

const loadSessions = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (statusFilter.value != null && statusFilter.value !== '') params.sessionStatus = statusFilter.value
    if (typeFilter.value != null && typeFilter.value !== '') params.sessionType = typeFilter.value
    if (timeRange.value?.length === 2) {
      params.startTime = timeRange.value[0]
      params.endTime = timeRange.value[1]
    }
    const res = await pageSessions(params)
    sessions.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    sessions.value = []
  } finally {
    loading.value = false
  }
}

/** 打开会话详情弹窗并拉取消息 */
const openSession = async (row) => {
  lastTransferAgent.value = null
  currentSession.value = row
  detailVisible.value = true
  try {
    const res = await listSessionMessages(row.sessionId)
    messages.value = res.data || []
  } catch {
    messages.value = []
  }
}

const transferCurrent = async () => {
  try {
    const res = await transferSession(currentSession.value.sessionId)
    const agent = res.data
    lastTransferAgent.value = agent
    const tip = agent?.agentName
      ? `已转接 工号 ${agent.agentNo} ${agent.agentName}`
      : '已转接人工'
    ElMessage.success(tip)
    currentSession.value.sessionType = 2
    if (agent?.agentId) currentSession.value.agentId = agent.agentId
    loadSessions()
  } catch {
    /* 拦截器已提示 */
  }
}

const closeCurrent = async () => {
  try {
    await endSession(currentSession.value.sessionId)
    ElMessage.success('会话已结束')
    currentSession.value.sessionStatus = 2
    // 结束时间用当前时刻，列表「结束时间 / 时长」立刻能对上
    currentSession.value.endTime = formatNow()
    loadSessions()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(async () => {
  loadSessions()
  try {
    const [aRes, cRes] = await Promise.all([listAgents(), listCustomers()])
    agents.value = aRes.data || []
    customers.value = Array.isArray(cRes.data) ? cRes.data : (cRes.data?.records || [])
  } catch {
    agents.value = []
    customers.value = []
  }
})
</script>

<style scoped>
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
  min-height: 32px;
}
.msg-list { max-height: 52vh; overflow: auto; }
.msg { margin-bottom: 12px; padding: 12px 14px; border-radius: 12px; background: #f7f9fc; border: 1px solid #eef2f7; }
.msg.user { background: #eef4ff; border-color: #d9e4ff; }
.msg.ai { background: #fff; }
.role { font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.body { line-height: 1.6; white-space: pre-wrap; }
.time { font-size: 12px; color: #9aa3b2; margin-top: 4px; }
@media (max-width: 640px) {
  .msg-list { max-height: 45vh; }
}
</style>
