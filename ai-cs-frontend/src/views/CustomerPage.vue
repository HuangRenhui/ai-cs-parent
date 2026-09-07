<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input v-model="keyword" placeholder="搜索手机号 / 邮箱 / 昵称 / 标签" clearable style="width: 300px" @keyup.enter="loadCustomers" />
      <div>
        <el-button @click="loadCustomers">查询</el-button>
        <el-button type="primary" @click="openDialog()">新增客户</el-button>
      </div>
    </div>
    <el-table :data="customerList" stripe v-loading="loading" empty-text="暂无客户">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="头像" width="80">
        <template #default="{ row }">
          <UserAvatar class="table-avatar" :url="row.avatar" alt="" />
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column label="性别" width="80">
        <template #default="{ row }">{{ genderLabel(row.gender) }}</template>
      </el-table-column>
      <el-table-column prop="customerTag" label="标签" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.customerTag">{{ row.customerTag }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="form.id ? '编辑客户' : '新增客户'" v-model="visible" width="520px">
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="80px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="11位大陆手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" maxlength="80" placeholder="选填，如 name@example.com" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="32" placeholder="选填，中文/字母/数字" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender" @change="onGenderChange">
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
            <el-radio :label="0">不选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="头像">
          <AvatarUpload v-model="form.avatar" :gender="form.gender" />
        </el-form-item>
        <el-form-item label="标签" prop="customerTag">
          <el-input v-model="form.customerTag" maxlength="20" placeholder="如 VIP / 普通" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteCustomer, listCustomers, saveCustomer, updateCustomer } from '../api'
import { rules } from '../utils/validate'
import { genderLabel, isSystemDefaultAvatar, pickRandomDefaultAvatar } from '../utils/avatar'
import AvatarUpload from '../components/AvatarUpload.vue'
import UserAvatar from '../components/UserAvatar.vue'

const customerList = ref([])
const keyword = ref('')
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({ id: null, phone: '', email: '', nickname: '', gender: 0, avatar: '', customerTag: '' })
const formRules = {
  phone: rules.requiredMobile,
  email: rules.optionalEmail,
  nickname: rules.optionalNickname,
  customerTag: rules.optionalTag
}

const resetForm = () => {
  form.id = null
  form.phone = ''
  form.email = ''
  form.nickname = ''
  form.gender = 0
  form.avatar = ''
  form.customerTag = ''
}

const onGenderChange = (gender) => {
  if (isSystemDefaultAvatar(form.avatar)) {
    form.avatar = pickRandomDefaultAvatar(gender)
  }
}

const loadCustomers = async () => {
  loading.value = true
  try {
    const res = await listCustomers(keyword.value)
    customerList.value = res.data || []
  } catch {
    customerList.value = []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  resetForm()
  if (row) {
    Object.assign(form, row)
    if (form.gender == null) form.gender = 0
  }
  visible.value = true
}

const submit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateCustomer({ ...form })
      ElMessage.success('修改成功')
    } else {
      await saveCustomer({ ...form, id: undefined })
      ElMessage.success('保存成功')
    }
    visible.value = false
    loadCustomers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const remove = async (id) => {
  await ElMessageBox.confirm('确定删除该客户吗？', '提示', { type: 'warning' })
  await deleteCustomer(id)
  ElMessage.success('删除成功')
  loadCustomers()
}

onMounted(loadCustomers)
</script>

<style scoped>
.table-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; background: #e8edf5; }
@media (max-width: 640px) {
  .page-toolbar > .el-input {
    width: 100% !important;
  }
  .page-toolbar > div {
    display: flex;
    gap: 8px;
    width: 100%;
  }
}
</style>
