<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>工单管理</h3>
      <div class="toolbar-actions">
        <el-input v-model="searchKeyword" placeholder="搜索工单号或内容" clearable style="width: 200px" @keyup.enter="loadWorkOrders" />
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 130px">
          <el-option label="待处理" :value="1" />
          <el-option label="处理中" :value="2" />
          <el-option label="已完成" :value="3" />
          <el-option label="已关闭" :value="4" />
        </el-select>
        <el-button @click="loadWorkOrders">查询</el-button>
        <el-button type="primary" @click="openCreateDialog">创建工单</el-button>
      </div>
    </div>

    <el-table :data="workOrderList" stripe v-loading="loading" empty-text="暂无工单或加载失败">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="orderNo" label="工单号" min-width="160" show-overflow-tooltip />
      <el-table-column prop="orderType" label="工单类型" width="100" />
      <el-table-column prop="orderContent" label="工单内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.orderStatus)">
            {{ statusLabel(scope.row.orderStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="editWorkOrder(scope.row)">编辑</el-button>
          <el-button size="small" type="success" @click="handleComplete(scope.row.id)" v-if="scope.row.orderStatus === 1 || scope.row.orderStatus === 2">完成</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑工单弹窗 -->
    <el-dialog :title="isEdit ? '编辑工单' : '创建工单'" v-model="showCreateDialog" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="工单类型">
          <el-select v-model="form.orderType" placeholder="请选择">
            <el-option label="咨询" value="咨询" />
            <el-option label="投诉" value="投诉" />
            <el-option label="建议" value="建议" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单内容">
          <el-input v-model="form.orderContent" type="textarea" :rows="4" placeholder="请描述工单内容" />
        </el-form-item>
        <el-form-item label="会话ID">
          <el-input v-model="form.sessionId" placeholder="选填" />
        </el-form-item>
        <el-form-item label="客户ID">
          <el-input v-model="form.customerId" type="number" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveWorkOrder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeWorkOrder, createWorkOrder, deleteWorkOrder, listWorkOrders, updateWorkOrder } from '../api'

const workOrderList = ref([])
const showCreateDialog = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const searchKeyword = ref('')
const statusFilter = ref('')
const form = ref({ id: '', orderType: '', orderContent: '', sessionId: '', customerId: '' })

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
    const params = { pageNum: 1, pageSize: 50 }
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    if (statusFilter.value !== '' && statusFilter.value != null) params.orderStatus = statusFilter.value
    const res = await listWorkOrders(params)
    workOrderList.value = res.data?.records || []
  } catch {
    workOrderList.value = []
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.value = { id: '', orderType: '', orderContent: '', sessionId: '', customerId: '' }
}

const openCreateDialog = () => {
  isEdit.value = false
  resetForm()
  showCreateDialog.value = true
}

const editWorkOrder = (row) => {
  isEdit.value = true
  form.value = { ...row }
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
        content: form.value.orderContent,
        sessionId: form.value.sessionId || undefined,
        customerId: form.value.customerId || undefined
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
