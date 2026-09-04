<template>
  <div class="workorder-container">
    <div class="header">
      <h2>工单管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">创建工单</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchKeyword" placeholder="搜索工单号或内容" class="search-input" />
      <el-select v-model="statusFilter" placeholder="状态筛选" class="search-select">
        <el-option label="全部" value="" />
        <el-option label="待处理" :value="1" />
        <el-option label="处理中" :value="2" />
        <el-option label="已完成" :value="3" />
        <el-option label="已关闭" :value="4" />
      </el-select>
      <el-button type="primary" @click="loadWorkOrders">搜索</el-button>
    </div>

    <el-table :data="workOrderList" border empty-text="暂无工单或加载失败">
      <el-table-column prop="id" label="ID" />
      <el-table-column prop="orderNo" label="工单号" />
      <el-table-column prop="orderType" label="工单类型" />
      <el-table-column prop="orderContent" label="工单内容" />
      <el-table-column prop="orderStatus" label="状态">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.orderStatus)">
            {{ statusLabel(scope.row.orderStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column prop="updateTime" label="更新时间" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editWorkOrder(scope.row)">编辑</el-button>
          <el-button size="small" type="success" @click="completeWorkOrder(scope.row.id)" v-if="scope.row.orderStatus === 1 || scope.row.orderStatus === 2">完成</el-button>
          <el-button size="small" type="danger" @click="deleteWorkOrder(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑工单弹窗 -->
    <el-dialog :title="isEdit ? '编辑工单' : '创建工单'" v-model="showCreateDialog">
      <el-form :model="form" label-width="80px">
        <el-form-item label="工单类型">
          <el-select v-model="form.orderType" placeholder="请选择">
            <el-option label="咨询" value="咨询" />
            <el-option label="投诉" value="投诉" />
            <el-option label="建议" value="建议" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单内容">
          <el-textarea v-model="form.orderContent" rows="4" />
        </el-form-item>
        <el-form-item label="会话ID">
          <el-input v-model="form.sessionId" />
        </el-form-item>
        <el-form-item label="客户ID">
          <el-input v-model="form.customerId" type="number" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveWorkOrder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { listWorkOrders } from '../api/index'

const workOrderList = ref([])
const showCreateDialog = ref(false)
const isEdit = ref(false)
const searchKeyword = ref('')
const statusFilter = ref('')
const form = ref({
  id: '',
  orderType: '',
  orderContent: '',
  sessionId: '',
  customerId: ''
})

const statusLabel = (code) => {
  return { 1: '待处理', 2: '处理中', 3: '已完成', 4: '已关闭' }[code] || code
}

const loadWorkOrders = async () => {
  try {
    const params = { pageNum: 1, pageSize: 50 }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (statusFilter.value !== '' && statusFilter.value != null) params.orderStatus = statusFilter.value
    const response = await listWorkOrders(params)
    workOrderList.value = response.data?.records || []
  } catch (error) {
    workOrderList.value = []
  }
}

const saveWorkOrder = async () => {
  try {
    if (isEdit.value) {
      await request.put('/workorder/update', form.value)
      alert('修改成功')
    } else {
      await request.post('/workorder/create', {
        orderType: form.value.orderType,
        content: form.value.orderContent,
        sessionId: form.value.sessionId,
        customerId: form.value.customerId
      })
      alert('创建成功')
    }
    showCreateDialog.value = false
    form.value = { id: '', orderType: '', orderContent: '', sessionId: '', customerId: '' }
    isEdit.value = false
    loadWorkOrders()
  } catch (error) {
    ElMessage.error(error?.msg || '保存工单失败')
  }
}

const editWorkOrder = (row) => {
  isEdit.value = true
  form.value = { ...row }
  showCreateDialog.value = true
}

const completeWorkOrder = async (id) => {
  if (!confirm('确定要标记为完成吗？')) return
  try {
    await request.put(`/workorder/complete/${id}`)
    loadWorkOrders()
    alert('已完成')
  } catch (error) {
    console.error('完成工单失败:', error)
  }
}

const deleteWorkOrder = async (id) => {
  if (!confirm('确定要删除这个工单吗？')) return
  try {
    await request.delete(`/workorder/delete/${id}`)
    loadWorkOrders()
    alert('删除成功')
  } catch (error) {
    console.error('删除工单失败:', error)
  }
}

const getStatusType = (status) => {
  if (status === 1) return 'warning'
  if (status === 2) return ''
  if (status === 3) return 'success'
  return 'info'
}

onMounted(loadWorkOrders)
</script>

<style scoped>
.workorder-container {
  padding: 16px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.header h2 {
  margin: 0;
}
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}
.search-input {
  width: 300px;
}
.search-select {
  width: 140px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .workorder-container {
    padding: 12px;
  }
  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .header h2 {
    font-size: 18px;
  }
  .search-bar {
    flex-direction: column;
    gap: 8px;
  }
  .search-input {
    width: 100%;
  }
  .search-select {
    width: 100%;
  }
  /* 表格横向滚动 */
  :deep(.el-table) {
    font-size: 13px;
  }
}
</style>