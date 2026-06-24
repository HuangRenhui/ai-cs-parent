<template>
  <div class="customer-container">
    <div class="header">
      <h2>客户管理</h2>
      <el-button type="primary" @click="showAddDialog = true">新增客户</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchKeyword" placeholder="搜索手机号或昵称" style="width: 300px;" />
      <el-button type="primary" @click="loadCustomers">搜索</el-button>
    </div>

    <el-table :data="customerList" border>
      <el-table-column prop="id" label="ID" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="avatar" label="头像">
        <template #default="scope">
          <img :src="scope.row.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" alt="头像" width="40" height="40" class="avatar-img" />
        </template>
      </el-table-column>
      <el-table-column prop="customerTag" label="标签" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editCustomer(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteCustomer(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加/编辑客户弹窗 -->
    <el-dialog :title="isEdit ? '编辑客户' : '新增客户'" v-model="showAddDialog">
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
const isEdit = ref(false)
const searchKeyword = ref('')
const form = ref({
  id: '',
  phone: '',
  nickname: '',
  avatar: '',
  customerTag: ''
})

const loadCustomers = async () => {
  try {
    const params = {}
    if (searchKeyword.value) params.keyword = searchKeyword.value
    const response = await request.get('/customer/list', { params })
    customerList.value = response.data
  } catch (error) {
    console.error('获取客户列表失败:', error)
  }
}

const saveCustomer = async () => {
  try {
    if (isEdit.value) {
      await request.put('/customer/update', form.value)
      alert('修改成功')
    } else {
      await request.post('/customer/save', form.value)
      alert('保存成功')
    }
    showAddDialog.value = false
    form.value = { id: '', phone: '', nickname: '', avatar: '', customerTag: '' }
    isEdit.value = false
    loadCustomers()
  } catch (error) {
    console.error('保存客户失败:', error)
  }
}

const editCustomer = (row) => {
  isEdit.value = true
  form.value = { ...row }
  showAddDialog.value = true
}

const deleteCustomer = async (id) => {
  if (!confirm('确定要删除这个客户吗？')) return
  try {
    await request.delete(`/customer/delete/${id}`)
    loadCustomers()
    alert('删除成功')
  } catch (error) {
    console.error('删除客户失败:', error)
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
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
.avatar-img {
  border-radius: 50%;
}
</style>