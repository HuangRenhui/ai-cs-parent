<template>
  <div class="customer-container">
    <div class="header">
      <h2>客户管理</h2>
      <el-button type="primary" @click="showAddDialog = true">新增客户</el-button>
    </div>

    <el-table :data="customerList" border>
      <el-table-column prop="id" label="ID" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="avatar" label="头像">
        <template #default="scope">
          <img :src="scope.row.avatar" alt="头像" width="40" height="40" />
        </template>
      </el-table-column>
      <el-table-column prop="customerTag" label="标签" />
      <el-table-column prop="createTime" label="创建时间" />
    </el-table>

    <!-- 添加客户弹窗 -->
    <el-dialog title="新增客户" v-model="showAddDialog">
      <el-form :model="form" label-width="80px">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="头像">
          <el-input v-model="form.avatar" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.customerTag" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCustomer">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'

const customerList = ref([])
const showAddDialog = ref(false)
const form = ref({
  phone: '',
  nickname: '',
  avatar: '',
  customerTag: ''
})

const loadCustomers = async () => {
  try {
    const response = await request.get('/customer/list')
    customerList.value = response.data
  } catch (error) {
    console.error('获取客户列表失败:', error)
  }
}

const saveCustomer = async () => {
  try {
    await request.post('/customer/save', form.value)
    showAddDialog.value = false
    form.value = { phone: '', nickname: '', avatar: '', customerTag: '' }
    loadCustomers()
    alert('保存成功')
  } catch (error) {
    console.error('保存客户失败:', error)
  }
}

onMounted(loadCustomers)
</script>

<style scoped>
.customer-container {
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