<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>AI 模型管理</h3>
      <div class="toolbar-right">
        <div class="current-model">
          <span class="cm-label">当前对话模型</span>
          <el-select :model-value="currentActiveId" placeholder="未设置生效模型" clearable style="width: 240px"
            @change="onQuickSwitch">
            <el-option v-for="m in llmEnabled" :key="m.id" :label="m.modelName" :value="m.id" />
          </el-select>
        </div>
        <el-button type="primary" @click="openDialog()">注册模型</el-button>
      </div>
    </div>

    <el-tabs v-model="activeType" @tab-change="loadModels">
      <el-tab-pane label="全部" name="" />
      <el-tab-pane v-for="t in typeOptions" :key="t.value" :label="t.label" :name="t.value" />
    </el-tabs>

    <el-table :data="modelList" stripe v-loading="loading" empty-text="暂无模型，请先注册">
      <el-table-column prop="modelName" label="名称" min-width="130" />
      <el-table-column label="能力" width="110">
        <template #default="{ row }">
          <el-tag>{{ typeLabel(row.modelType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="供应方" width="120">
        <template #default="{ row }">
          <el-tag :type="providerType(row.provider)" effect="plain">{{ providerLabel(row.provider) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remoteModel" label="上游模型" min-width="120" />
      <el-table-column prop="baseUrl" label="服务地址" min-width="200" show-overflow-tooltip />
      <el-table-column label="生效" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isActive === 1" type="success" size="small">生效中</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="健康" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="healthType(row.health)" size="small">{{ healthLabel(row.health) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(v) => toggleEnabled(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <el-button size="small" type="success" :disabled="row.isActive === 1 || row.enabled !== 1"
            @click="setActive(row)">设为生效</el-button>
          <el-button size="small" @click="test(row)">测试</el-button>
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="form.id ? '编辑模型' : '注册模型'" v-model="visible" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="显示名称" required>
          <el-input v-model="form.modelName" maxlength="100" placeholder="例如：本地 Llama3 / 通义千问" />
        </el-form-item>
        <el-form-item label="供应方" required>
          <el-select v-model="form.provider" style="width: 100%">
            <el-option v-for="p in providerOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="能力类型" required>
          <el-select v-model="form.modelType" style="width: 100%">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务地址" required>
          <el-input v-model="form.baseUrl" placeholder="本地 Ollama：http://localhost:11434/v1；在线：compatible-mode/v1 地址" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" :placeholder="form.apiKey && form.apiKey.includes('*') ? '已加密(留空则保持不变)' : '在线模型必填；Ollama 可留空'"
            show-password />
        </el-form-item>
        <el-form-item label="上游模型" required>
          <el-input v-model="form.remoteModel" placeholder="例如 llama3、qwen-plus" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="温度">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-tooltip content="故障切换顺序，越小越优先" placement="top">
                <el-input-number v-model="form.priority" :min="0" :max="100" style="width: 100%" />
              </el-tooltip>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="向量维度" v-if="form.modelType === 'EMBEDDING'">
          <el-input-number v-model="form.dimension" :min="64" :step="64" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="500" type="textarea" :rows="2" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAiModel, getAiModelActive, listAiModels, listAiModelsEnabled,
  saveAiModel, setAiModelActive, setAiModelEnabled, testAiModel
} from '../api'

const modelList = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const activeType = ref('')
const llmEnabled = ref([])
const currentActiveId = ref(null)

const providerOptions = [
  { value: 'OLLAMA', label: 'OLLAMA（本地）' },
  { value: 'DASHSCOPE', label: 'DashScope（通义千问）' },
  { value: 'OPENAI', label: 'OpenAI 兼容' },
  { value: 'DEEPSEEK', label: 'DeepSeek' },
  { value: 'OTHER', label: '其他' }
]
const typeOptions = [
  { value: 'LLM', label: 'LLM 对话' },
  { value: 'EMBEDDING', label: 'EMBEDDING 向量' },
  { value: 'RERANK', label: 'RERANK 重排' },
  { value: 'VISION', label: 'VISION 视觉' },
  { value: 'MULTIMODAL', label: 'MULTIMODAL 多模态' }
]

const emptyForm = () => ({
  id: null, modelName: '', provider: 'OLLAMA', modelType: 'LLM', baseUrl: '',
  apiKey: '', apiSecret: '', remoteModel: '', temperature: 0.3, dimension: 1024,
  priority: 0, enabled: 1, isActive: 0, health: 'UNKNOWN', remark: ''
})
const form = reactive(emptyForm())

const providerLabel = (v) => providerOptions.find((p) => p.value === v)?.label || v
const providerType = (v) => ({ OLLAMA: 'warning', DASHSCOPE: 'success', OPENAI: 'primary', DEEPSEEK: 'danger', OTHER: 'info' }[v] || 'info')
const typeLabel = (v) => typeOptions.find((t) => t.value === v)?.label || v
const healthLabel = (v) => ({ UNKNOWN: '未知', HEALTHY: '健康', DOWN: '故障' }[v] || v)
const healthType = (v) => ({ UNKNOWN: 'info', HEALTHY: 'success', DOWN: 'danger' }[v] || 'info')

const loadModels = async () => {
  loading.value = true
  try {
    const res = await listAiModels()
    const all = res.data || []
    modelList.value = activeType.value ? all.filter((m) => m.modelType === activeType.value) : all
    refreshQuickSwitch()
  } catch {
    modelList.value = []
  } finally {
    loading.value = false
  }
}

const refreshQuickSwitch = async () => {
  try {
    const en = await listAiModelsEnabled('LLM')
    llmEnabled.value = (en.data || []).filter((m) => m.enabled === 1)
    const act = await getAiModelActive('LLM')
    currentActiveId.value = act?.data?.id ?? null
  } catch {
    llmEnabled.value = []
    currentActiveId.value = null
  }
}

const onQuickSwitch = async (id) => {
  if (!id) {
    loadModels()
    return
  }
  try {
    await setAiModelActive(id)
    ElMessage.success('已切换当前对话模型')
    loadModels()
  } catch {
    refreshQuickSwitch()
  }
}

const openDialog = (row) => {
  Object.assign(form, emptyForm(), row ? { ...row, apiKey: row.apiKey && row.apiKey.includes('*') ? row.apiKey : (row.apiKey || '') } : {})
  if (!form.provider) form.provider = 'OLLAMA'
  if (!form.modelType) form.modelType = 'LLM'
  if (form.temperature == null) form.temperature = 0.3
  if (form.priority == null) form.priority = 0
  if (form.dimension == null) form.dimension = 1024
  if (form.enabled == null) form.enabled = 1
  visible.value = true
}

const submit = async () => {
  if (!form.modelName || !form.provider || !form.modelType || !form.remoteModel) {
    ElMessage.warning('请填写名称、供应方、能力类型与上游模型')
    return
  }
  saving.value = true
  try {
    // 密钥已脱敏(带*)，编辑未改则传空由后端保持不变
    const payload = { ...form }
    if (form.apiKey && String(form.apiKey).includes('*')) payload.apiKey = undefined
    if (form.id) {
      await saveAiModel(payload)
      ElMessage.success('保存成功')
    } else {
      await saveAiModel({ ...payload, id: undefined, isActive: undefined, health: undefined })
      ElMessage.success('注册成功')
    }
    visible.value = false
    loadModels()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const setActive = async (row) => {
  await setAiModelActive(row.id)
  ElMessage.success(`已将「${row.modelName}」设为生效`)
  loadModels()
}

const toggleEnabled = async (row, v) => {
  await setAiModelEnabled(row.id, !!v)
  ElMessage.success(v ? '已启用' : '已停用')
  loadModels()
}

const test = async (row) => {
  try {
    const res = await testAiModel(row.id)
    ElMessage.success(res?.data || '连接成功')
  } catch {
    ElMessage.error('连接失败')
  }
  loadModels()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除模型「${row.modelName}」吗？`, '提示', { type: 'warning' })
  await deleteAiModel(row.id)
  ElMessage.success('删除成功')
  loadModels()
}

onMounted(loadModels)
</script>

<style scoped>
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.current-model {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cm-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}
.muted {
  color: #c0c4cc;
}
</style>
