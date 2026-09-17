<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div>
        <h3>工单管理</h3>
        <p class="page-desc">跟踪咨询、投诉与售后工单，支持分配坐席与关闭。</p>
      </div>
      <div class="toolbar-actions">
        <el-input v-model="searchKeyword" placeholder="请输入工单号或内容" clearable style="width: 200px" @keyup.enter="searchWorkOrders" />
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 130px">
          <el-option label="待处理" :value="1" />
          <el-option label="处理中" :value="2" />
          <el-option label="已完成" :value="3" />
          <el-option label="已关闭" :value="4" />
        </el-select>
        <FilterActions @search="searchWorkOrders" @reset="resetSearch" />
        <TbBtn act="add" label="新增" @click="openCreateDialog" />
        <TbBtn act="export" @click="exportVisible = true" />
        <TbBtn act="complete" label="完成" :disabled="!selectedRows.length" :loading="batchCompleting" @click="batchComplete" />
        <TbBtn act="close" label="关闭" :disabled="!selectedRows.length" :loading="batchClosing" @click="batchClose" />
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>

    <el-table :data="workOrderList" stripe v-loading="loading" empty-text="暂无工单或加载失败" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column prop="orderNo" label="工单号" min-width="148" show-overflow-tooltip />
      <el-table-column label="类型" min-width="88">
        <template #default="{ row }">
          <TypeTag :text="row.orderType" />
        </template>
      </el-table-column>
      <el-table-column label="工单内容" min-width="160">
        <template #default="{ row }">
          <CellText title="工单内容" :text="row.orderContent" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="88">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.orderStatus)">
            {{ statusLabel(scope.row.orderStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="168" />
      <el-table-column prop="updateTime" label="更新时间" min-width="168" />
      <el-table-column label="操作" min-width="470">
        <template #default="scope">
          <div class="table-actions">
            <TblAct act="detail" @click="showDetail(scope.row)" />
            <TblAct act="edit" @click="editWorkOrder(scope.row)" />
            <TblAct v-if="scope.row.orderStatus === 1 || scope.row.orderStatus === 2" act="assign" @click="openAssign(scope.row)" />
            <TblAct v-if="scope.row.orderStatus === 1 || scope.row.orderStatus === 2" act="complete" @click="handleComplete(scope.row.id)" />
            <TblAct v-if="scope.row.orderStatus !== 4" act="close" @click="handleClose(scope.row.id)" />
            <TblAct act="similar" @click="showSimilar(scope.row)" />
            <TblAct act="classify" @click="handleClassify(scope.row.id)" />
            <TblAct act="delete" @click="handleDelete(scope.row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="pageNum" v-model:size="pageSize" :total="total" @change="loadWorkOrders" />

    <ExportPickDialog
      v-model="exportVisible"
      title="导出工单"
      :fields="exportFields"
      :selected-count="selectedRows.length"
      :page-count="workOrderList.length"
      @confirm="doExport"
    />

    <WorkOrderDialog
      v-model="showCreateDialog"
      :form="form"
      :is-edit="isEdit"
      :loading="saving"
      :customers="customers"
      :sessions="sessions"
      @submit="saveWorkOrder"
    />

    <el-dialog title="分配坐席" v-model="assignVisible" width="420px">
      <p class="dialog-lead">选择一名坐席接手该工单，分配后状态进入处理中。</p>
      <el-select v-model="assignAgentId" placeholder="选择坐席" style="width: 100%">
        <el-option
          v-for="a in agents"
          :key="a.id"
          :label="`工号 ${a.agentNo || ('A' + String(a.id).padStart(3, '0'))}　${a.agentName}`"
          :value="a.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 工单详情：列表只放关键列，完整字段走详情接口 -->
    <el-dialog title="工单详情" v-model="detailVisible" width="600px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="编号">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.orderType }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.orderStatus) }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detail.assignee || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="工单内容" :span="2">{{ detail.orderContent }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog title="相似工单" v-model="similarVisible" width="720px">
      <el-table :data="similarList" stripe empty-text="暂无相似工单" table-layout="fixed" max-height="420">
        <el-table-column prop="orderNo" label="工单号" min-width="148" show-overflow-tooltip />
        <el-table-column label="类型" min-width="88">
          <template #default="{ row }">
            <TypeTag :text="row.orderType" />
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="160">
          <template #default="{ row }">
            <CellText title="工单内容" :text="row.orderContent" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignWorkOrder, autoClassifyWorkOrder, closeWorkOrder, completeWorkOrder, createWorkOrder, deleteWorkOrder, getWorkOrderDetail, listAgents, listCustomers, listSessions, listWorkOrders, similarWorkOrders, updateWorkOrder } from '../api'
import WorkOrderDialog from '../components/WorkOrderDialog.vue'
import TablePager from '../components/TablePager.vue'
import ExportPickDialog from '../components/ExportPickDialog.vue'
import { rowsToCsv, downloadTextFile } from '../utils/listCsv'

const workOrderList = ref([])
const showCreateDialog = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const searchKeyword = ref('')
const statusFilter = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const form = ref({ id: '', orderType: '', orderContent: '', sessionId: '', customerId: '', priority: '普通' })
const customers = ref([])
const sessions = ref([])
const assignVisible = ref(false)
const assignAgentId = ref(null)
const assignRow = ref(null)
const agents = ref([])
const similarVisible = ref(false)
const similarList = ref([])
const selectedRows = ref([])
const exportVisible = ref(false)
const batchRemoving = ref(false)
const batchCompleting = ref(false)
const batchClosing = ref(false)

/** 导出可选字段，状态码转成中文 */
const exportFields = [
  { key: 'id', label: '编号' },
  { key: 'orderNo', label: '工单号' },
  { key: 'orderType', label: '类型' },
  { key: 'orderContent', label: '工单内容' },
  { key: 'orderStatus', label: '状态', value: (r) => statusLabel(r.orderStatus) },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '更新时间' }
]

const onSelect = (rows) => {
  selectedRows.value = rows
}

const statusLabel = (code) => {
  return { 1: '待处理', 2: '处理中', 3: '已完成', 4: '已关闭' }[code] || '未知'
}

const getStatusType = (status) => {
  if (status === 1) return 'warning'
  if (status === 2) return 'primary'
  if (status === 3) return 'success'
  return 'info'
}

const loadWorkOrders = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    if (statusFilter.value !== '' && statusFilter.value != null) params.orderStatus = statusFilter.value
    const res = await listWorkOrders(params)
    workOrderList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    workOrderList.value = []
  } finally {
    loading.value = false
  }
}

const searchWorkOrders = () => {
  pageNum.value = 1
  loadWorkOrders()
}

/** 清空工单号、内容和状态筛选 */
const resetSearch = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  searchWorkOrders()
}

const resetForm = () => {
  form.value = { id: '', orderType: '咨询', orderContent: '', sessionId: '', customerId: '', priority: '普通' }
}

/** 打开弹窗时刷新客户与会话，供下拉选择 */
const loadRefs = async () => {
  try {
    const [cRes, sRes] = await Promise.all([listCustomers(), listSessions()])
    customers.value = Array.isArray(cRes.data) ? cRes.data : (cRes.data?.records || [])
    sessions.value = Array.isArray(sRes.data) ? sRes.data : (sRes.data?.records || [])
  } catch {
    customers.value = []
    sessions.value = []
  }
}

const openCreateDialog = () => {
  isEdit.value = false
  resetForm()
  loadRefs()
  showCreateDialog.value = true
}

const editWorkOrder = (row) => {
  isEdit.value = true
  form.value = { ...row, customerId: row.customerId ? String(row.customerId) : '', priority: '普通' }
  loadRefs()
  showCreateDialog.value = true
}

const saveWorkOrder = async () => {
  if (!form.value.orderType) {
    ElMessage.warning('请选择工单类型')
    return
  }
  if (!form.value.orderContent || !form.value.orderContent.trim()) {
    ElMessage.warning('请填写工单内容')
    return
  }
  saving.value = true
  try {
    let res
    if (isEdit.value) {
      res = await updateWorkOrder(form.value)
    } else {
      res = await createWorkOrder({
        orderType: form.value.orderType,
        content: form.value.priority === '紧急'
          ? `【紧急】${form.value.orderContent}`
          : form.value.orderContent,
        sessionId: form.value.sessionId || undefined,
        customerId: form.value.customerId ? Number(form.value.customerId) : undefined
      })
    }
    ElMessage.success(res?.data || (isEdit.value ? '修改成功' : '创建成功'))
    showCreateDialog.value = false
    resetForm()
    isEdit.value = false
    loadWorkOrders()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const handleComplete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要标记该工单为完成吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await completeWorkOrder(id)
    ElMessage.success(res?.data || '工单已完成')
    loadWorkOrders()
  } catch {
    /* 拦截器已提示 */
  }
}

const handleClose = async (id) => {
  try {
    await ElMessageBox.confirm('关闭后工单不再流转，确定关闭？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await closeWorkOrder(id)
    ElMessage.success(res?.data || '工单已关闭')
    loadWorkOrders()
  } catch {
    /* 拦截器已提示 */
  }
}

const openAssign = async (row) => {
  assignRow.value = row
  assignAgentId.value = row.agentId || null
  assignVisible.value = true
  try {
    const res = await listAgents()
    agents.value = res.data || []
  } catch {
    agents.value = []
  }
}

const submitAssign = async () => {
  if (!assignAgentId.value) {
    ElMessage.warning('请选择坐席')
    return
  }
  await assignWorkOrder(assignRow.value.id, assignAgentId.value)
  ElMessage.success('分配成功')
  assignVisible.value = false
  loadWorkOrders()
}

/** 相似工单用弹窗列表展示，不占用右侧抽屉 */
const showSimilar = async (row) => {
  similarVisible.value = true
  try {
    const res = await similarWorkOrders(row.id)
    similarList.value = res.data || []
  } catch {
    similarList.value = []
  }
}

const handleClassify = async (id) => {
  const res = await autoClassifyWorkOrder(id)
  ElMessage.success(res?.data || '已自动分类')
  loadWorkOrders()
}

/** 详情：拉不到就退回行数据，避免弹窗空白 */
const detailVisible = ref(false)
const detail = ref(null)

const showDetail = async (row) => {
  try {
    const res = await getWorkOrderDetail(row.id)
    detail.value = res.data
  } catch {
    detail.value = { ...row }
  }
  detailVisible.value = true
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个工单吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteWorkOrder(id)
    ElMessage.success('删除成功')
    loadWorkOrders()
  } catch {
    /* 拦截器已提示 */
  }
}

/** 按勾选行逐条删除 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 条工单吗？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await deleteWorkOrder(row.id)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已删除 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('删除失败')
    selectedRows.value = []
    loadWorkOrders()
  } finally {
    batchRemoving.value = false
  }
}

/** 按勾选行逐条标记完成 */
const batchComplete = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定将选中的 ${rows.length} 条工单标记为完成吗？`, '批量完成', { type: 'warning' })
  } catch {
    return
  }
  batchCompleting.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await completeWorkOrder(row.id)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已完成 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('操作失败')
    selectedRows.value = []
    loadWorkOrders()
  } finally {
    batchCompleting.value = false
  }
}

/** 按勾选行逐条关闭 */
const batchClose = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定关闭选中的 ${rows.length} 条工单吗？关闭后不再流转。`, '批量关闭', { type: 'warning' })
  } catch {
    return
  }
  batchClosing.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await closeWorkOrder(row.id)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已关闭 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('操作失败')
    selectedRows.value = []
    loadWorkOrders()
  } finally {
    batchClosing.value = false
  }
}

/** 按弹窗勾选的范围和字段写出 CSV */
const doExport = async ({ scope, keys }) => {
  const cols = exportFields.filter((f) => keys.includes(f.key))
  if (!cols.length) {
    ElMessage.warning('请至少勾选一个字段')
    return
  }
  try {
    let list = []
    if (scope === 'selected') {
      list = selectedRows.value
    } else if (scope === 'page') {
      list = workOrderList.value
    } else {
      const params = { pageNum: 1, pageSize: 9999 }
      if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
      if (statusFilter.value !== '' && statusFilter.value != null) params.orderStatus = statusFilter.value
      const res = await listWorkOrders(params)
      list = res.data?.records || []
    }
    if (!list.length) {
      ElMessage.warning('没有可导出的工单')
      return
    }
    const stamp = new Date().toISOString().slice(0, 10)
    downloadTextFile(`工单列表_${stamp}.csv`, rowsToCsv(list, cols))
    ElMessage.success(`已导出 ${list.length} 条`)
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(loadWorkOrders)
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 640px) {
  .toolbar-actions {
    width: 100%;
  }
  .toolbar-actions .el-input,
  .toolbar-actions .el-select {
    width: 100% !important;
  }
  .toolbar-actions .el-button {
    flex: 1;
  }
}
</style>
