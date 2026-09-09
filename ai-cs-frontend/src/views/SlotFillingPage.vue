<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>填槽配置</h3>
      <el-button type="primary" @click="openDialog()">新增槽位</el-button>
    </div>

    <el-alert type="info" :closable="false" style="margin: 8px 0 12px"
      title="多轮填槽：为意图配置必填槽位，缺槽时由 LLM 追问补齐，用于投诉建单等需要结构化信息的场景" />

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无槽位配置">
      <el-table-column prop="intentCode" label="关联意图" min-width="130" />
      <el-table-column prop="slotName" label="槽位名称" min-width="110" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ typeLabel(row.slotType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="必填" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.required === 1 ? 'danger' : 'info'" size="small">{{ row.required === 1 ? '必填' : '选填' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="promptTemplate" label="追问话术" min-width="180" show-overflow-tooltip />
      <el-table-column label="优先级" width="80" align="center">
        <template #default="{ row }">{{ row.priority ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="form.id ? '编辑槽位' : '新增槽位'" v-model="visible" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="关联意图" required>
          <el-select v-model="form.intentCode" style="width: 100%" placeholder="选择意图">
            <el-option v-for="i in intents" :key="i.intentCode" :label="`${i.intentName}(${i.intentCode})`" :value="i.intentCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="槽位名称" required>
          <el-input v-model="form.slotName" maxlength="64" placeholder="例如 订单号" />
        </el-form-item>
        <el-form-item label="槽位类型" required>
          <el-select v-model="form.slotType" style="width: 100%">
            <el-option v-for="t in slotTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="追问话术">
          <el-input v-model="form.promptTemplate" type="textarea" :rows="2" placeholder="缺槽时的追问，例如 请问您的订单号是多少？" />
        </el-form-item>
        <el-form-item label="提取提示词">
          <el-input v-model="form.extractPrompt" type="textarea" :rows="2" placeholder="从用户话术中提取槽位的提示词，可空" />
        </el-form-item>
        <el-form-item label="验证正则">
          <el-input v-model="form.validationRegex" placeholder="校验槽位值格式，例如 ^[A-Z0-9]{8,}$ 可空" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="必填">
              <el-switch v-model="form.required" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
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
import { listSlots, saveSlot, deleteSlot, listIntents } from '../api'

const list = ref([])
const intents = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)

const slotTypes = [
  { value: 'string', label: '文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'phone', label: '手机号' },
  { value: 'order_id', label: '订单号' }
]
const typeLabel = (v) => slotTypes.find((t) => t.value === v)?.label || v

const emptyForm = () => ({
  id: null, tenantCode: 'default', intentCode: '', slotName: '', slotType: 'string',
  required: 1, promptTemplate: '', validationRegex: '', extractPrompt: '',
  priority: 0, enabled: 1
})
const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const res = await listSlots('default')
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const loadIntents = async () => {
  try {
    const res = await listIntents('default')
    intents.value = (res.data || []).filter((i) => i.enabled === 1)
  } catch {
    intents.value = []
  }
}

const openDialog = (row) => {
  Object.assign(form, emptyForm(), row || {})
  if (form.slotType == null) form.slotType = 'string'
  if (form.required == null) form.required = 1
  if (form.priority == null) form.priority = 0
  if (form.enabled == null) form.enabled = 1
  visible.value = true
}

const submit = async () => {
  if (!form.intentCode || !form.slotName) {
    ElMessage.warning('请选择关联意图并填写槽位名称')
    return
  }
  saving.value = true
  try {
    await saveSlot({ ...form })
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
  await ElMessageBox.confirm(`确定删除槽位「${row.slotName}」吗？`, '提示', { type: 'warning' })
  await deleteSlot(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  load()
  loadIntents()
})
</script>
