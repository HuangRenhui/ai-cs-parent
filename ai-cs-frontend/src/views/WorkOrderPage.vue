<template>
  <div class="workorder-container">
    <div class="header">
      <h2>工单管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">创建工单</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchKeyword" placeholder="搜索工单号或内容" style="width: 300px;" />
      <el-select v-model="statusFilter" placeholder="状态筛选">
        <el-option label="全部" value="" />
        <el-option label="待处理" value="待处理" />
        <el-option label="已完成" value="已完成" />
      </el-select>
      <el-button type="primary" @click="loadWorkOrders">搜索</el-button>
    </div>

    <el-table :data="workOrderList" border>
      <el-table-column prop="id" label="ID" />
      <el-table-column prop="orderNo" label="工单号" />
      <el-table-column prop="orderType" label="工单类型" />
      <el-table-column prop="content" label="工单内容" />
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column prop="updateTime" label="更新时间" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editWorkOrder(scope.row)">编辑</el-button>
          <el-button size="small" type="success" @click="completeWorkOrder(scope.row.id)" v-if="scope.row.status === '待处理'">完成</el-button>
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
          <el-textarea v-model="form.content" rows="4" />
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
import request from '../utils/request'

const workOrderList = ref([])
const showCreateDialog = ref(false)
const isEdit = ref(false)
const searchKeyword = ref('')
const statusFilter = ref('')
const form = ref({
  id: '',
  orderType: '',
  content: '',
  sessionId: '',
  customerId: ''
})

const loadWorkOrders = async () => {
  try {
    const params = {}
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (statusFilter.value) params.status = statusFilter.value
    const response = await request.get('/workorder/list', { params })
    workOrderList.value = response.data
  } catch (error) {
    console.error('获取工单列表失败:', error)
  }
}

const saveWorkOrder = async () => {
  try {
    if (isEdit.value) {
      await request.put('/workorder/update', form.value)
      alert('修改成功')
    } else {
      await request.post('/workorder/create', form.value)
      alert('创建成功')
    }
    showCreateDialog.value = false
    form.value = { id: '', orderType: '', content: '', sessionId: '', customerId: '' }
    isEdit.value = false
    loadWorkOrders()
  } catch (error) {
    console.error('保存工单失败:', error)
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
  return status === '待处理' ? 'warning' : 'success'
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
}
</style>