<template>
  <div class="page-card">
    <StubBanner description="默认未启用 Flowable。未打开流程引擎时本页无真实接口，当前展示为演示流程。" />
    <p class="hint">
      复杂工单（退款审批、投诉升级）走流程引擎：按定义好的节点逐个流转，每步留下处理人和意见。
      简单工单不必启动流程，直接用状态流转即可。
    </p>

    <SectionSwitch v-model="tab" :options="tabOptions" />

    <!-- 待办任务 -->
    <template v-if="tab === 'tasks'">
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <el-select v-model="taskStatus" style="width: 150px" @change="loadTasks">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="pending" />
            <el-option label="已完成" value="completed" />
          </el-select>
          <el-button @click="loadTasks">刷新</el-button>
        </div>
      </div>

      <el-table :data="tasks" stripe v-loading="busy === 'tasks'" empty-text="暂无待办任务"
        table-layout="fixed" max-height="420">
        <el-table-column prop="taskId" label="任务ID" min-width="100" />
        <el-table-column prop="workOrderNo" label="工单号" min-width="120" />
        <el-table-column prop="taskName" label="环节" min-width="130" />
        <el-table-column prop="assignee" label="处理人" min-width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" label="到达时间" min-width="150" />
        <el-table-column label="时限" min-width="150">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row) && row.status === 'pending' }">
              {{ row.dueTime }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : 'warning'" size="small">
              {{ row.status === 'completed' ? '已完成' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" :disabled="row.status === 'completed'" @click="openComplete(row)">处理</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 工单流程查询 -->
    <template v-else-if="tab === 'instance'">
      <div class="form-row">
        <el-input v-model="queryOrderId" placeholder="工单ID，例如 9001" style="width: 200px" />
        <el-button type="primary" :loading="busy === 'status'" @click="loadStatus">查询流程</el-button>
        <el-button :loading="busy === 'history'" @click="loadHistory">流程历史</el-button>
      </div>

      <el-descriptions v-if="instance" :column="3" border size="small" class="mini-table">
        <el-descriptions-item label="工单号">{{ instance.workOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="流程定义">{{ instance.definitionId }}</el-descriptions-item>
        <el-descriptions-item label="实例ID">{{ instance.processInstanceId }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ instance.currentNode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(instance.status)" size="small">{{ statusText(instance.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="启动时间">{{ instance.startTime }}</el-descriptions-item>
        <el-descriptions-item label="进度" :span="3">
          <el-progress :percentage="instance.progress" :stroke-width="8" />
        </el-descriptions-item>
      </el-descriptions>

      <!-- 取消是不可逆操作，做成描边小按钮并配一行后果说明，别让它抢页面的主视线 -->
      <div v-if="instance" class="btn-row">
        <el-button
          type="danger"
          plain
          size="small"
          :disabled="instance.status !== 'running'"
          :loading="busy === 'cancel'"
          @click="doCancel"
        >
          取消流程
        </el-button>
        <span class="muted">取消后流程不再继续流转，已产生的流转记录会保留</span>
      </div>

      <h5 v-if="history.length" class="sub-title">流程历史</h5>
      <el-timeline v-if="history.length" class="timeline-box">
        <el-timeline-item v-for="(h, i) in history" :key="i" :timestamp="h.time" placement="top">
          <div class="tl-node">{{ h.node }}</div>
          <div class="tl-body">
            {{ h.action }}　<span class="muted">{{ h.assignee }}</span>
            <p v-if="h.comment" class="tl-comment">{{ h.comment }}</p>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="查询工单后可查看它的流程历史" :image-size="70" />

      <h5 class="sub-title">启动流程</h5>
      <div class="form-row">
        <el-input v-model="startForm.workOrderId" placeholder="工单ID" style="width: 160px" />
        <el-select v-model="startForm.definitionId" style="width: 220px">
          <el-option v-for="d in definitions" :key="d.definitionId" :label="d.name" :value="d.definitionId" />
        </el-select>
        <el-input v-model="startForm.comment" placeholder="启动说明（选填）" style="width: 220px" />
        <el-button type="primary" :loading="busy === 'start'" @click="doStart">启动</el-button>
      </div>
    </template>

    <!-- 流程定义 -->
    <template v-else>
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <el-button @click="loadDefinitions">刷新</el-button>
        </div>
      </div>
      <el-table :data="definitions" stripe v-loading="busy === 'defs'" empty-text="暂无流程定义"
        table-layout="fixed" max-height="420">
        <el-table-column prop="definitionId" label="定义ID" min-width="130" />
        <el-table-column prop="name" label="流程名称" min-width="160" />
        <el-table-column prop="version" label="版本" min-width="80" />
        <el-table-column prop="nodeCount" label="节点数" min-width="90" />
        <el-table-column label="已部署" min-width="90">
          <template #default="{ row }">
            <el-tag :type="row.deployed === 1 ? 'success' : 'info'" size="small">{{ row.deployed === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deployTime" label="部署时间" min-width="150" />
      </el-table>
      <p class="muted">流程定义由后端部署，页面只做查看与选用，不支持在线编辑。</p>
    </template>

    <!-- 处理任务 -->
    <el-dialog :title="`处理任务 ${completeForm.taskId}`" v-model="completeVisible" width="520px">
      <el-form label-width="90px">
        <el-form-item label="环节">
          <span>{{ completeForm.taskName }}</span>
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="completeForm.comment" type="textarea" :rows="3" placeholder="例如：情况属实，同意退款" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'complete'" @click="doComplete">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** 工单流程：待办任务、流程实例查询与启动取消、流程定义 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SectionSwitch from '../components/SectionSwitch.vue'
import {
  cancelWorkOrderFlow, completeFlowTask, getFlowHistory, getWorkOrderFlowStatus,
  listFlowDefinitions, listFlowTasks, startWorkOrderFlow
} from '../api'

const tab = ref('tasks')
const tabOptions = [
  { value: 'tasks', label: '待办任务' },
  { value: 'instance', label: '流程查询' },
  { value: 'defs', label: '流程定义' }
]

const busy = ref('')
const tasks = ref([])
const taskStatus = ref('')
const definitions = ref([])
const queryOrderId = ref('9001')
const instance = ref(null)
const history = ref([])
const completeVisible = ref(false)
const completeForm = reactive({ taskId: '', taskName: '', comment: '' })
const startForm = reactive({ workOrderId: '9010', definitionId: 'wf-refund', comment: '' })

const statusText = (s) => ({ running: '进行中', completed: '已完成', cancelled: '已取消' }[s] || s)
const statusTag = (s) => ({ running: 'warning', completed: 'success', cancelled: 'info' }[s] || 'info')

/** 超时未处理的任务标红：时限早于当前时间且未完成 */
const isOverdue = (row) => {
  if (!row.dueTime) return false
  return new Date(row.dueTime.replace(/-/g, '/')).getTime() < Date.now()
}

const loadTasks = async () => {
  busy.value = 'tasks'
  try {
    const res = await listFlowTasks({ status: taskStatus.value || undefined })
    tasks.value = Array.isArray(res.data) ? res.data : []
  } catch {
    tasks.value = []
  } finally {
    busy.value = ''
  }
}

const loadDefinitions = async () => {
  busy.value = 'defs'
  try {
    const res = await listFlowDefinitions()
    definitions.value = Array.isArray(res.data) ? res.data : []
  } catch {
    definitions.value = []
  } finally {
    busy.value = ''
  }
}

const loadStatus = async () => {
  busy.value = 'status'
  try {
    const res = await getWorkOrderFlowStatus(queryOrderId.value)
    instance.value = res.data
  } catch {
    instance.value = null
  } finally {
    busy.value = ''
  }
}

const loadHistory = async () => {
  busy.value = 'history'
  try {
    const res = await getFlowHistory(queryOrderId.value)
    history.value = Array.isArray(res.data) ? res.data : []
  } catch {
    history.value = []
  } finally {
    busy.value = ''
  }
}

const openComplete = (row) => {
  Object.assign(completeForm, { taskId: row.taskId, taskName: row.taskName, comment: '' })
  completeVisible.value = true
}

const doComplete = async () => {
  busy.value = 'complete'
  try {
    const res = await completeFlowTask(completeForm.taskId, { comment: completeForm.comment })
    ElMessage.success(res.msg || '已提交')
    completeVisible.value = false
    loadTasks()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const doStart = async () => {
  if (!startForm.workOrderId) {
    ElMessage.warning('请填写工单ID')
    return
  }
  busy.value = 'start'
  try {
    const res = await startWorkOrderFlow(startForm.workOrderId, {
      definitionId: startForm.definitionId,
      comment: startForm.comment
    })
    ElMessage.success(res.msg || '流程已启动')
    queryOrderId.value = startForm.workOrderId
    loadStatus()
    loadHistory()
    loadTasks()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const doCancel = async () => {
  try {
    await ElMessageBox.confirm(
      `确认取消工单 ${instance.value.workOrderNo} 的流程？已流转的记录会保留，但流程不再继续。`,
      '取消流程',
      { type: 'warning' }
    )
  } catch {
    return
  }
  busy.value = 'cancel'
  try {
    const res = await cancelWorkOrderFlow(queryOrderId.value, {})
    ElMessage.success(res.msg || '已取消')
    loadStatus()
    loadHistory()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

onMounted(() => {
  loadTasks()
  loadDefinitions()
  loadStatus()
})
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
  flex-wrap: wrap;
}
.mini-table { max-width: 900px; margin: 10px 0; }
.btn-row {
  margin: 12px 0;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.sub-title { margin: 18px 0 10px; font-size: 13.5px; color: #1c2b4a; }
.timeline-box { max-width: 720px; padding-left: 4px; }
.tl-node { font-size: 13.5px; font-weight: 650; color: #1c2b4a; }
.tl-body { font-size: 13px; color: #475467; margin-top: 2px; }
.tl-comment { margin: 4px 0 0; color: #667085; font-size: 12.5px; }
.overdue { color: #b42318; font-weight: 650; }
.muted { color: #98a2b3; font-size: 12.5px; }
</style>
