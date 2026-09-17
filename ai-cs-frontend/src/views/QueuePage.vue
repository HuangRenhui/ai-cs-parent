<template>
  <div class="page-card">
    <StubBanner />
    <div class="page-toolbar">
      <div>
        <h3>排队监控</h3>
        <p class="page-desc">查看排队会话与坐席负载，支持手动分配、转接与咨询。</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="load">刷新</el-button>
        <TbBtn act="delete" label="批量删除" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>

    <div class="metric-row">
      <div class="metric"><span class="metric-num">{{ monitor.waiting ?? 0 }}</span><span class="metric-label">排队中</span></div>
      <div class="metric"><span class="metric-num">{{ monitor.avgWaitSeconds ?? 0 }}s</span><span class="metric-label">平均等待</span></div>
      <div class="metric"><span class="metric-num">{{ monitor.maxWaitSeconds ?? 0 }}s</span><span class="metric-label">最长等待</span></div>
      <div class="metric"><span class="metric-num">{{ monitor.onlineAgents ?? 0 }}</span><span class="metric-label">在线坐席</span></div>
      <div class="metric"><span class="metric-num">{{ monitor.busyAgents ?? 0 }}</span><span class="metric-label">忙碌</span></div>
      <div class="metric"><span class="metric-num">{{ monitor.idleAgents ?? 0 }}</span><span class="metric-label">空闲</span></div>
      <div class="metric"><span class="metric-num">{{ monitor.assignToday ?? 0 }}</span><span class="metric-label">今日分配</span></div>
      <div class="metric"><span class="metric-num">{{ monitor.abandonToday ?? 0 }}</span><span class="metric-label">今日放弃</span></div>
    </div>

    <el-table :data="records" stripe v-loading="loading" empty-text="暂无排队会话" table-layout="fixed" max-height="600" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="sessionId" label="会话号" min-width="160" show-overflow-tooltip />
      <el-table-column prop="customerName" label="客户" min-width="110" show-overflow-tooltip />
      <el-table-column prop="lastMessage" label="最后一句" min-width="180" show-overflow-tooltip />
      <el-table-column prop="skillGroup" label="期望技能组" min-width="130" show-overflow-tooltip />
      <el-table-column label="已等待" min-width="90">
        <template #default="{ row }">{{ row.waitSeconds }}s</template>
      </el-table-column>
      <el-table-column label="优先级" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.priority === 1 ? 'danger' : 'info'" size="small">{{ row.priority === 1 ? '高' : '普通' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enqueueTime" label="进入时间" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="assign" @click="openAssign(row)" />
            <TblAct act="run" label="转接" @click="openTransfer(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog title="分配坐席" v-model="assignVisible" width="420px">
      <el-form label-width="80px">
        <el-form-item label="会话号">{{ current.sessionId }}</el-form-item>
        <el-form-item label="坐席">
          <el-select v-model="assignAgentId" placeholder="选择坐席" style="width: 100%">
            <el-option v-for="a in onlineAgents" :key="a.id" :label="a.agentName" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="assign">分配</el-button>
      </template>
    </el-dialog>

    <el-dialog title="转接会话" v-model="transferVisible" width="420px">
      <p class="dialog-lead">可转给其他技能组重新排队，或直接指定坐席接手。</p>
      <el-form label-width="90px">
        <el-form-item label="会话号">{{ transferRow.sessionId }}</el-form-item>
        <el-form-item label="目标技能组">
          <el-select v-model="transferSkillGroup" placeholder="按技能组重新排队" clearable style="width: 100%">
            <el-option v-for="g in skillGroups" :key="g.groupCode" :label="g.groupName" :value="g.groupName" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标坐席">
          <el-select v-model="transferAgentId" placeholder="不指定则按技能组排队" clearable style="width: 100%">
            <el-option v-for="a in onlineAgents" :key="a.id" :label="a.agentName" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferring" @click="submitTransfer">确定转接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listQueue, getQueueMonitor, assignQueueSession, transferQueueSession, removeQueueSession, listAgents, listSkillGroups } from '../api'
import TablePager from '../components/TablePager.vue'
import TbBtn from '../components/TbBtn.vue'
import TblAct from '../components/TblAct.vue'
import { useClientPager } from '../composables/useClientPager'

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const loading = ref(false)
const monitor = ref({})
const onlineAgents = ref([])
const assignVisible = ref(false)
const assignAgentId = ref(null)
const current = ref({})
const selectedRows = ref([])
const batchRemoving = ref(false)
const transferVisible = ref(false)
const transferRow = ref({})
const transferSkillGroup = ref('')
const transferAgentId = ref(null)
const transferring = ref(false)
const skillGroups = ref([])

const onSelect = (rows) => {
  selectedRows.value = rows
}

const load = async () => {
  loading.value = true
  try {
    const [q, m] = await Promise.all([listQueue(), getQueueMonitor()])
    list.value = q.data || []
    monitor.value = m.data || {}
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}
const loadAgents = async () => {
  try {
    const res = await listAgents()
    onlineAgents.value = (res.data || []).filter((a) => a.agentStatus === 1)
  } catch {
    onlineAgents.value = []
  }
}
const loadSkillGroups = async () => {
  try {
    const res = await listSkillGroups()
    skillGroups.value = res.data || []
  } catch {
    skillGroups.value = []
  }
}
const openAssign = (row) => {
  current.value = row
  assignAgentId.value = null
  assignVisible.value = true
}
const assign = async () => {
  if (!assignAgentId.value) {
    ElMessage.warning('请选择坐席')
    return
  }
  await assignQueueSession(current.value.sessionId, assignAgentId.value)
  ElMessage.success('已分配')
  assignVisible.value = false
  load()
}

/** 打开转接弹窗：默认带上原期望技能组，坐席留空表示按技能组排队 */
const openTransfer = (row) => {
  transferRow.value = row
  transferSkillGroup.value = row.skillGroup || ''
  transferAgentId.value = null
  transferVisible.value = true
}
const submitTransfer = async () => {
  if (!transferSkillGroup.value && !transferAgentId.value) {
    ElMessage.warning('请选择目标技能组或坐席')
    return
  }
  transferring.value = true
  try {
    await transferQueueSession(transferRow.value.sessionId, transferSkillGroup.value || null, transferAgentId.value || null)
    ElMessage.success('已转接')
    transferVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    transferring.value = false
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认将会话「${row.sessionId}」移出排队？`, '提示', { type: 'warning' })
  await removeQueueSession(row.sessionId)
  ElMessage.success('已移出')
  load()
}

/** 批量移出排队 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定将选中的 ${rows.length} 个会话移出排队吗？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await removeQueueSession(row.sessionId)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已移出 ${ok} 个${fail ? `，失败 ${fail} 个` : ''}`)
    else ElMessage.error('操作失败')
    selectedRows.value = []
    load()
  } finally {
    batchRemoving.value = false
  }
}

onMounted(() => {
  load()
  loadAgents()
  loadSkillGroups()
})
</script>

<style scoped>
.metric-row { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.metric {
  flex: 1 1 110px;
  min-width: 110px;
  padding: 12px 14px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-num { font-size: 20px; font-weight: 700; color: #1c2b4a; }
.metric-label { font-size: 12px; color: #6b7280; }
.dialog-lead { margin: 0 0 12px; color: #6b7280; font-size: 13px; }
</style>
