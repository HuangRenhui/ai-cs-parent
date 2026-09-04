<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>坐席管理</h3>
      <el-button type="primary" @click="openDialog()">新增坐席</el-button>
    </div>
    <el-table :data="agentList" stripe v-loading="loading" empty-text="暂无坐席">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="agentAccount" label="账号" />
      <el-table-column prop="agentName" label="姓名" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.agentStatus)">{{ statusLabel(row.agentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="setStatus(row, 1)">上线</el-button>
          <el-button size="small" @click="setStatus(row, 2)">忙碌</el-button>
          <el-button size="small" @click="setStatus(row, 0)">离线</el-button>
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="form.id ? '编辑坐席' : '新增坐席'" v-model="visible" width="480px">
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="80px">
        <el-form-item label="账号" prop="agentAccount">
          <el-input v-model="form.agentAccount" maxlength="32" placeholder="字母开头，4-32 位" />
        </el-form-item>
        <el-form-item label="姓名" prop="agentName">
          <el-input v-model="form.agentName" maxlength="32" />
        </el-form-item>
        <el-form-item label="密码" prop="agentPwd">
          <el-input v-model="form.agentPwd" type="password" maxlength="32" placeholder="6-32 位，需含字母和数字；编辑可留空" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.agentStatus">
            <el-option label="离线" :value="0" />
            <el-option label="在线" :value="1" />
            <el-option label="忙碌" :value="2" />
          </el-select>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteAgent, listAgents, saveAgent, updateAgent, updateAgentStatus } from '../api'
import { rules } from '../utils/validate'

const agentList = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({ id: null, agentAccount: '', agentName: '', agentPwd: '', agentStatus: 1 })
const formRules = computed(() => ({
  agentAccount: rules.requiredAccount,
  agentName: rules.requiredNickname,
  agentPwd: rules.optionalPassword(!form.id)
}))

const statusLabel = (status) => ({ 0: '离线', 1: '在线', 2: '忙碌' }[status] || '未知')
const statusType = (status) => ({ 0: 'info', 1: 'success', 2: 'warning' }[status] || 'info')

const loadAgents = async () => {
  loading.value = true
  try {
    const res = await listAgents()
    agentList.value = res.data || []
  } catch {
    agentList.value = []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  Object.assign(form, row || { id: null, agentAccount: '', agentName: '', agentPwd: '', agentStatus: 1 })
  if (!row) form.agentPwd = 'Agent123'
  else form.agentPwd = ''
  visible.value = true
}

const submit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateAgent({ ...form })
      ElMessage.success('修改成功')
    } else {
      await saveAgent({ ...form, id: undefined })
      ElMessage.success('新增成功')
    }
    visible.value = false
    loadAgents()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const setStatus = async (row, agentStatus) => {
  await updateAgentStatus(row.id, agentStatus)
  ElMessage.success('状态已更新')
  loadAgents()
}

const remove = async (id) => {
  await ElMessageBox.confirm('确定删除该坐席吗？', '提示', { type: 'warning' })
  await deleteAgent(id)
  ElMessage.success('删除成功')
  loadAgents()
}

onMounted(loadAgents)
</script>
