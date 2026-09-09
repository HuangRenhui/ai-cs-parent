<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>意图配置</h3>
      <el-button type="primary" @click="openDialog()">新增意图</el-button>
    </div>

    <el-alert type="info" :closable="false" style="margin: 8px 0 12px"
      title="意图识别已配置化：可定义租户自定义意图，LLM 按配置识别并路由，缺省回退内置意图枚举" />

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无意图配置">
      <el-table-column prop="intentCode" label="意图编码" min-width="120" />
      <el-table-column prop="intentName" label="意图名称" min-width="120" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.intentType)" size="small">{{ typeLabel(row.intentType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关键词" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ parseArr(row.keywords).join('、') || '—' }}</template>
      </el-table-column>
      <el-table-column label="绑定工具" min-width="110">
        <template #default="{ row }">{{ row.toolBind || '—' }}</template>
      </el-table-column>
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

    <el-dialog :title="form.id ? '编辑意图' : '新增意图'" v-model="visible" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="意图编码" required>
          <el-input v-model="form.intentCode" maxlength="64" placeholder="例如 order_complaint" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="意图名称" required>
          <el-input v-model="form.intentName" maxlength="64" placeholder="例如 订单投诉" />
        </el-form-item>
        <el-form-item label="意图类型" required>
          <el-select v-model="form.intentType" style="width: 100%">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" maxlength="255" type="textarea" :rows="2" placeholder="意图的补充说明，可空" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-select v-model="keywordList" multiple filterable allow-create default-first-option style="width: 100%"
            placeholder="输入后回车添加关键词">
            <el-option v-for="k in keywordList" :key="k" :label="k" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="示例话术">
          <el-select v-model="exampleList" multiple filterable allow-create default-first-option style="width: 100%"
            placeholder="输入后回车添加示例话术">
            <el-option v-for="e in exampleList" :key="e" :label="e" :value="e" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定工具">
          <el-input v-model="form.toolBind" maxlength="64" placeholder="命中该意图后调用的工具名称，可空" />
        </el-form-item>
        <el-form-item label="回复模板">
          <el-input v-model="form.responseTemplate" type="textarea" :rows="2" placeholder="命中该意图时的兜底回复，可空" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
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
import { listIntents, saveIntent, deleteIntent } from '../api'

const list = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const keywordList = ref([])
const exampleList = ref([])

const typeOptions = [
  { value: 'business', label: '业务' },
  { value: 'system', label: '系统' },
  { value: 'transfer', label: '转人工' }
]
const typeLabel = (v) => typeOptions.find((t) => t.value === v)?.label || v
const typeTag = (v) => ({ business: 'primary', system: 'warning', transfer: 'danger' }[v] || 'info')

const parseArr = (s) => {
  if (!s) return []
  try {
    const arr = JSON.parse(s)
    return Array.isArray(arr) ? arr : []
  } catch {
    return String(s).split(',').map((x) => x.trim()).filter(Boolean)
  }
}

const emptyForm = () => ({
  id: null, tenantCode: 'default', intentCode: '', intentName: '', intentType: 'business',
  description: '', keywords: '[]', examples: '[]', toolBind: '', responseTemplate: '',
  priority: 0, enabled: 1
})
const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const res = await listIntents('default')
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  Object.assign(form, emptyForm(), row || {})
  if (form.intentType == null) form.intentType = 'business'
  if (form.priority == null) form.priority = 0
  if (form.enabled == null) form.enabled = 1
  keywordList.value = parseArr(form.keywords)
  exampleList.value = parseArr(form.examples)
  visible.value = true
}

const submit = async () => {
  if (!form.intentCode || !form.intentName) {
    ElMessage.warning('请填写意图编码与名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      keywords: JSON.stringify(keywordList.value || []),
      examples: JSON.stringify(exampleList.value || [])
    }
    await saveIntent(payload)
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
  await ElMessageBox.confirm(`确定删除意图「${row.intentName}」吗？`, '提示', { type: 'warning' })
  await deleteIntent(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
