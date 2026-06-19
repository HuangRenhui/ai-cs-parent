<template>
  <div class="workorder-container">
    <div class="header">
      <h2>工单管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">创建工单</el-button>
    </div>

    <el-table :data="workOrderList" border>
      <el-table-column prop="id" label="ID" />
      <el-table-column prop="orderNo" label="工单号" />
      <el-table-column prop="orderType" label="工单类型" />
      <el-table-column prop="content" label="工单内容" />
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <el-tag :type="scope.row.status === '待处理' ? 'warning' : 'success'">
            {{ scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
    </el-table>

    <!-- 创建工单弹窗 -->
    <el-dialog title="创建工单" v-model="showCreateDialog">
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
        <el-button type="primary" @click="createWorkOrder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import request from '../utils/request'

const workOrderList = ref([])
const showCreateDialog = ref(false)
const form = ref({
  orderType: '',
  content: '',
  sessionId: '',
  customerId: ''
})

const createWorkOrder = async () => {
  try {
    const response = await request.post('/workorder/create', form.value)
    showCreateDialog.value = false
    form.value = { orderType: '', content: '', sessionId: '', customerId: '' }
    alert(response.data)
  } catch (error) {
    console.error('创建工单失败:', error)
  }
}
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
</style>