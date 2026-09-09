<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>数据保留策略</h3>
      <el-button type="primary" @click="openDialog()">新增策略</el-button>
    </div>

    <el-alert type="warning" :closable="false" style="margin: 8px 0 12px"
      title="出域与保留：配置会话/消息/知识/工具调用的保留天数、出域与用户删除权限、匿名化周期，满足数据合规" />

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无保留策略">
      <el-table-column label="数据类型" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ typeLabel(row.dataType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="保留天数" width="100" align="center">
        <template #default="{ row }">{{ row.retentionDays }} 天</template>
      </el-table-column>
      <el-table-column label="允许出域" width="90" align="center">
        <template #default="{ row }">{{ row.allowExternalDomain === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="用户可删除" width="100" align="center">
        <template #default="{ row }">{{ row.allowUserDelete === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="匿名化(天)" width="100" align="center">
        <template #default="{ row }">{{ row.anonymizeAfterDays ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="form.id ? '编辑策略' : '新增策略'" v-model="visible" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="数据类型" required>
          <el-select v-model="form.dataType" style="width: 100%" :disabled="!!form.id">
            <el-option v-for="t in dataTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="保留天数" required>
          <el-input-number v-model="form.retentionDays" :min="1" :max="3650" style="width: 100%" />
        </el-form-item>
        <el-form-item label="匿名化天数">
          <el-input-number v-model="form.anonymizeAfterDays" :min="0" :max="3650" style="width: 100%" placeholder="0 表示不匿名化" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="允许出域">
              <el-switch v-model="form.allowExternalDomain" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="用户可删除">
              <el-switch v-model="form.allowUserDelete" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="启用">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明">
          <el-input v-model="form.description" maxlength="255" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDataRetentions, saveDataRetention, deleteDataRetention } from '../api'

const list = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)

const dataTypes = [
  { value: 'session', label: '会话' },
  { value: 'message', label: '消息' },
  { value: 'knowledge', label: '知识库' },
  { value: 'tool_invoke', label: '工具调用' }
]
const typeLabel = (v) => dataTypes.find((t) => t.value === v)?.label || v

const emptyForm = () => ({
  id: null, tenantCode: 'default', dataType: 'session', retentionDays: 180,
  allowExternalDomain: 0, allowUserDelete: 0, anonymizeAfterDays: 0, description: '', status: 1
})
const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const res = await listDataRetentions('default')
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  Object.assign(form, emptyForm(), row || {})
  if (form.dataType == null) form.dataType = 'session'
  if (form.retentionDays == null) form.retentionDays = 180
  if (form.allowExternalDomain == null) form.allowExternalDomain = 0
  if (form.allowUserDelete == null) form.allowUserDelete = 0
  if (form.anonymizeAfterDays == null) form.anonymizeAfterDays = 0
  if (form.status == null) form.status = 1
  visible.value = true
}

const submit = async () => {
  if (!form.dataType || form.retentionDays == null) {
    ElMessage.warning('请选择数据类型并填写保留天数')
    return
  }
  saving.value = true
  try {
    await saveDataRetention({ ...form })
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除「${typeLabel(row.dataType)}」的保留策略吗？`, '提示', { type: 'warning' })
  await deleteDataRetention(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
