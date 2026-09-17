<template>
  <div class="page-card">
    <p class="hint">注册上游模型并切换当前对话模型。</p>
    <!-- 顶栏切片已标明本页，不再重复「模型管理」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-right">
        <div class="current-model">
          <span class="cm-label">当前对话模型</span>
          <el-select :model-value="currentActiveId" placeholder="未设置生效模型" clearable style="width: 240px"
            @change="onQuickSwitch">
            <el-option v-for="m in llmEnabled" :key="m.id" :label="m.modelName" :value="m.id" />
          </el-select>
        </div>
        <el-button v-if="mainTab === 'models'" type="primary" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchSetActive">批量生效</el-button>
        <el-button v-if="mainTab === 'models'" type="primary" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchTest">批量测试</el-button>
        <TbBtn v-if="mainTab === 'models'" act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deleteAiModel, '模型', loadModels)" />
        <TbBtn act="add" label="注册模型" @click="openDialog()" />
      </div>
    </div>

    <SectionSwitch v-model="mainTab" :options="mainTabOptions" />

    <template v-if="mainTab === 'models'">
      <SectionSwitch v-model="activeType" :options="typeFilterOptions" @change="loadModels" />

    <el-table :data="modelRecords" stripe v-loading="loading" empty-text="暂无模型，请先注册" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="modelName" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="能力" min-width="88">
        <template #default="{ row }">
          <el-tag>{{ typeLabel(row.modelType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="供应方" min-width="160">
        <template #default="{ row }">
          <el-tag :type="providerType(row.provider)" effect="plain">{{ providerLabel(row.provider) }}</el-tag>
          <el-tag :type="billingTag(row).type" size="small" effect="dark" style="margin-left: 4px">{{ billingTag(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remoteModel" label="上游模型" min-width="120" show-overflow-tooltip />
      <el-table-column label="服务地址" min-width="160">
        <template #default="{ row }">
          <CellText title="服务地址" :text="row.baseUrl" />
        </template>
      </el-table-column>
      <el-table-column label="生效" min-width="76" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isActive === 1" type="success" size="small">生效中</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="健康" min-width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="healthType(row.health)" size="small">{{ healthLabel(row.health) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" min-width="64" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(v) => toggleEnabled(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="268">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="active" :disabled="row.isActive === 1 || row.enabled !== 1" @click="setActive(row)" />
            <TblAct act="ping" @click="test(row)" />
            <TblAct act="edit" @click="openDialog(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="modelPage" v-model:size="modelSize" :total="modelTotal" />
    </template>

    <template v-else>
        <div class="usage-bar">
          <el-select v-model="usageQuery.modelType" placeholder="能力类型" clearable style="width: 160px">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="usageQuery.success" placeholder="结果" clearable style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
          <FilterActions @search="searchUsage" @reset="resetUsage" />
        </div>
        <div class="usage-summary" v-if="usageSummary">
          <el-tag>总调用 {{ usageSummary.total }}</el-tag>
          <el-tag type="success">成功 {{ usageSummary.success }}</el-tag>
          <el-tag type="danger">失败 {{ usageSummary.fail }}</el-tag>
          <el-tag type="info">令牌 {{ usageSummary.totalTokens }}</el-tag>
          <el-tag type="warning">均耗时 {{ usageSummary.avgLatencyMs }} 毫秒</el-tag>
          <el-tag type="danger" v-if="usageSummary.totalCost != null">成本 ¥{{ usageSummary.totalCost }}</el-tag>
        </div>
        <el-table :data="usageList" stripe v-loading="usageLoading" empty-text="暂无用量记录" size="small" table-layout="fixed" max-height="420">
          <el-table-column prop="createTime" label="时间" min-width="160" />
          <el-table-column prop="modelName" label="模型" min-width="140" show-overflow-tooltip />
          <el-table-column label="能力" min-width="88">
            <template #default="{ row }">{{ typeLabel(row.modelType) }}</template>
          </el-table-column>
          <el-table-column prop="totalTokens" label="令牌数" min-width="88" align="right" />
          <el-table-column prop="latencyMs" label="耗时毫秒" min-width="96" align="right" />
          <el-table-column label="成本（元）" min-width="96" align="right">
            <template #default="{ row }">{{ row.cost != null ? row.cost : '—' }}</template>
          </el-table-column>
          <el-table-column label="结果" min-width="72" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="140">
            <template #default="{ row }">
              <CellText title="失败原因" :text="row.errorMsg" />
            </template>
          </el-table-column>
        </el-table>
        <TablePager v-model:page="usageQuery.page" v-model:size="usageQuery.size" :total="usageTotal" @change="loadUsage" />
    </template>

    <el-dialog :title="form.id ? '编辑模型' : '注册模型'" v-model="visible" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="显示名称" required>
          <el-input v-model="form.modelName" maxlength="100" placeholder="例如：本地对话模型 / 通义千问" />
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
          <p class="field-tip">{{ typeDesc(form.modelType) }}</p>
        </el-form-item>
        <el-form-item label="服务地址" required>
          <el-input v-model="form.baseUrl" placeholder="本地模型地址或在线兼容接口地址" />
        </el-form-item>
        <el-form-item label="接口密钥">
          <el-input v-model="form.apiKey" :placeholder="form.apiKey && form.apiKey.includes('*') ? '已加密（留空则保持不变）' : '在线模型必填；本地模型可留空'"
            show-password />
        </el-form-item>
        <el-form-item label="上游模型" required>
          <el-input v-model="form.remoteModel" placeholder="例如通义千问或本地模型名" />
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
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="超时毫秒">
              <el-tooltip content="单次调用超时，默认 20000" placement="top">
                <el-input-number v-model="form.timeoutMs" :min="1000" :max="120000" :step="1000" style="width: 100%" />
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重试次数">
              <el-tooltip content="同模型失败重试次数(0-5)；超时重试对付费模型可能重复计费" placement="top">
                <el-input-number v-model="form.maxRetries" :min="0" :max="5" style="width: 100%" />
              </el-tooltip>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="熔断阈值">
              <el-tooltip content="连续失败达到此次数即摘除，冷却 60s 后探测恢复" placement="top">
                <el-input-number v-model="form.failThreshold" :min="1" :max="10" style="width: 100%" />
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日令牌配额">
              <el-tooltip content="每日令牌上限，留空不限；超限自动顺延到其它候选（本地/免费模型即降级目标）" placement="top">
                <el-input-number v-model="form.dailyTokenLimit" :min="0" :step="100000" style="width: 100%" placeholder="不限" />
              </el-tooltip>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="日成本配额">
              <el-tooltip content="每日成本上限(元)，留空不限；达 80% 输出预警日志" placement="top">
                <el-input-number v-model="form.dailyCostLimit" :min="0" :precision="2" :step="10" style="width: 100%" placeholder="不限" />
              </el-tooltip>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="输入单价">
              <el-tooltip content="元/千令牌，用于成本估算，可留空" placement="top">
                <el-input-number v-model="form.costPer1kIn" :min="0" :precision="6" :step="0.001" style="width: 100%" />
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="输出单价">
              <el-tooltip content="元/千令牌，用于成本估算，可留空" placement="top">
                <el-input-number v-model="form.costPer1kOut" :min="0" :precision="6" :step="0.001" style="width: 100%" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAiModel, getAiModelActive, listAiModels, listAiModelsEnabled,
  saveAiModel, setAiModelActive, setAiModelEnabled, testAiModel,
  pageModelUsage, getModelUsageSummary
} from '../api'
import { useClientPager } from '../composables/useClientPager'
import { useBatchSelect } from '../composables/useBatchSelect'
import TablePager from '../components/TablePager.vue'
import SectionSwitch from '../components/SectionSwitch.vue'

const modelList = ref([])
const { page: modelPage, size: modelSize, total: modelTotal, records: modelRecords } = useClientPager(modelList)
const { selectedRows, batchRemoving, onSelect, batchRemove, batchAct } = useBatchSelect()
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const activeType = ref('ALL')
const mainTab = ref('models')
const mainTabOptions = [
  { value: 'models', label: '模型列表' },
  { value: 'usage', label: '用量统计' }
]
const llmEnabled = ref([])
const currentActiveId = ref(null)

const usageList = ref([])
const usageLoading = ref(false)
const usageTotal = ref(0)
const usageSummary = ref(null)
const usageQuery = reactive({ page: 1, size: 10, modelType: '', success: null })

const providerOptions = [
  { value: 'OLLAMA', label: '本地模型' },
  { value: 'DASHSCOPE', label: '通义千问' },
  { value: 'OPENAI', label: '通用兼容接口' },
  { value: 'DEEPSEEK', label: '深度求索' },
  { value: 'OTHER', label: '其他' }
]
/** 能力类型：desc 会实时显示在表单下方，省得选的人靠猜 */
const typeOptions = [
  { value: 'LLM', label: '对话', desc: '理解并生成回答。客服机器人的主模型，一套配置里必须且只有一个生效。' },
  { value: 'EMBEDDING', label: '向量', desc: '把文本转成向量做语义检索，知识库召回靠它。换模型或换维度需重建全部向量。' },
  { value: 'RERANK', label: '重排', desc: '对检索回来的候选做二次精排，把最相关的排前面。非必需，但召准率提升明显。' },
  { value: 'VISION', label: '视觉', desc: '识别图片内容，用于用户发截图、票据、商品图时的理解。' },
  { value: 'MULTIMODAL', label: '多模态', desc: '图文混合输入输出，一个模型兼做对话与识图，可省一次调用。' }
]
const typeDesc = (v) => typeOptions.find((t) => t.value === v)?.desc || ''
const typeFilterOptions = [{ value: 'ALL', label: '全部' }, ...typeOptions]

const emptyForm = () => ({
  id: null, modelName: '', provider: 'OLLAMA', modelType: 'LLM', baseUrl: '',
  apiKey: '', apiSecret: '', remoteModel: '', temperature: 0.3, dimension: 1024,
  priority: 0, timeoutMs: 20000, maxRetries: 1, failThreshold: 2,
  dailyTokenLimit: null, dailyCostLimit: null, costPer1kIn: null, costPer1kOut: null,
  enabled: 1, isActive: 0, health: 'UNKNOWN', remark: ''
})
const form = reactive(emptyForm())

const providerLabel = (v) => providerOptions.find((p) => p.value === v)?.label || v
const providerType = (v) => ({ OLLAMA: 'warning', DASHSCOPE: 'success', OPENAI: 'primary', DEEPSEEK: 'danger', OTHER: 'info' }[v] || 'info')
const typeLabel = (v) => typeOptions.find((t) => t.value === v)?.label || v
const healthLabel = (v) => ({ UNKNOWN: '未知', HEALTHY: '健康', DOWN: '故障' }[v] || v)
const healthType = (v) => ({ UNKNOWN: 'info', HEALTHY: 'success', DOWN: 'danger' }[v] || 'info')
/** 本地/免费 与 按量付费 标识：单价为空即免费（本地 Ollama 或自建） */
const billingTag = (row) => {
  const metered = row.costPer1kIn != null || row.costPer1kOut != null
  return metered ? { label: '按量', type: 'warning' } : { label: '免费', type: 'success' }
}

const loadModels = async () => {
  loading.value = true
  try {
    const res = await listAiModels()
    const all = res.data || []
    modelList.value = activeType.value && activeType.value !== 'ALL'
      ? all.filter((m) => m.modelType === activeType.value)
      : all
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
  const target = llmEnabled.value.find((m) => m.id === id)
  const name = target?.modelName || '该模型'
  try {
    await ElMessageBox.confirm(`确定将「${name}」设为当前对话模型？`, '确认生效', { type: 'warning' })
  } catch {
    refreshQuickSwitch()
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
  if (form.timeoutMs == null) form.timeoutMs = 20000
  if (form.maxRetries == null) form.maxRetries = 1
  if (form.failThreshold == null) form.failThreshold = 2
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
  try {
    await ElMessageBox.confirm(`确定将「${row.modelName}」设为当前生效模型？`, '确认生效', { type: 'warning' })
  } catch {
    return
  }
  await setAiModelActive(row.id)
  ElMessage.success(`已将「${row.modelName}」设为生效`)
  loadModels()
}

/** 勾选后批量生效：同时只能生效一个，取第一条已启用且尚未生效的 */
const batchSetActive = () => batchAct(
  (row) => setAiModelActive(row.id),
  {
    noun: '模型',
    verb: '设为生效',
    title: '确认生效',
    reload: loadModels,
    filter: (row) => row.enabled === 1 && row.isActive !== 1,
    pickOne: true,
    labelOf: (row) => row.modelName
  }
)

const toggleEnabled = async (row, v) => {
  await setAiModelEnabled(row.id, !!v)
  ElMessage.success(v ? '已启用' : '已停用')
  loadModels()
}

/** 探测连通性，skipConfirm 给批量测试复用（外层已确认过） */
const pingModel = async (row) => {
  const res = await testAiModel(row.id)
  return res?.data || '连接成功'
}

const test = async (row) => {
  try {
    await ElMessageBox.confirm(`确定测试模型「${row.modelName}」的连通性？`, '确认测试', { type: 'warning' })
  } catch {
    return
  }
  try {
    const msg = await pingModel(row)
    ElMessage.success(msg)
  } catch {
    ElMessage.error('连接失败')
  }
  loadModels()
}

/** 对勾选模型逐条测通 */
const batchTest = () => batchAct(
  pingModel,
  { noun: '模型', verb: '测试', title: '确认测试', reload: loadModels, labelOf: (row) => row.modelName }
)

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除模型「${row.modelName}」吗？`, '提示', { type: 'warning' })
  await deleteAiModel(row.id)
  ElMessage.success('删除成功')
  loadModels()
}

const loadUsage = async () => {
  usageLoading.value = true
  try {
    const params = { page: usageQuery.page, size: usageQuery.size }
    if (usageQuery.modelType) params.modelType = usageQuery.modelType
    if (usageQuery.success !== null && usageQuery.success !== undefined && usageQuery.success !== '') params.success = usageQuery.success
    const res = await pageModelUsage(params)
    usageList.value = res.data?.records || []
    usageTotal.value = res.data?.total || 0
    const sum = await getModelUsageSummary({ modelType: usageQuery.modelType || undefined })
    usageSummary.value = sum.data
  } catch {
    usageList.value = []
    usageTotal.value = 0
  } finally {
    usageLoading.value = false
  }
}

const searchUsage = () => {
  usageQuery.page = 1
  loadUsage()
}

/** 清空能力类型和结果筛选 */
const resetUsage = () => {
  usageQuery.modelType = ''
  usageQuery.success = null
  searchUsage()
}

onMounted(() => {
  loadModels()
  loadUsage()
})
</script>

<style scoped>
/* 能力类型下方的解释行，选完立刻知道这个类型干什么 */
.field-tip {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: #667085;
}
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
.usage-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.usage-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
@media (max-width: 640px) {
  .page-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .toolbar-right {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    width: 100%;
  }
  .current-model .el-select {
    width: 100% !important;
  }
  .usage-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 10px;
  }
  .usage-summary {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 10px;
  }
  :deep(.el-table) {
    font-size: 13px;
  }
}
</style>
