<template>
  <div class="page-card">
    <p class="hint">配置问答系统的 Prompt 组成与 RAG 召回参数。</p>
    <!-- 顶栏切片已标明本页，不再重复「提示词与检索」标题 -->

    <el-tabs v-model="tab">
      <!-- ===================== 提示词模板 ===================== -->
      <el-tab-pane label="提示词模板" name="template">
        <el-alert
          v-if="!templateImplemented"
          type="warning"
          :closable="false"
          show-icon
          title="后端未完成"
          description="提示词模板为内存桩或假数据，保存后不会真正生效。"
          style="margin-bottom: 12px"
        />
        <div class="page-toolbar is-actions-only">
          <div class="table-actions">
            <TbBtn act="add" label="新建模板" @click="openTemplate()" />
            <el-button @click="saveAsTemplate">从当前配置另存</el-button>
            <el-button @click="loadTemplates">刷新</el-button>
          </div>
        </div>

        <el-table :data="templates" stripe v-loading="tplLoading" empty-text="暂无提示词模板" table-layout="fixed" max-height="560">
          <el-table-column label="模板" min-width="160">
            <template #default="{ row }">
              <span class="strong-text">{{ row.name }}</span>
              <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="plain" style="margin-left: 6px">默认</el-tag>
              <el-tag v-if="row.enabled === 0" size="small" type="warning" effect="plain" style="margin-left: 6px">停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="角色设定" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.config && row.config.roleConfig ? row.config.roleConfig.systemRole : '-' }}</template>
          </el-table-column>
          <el-table-column label="输出格式" width="110">
            <template #default="{ row }">
              {{ row.config && row.config.formatConfig ? row.config.formatConfig.outputFormat : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="思维链" width="88">
            <template #default="{ row }">
              {{ row.config && row.config.cotConfig && row.config.cotConfig.cotEnabled ? '开' : '关' }}
            </template>
          </el-table-column>
          <el-table-column prop="updater" label="更新人" width="110" show-overflow-tooltip />
          <el-table-column prop="updateTime" label="更新时间" width="148" show-overflow-tooltip />
          <!-- 操作列要一行放下「套用/编辑/复制/删除」，窄了会把删除挤到第二行 -->
          <el-table-column label="操作" width="228" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" :disabled="row.enabled === 0" @click="applyTemplate(row)">套用</el-button>
                <el-button link type="primary" @click="openTemplate(row)">编辑</el-button>
                <el-button link type="primary" @click="duplicateTemplate(row)">复制</el-button>
                <el-button link type="danger" @click="removeTemplate(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <p class="hint">「套用」会把模板内容写进当前配置并立即生效；想改当前配置的细节去「当前配置」页。</p>
      </el-tab-pane>

      <!-- ===================== 当前配置 ===================== -->
      <el-tab-pane label="当前配置" name="prompt">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="这里编辑的是当前生效的配置。想存成可复用的模板，去「提示词模板」点「从当前配置另存」。"
          style="margin-bottom: 16px"
        />
        <PromptConfigForm v-model="prompt" :presets="presets" />
        <el-form label-width="150px" style="max-width: 820px">
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="submit">保存当前配置</el-button>
            <el-button @click="reset">重置为默认</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="配置为运行时生效，服务重启后会恢复为系统默认值。"
        />
      </el-tab-pane>

      <!-- ===================== 版本与发布 ===================== -->
      <el-tab-pane label="版本与发布" name="version">
        <PromptVersionPanel :current-config="currentNestedConfig" />
      </el-tab-pane>

      <!-- ===================== 效果评测 ===================== -->
      <el-tab-pane label="效果评测" name="eval">
        <PromptEvalPanel />
      </el-tab-pane>

      <!-- ===================== Prompt 预览 ===================== -->
      <el-tab-pane label="Prompt 预览" name="preview">
        <p class="hint">
          按当前配置实时拼出最终发给模型的 Prompt。改完配置来这里看一眼，
          能确认变量替换、上下文注入与边界约束是否真的生效，而不用上线后靠回答猜。
        </p>

        <div class="page-toolbar is-actions-only">
          <div class="table-actions">
            <el-button type="primary" :loading="previewLoading === 'system'" @click="loadSystemPreview">只看 System</el-button>
            <el-button :loading="previewLoading === 'full'" @click="loadFullPreview">看完整 Prompt</el-button>
            <el-button :loading="previewLoading === 'messages'" @click="loadMessagesPreview">看 messages 格式</el-button>
          </div>
        </div>

        <div class="preview-row">
          <el-input v-model="previewQuestion" placeholder="用于预览的问题" style="width: 300px" />
          <el-select v-model="previewPreset" placeholder="查看某个预设模板的详情" clearable style="width: 220px" @change="loadPresetDetail">
            <el-option v-for="p in presets" :key="p.name" :label="p.label" :value="p.name" />
          </el-select>
        </div>

        <pre v-if="previewText" class="preview-box">{{ previewText }}</pre>
        <p v-if="previewMeta" class="preview-meta">{{ previewMeta }}</p>

        <el-descriptions v-if="presetDetail" :column="2" border size="small" class="preset-box">
          <el-descriptions-item label="预设名">{{ presetDetail.name }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ presetDetail.systemRole }}</el-descriptions-item>
          <el-descriptions-item label="领域">{{ presetDetail.roleDomain || '—' }}</el-descriptions-item>
          <el-descriptions-item label="输出格式">{{ presetDetail.outputFormat }}</el-descriptions-item>
          <el-descriptions-item label="思维链">{{ presetDetail.cotEnabled ? '开' : '关' }}</el-descriptions-item>
          <el-descriptions-item label="少样本">{{ presetDetail.fewShotEnabled ? '开' : '关' }}</el-descriptions-item>
          <el-descriptions-item label="严格输出">{{ presetDetail.strictOutput ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="仅依据上下文">{{ presetDetail.contextOnlyReply ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- ===================== 检索参数 ===================== -->
      <el-tab-pane label="检索参数" name="retrieval">
        <el-alert
          v-if="!retrievalImplemented"
          type="warning"
          :closable="false"
          show-icon
          title="后端未完成"
          description="检索参数尚未与运行时配置打通，保存后不会影响实际召回。"
          style="margin-bottom: 16px"
        />
        <el-form :model="retrieval" label-width="150px" style="max-width: 820px" v-loading="retrievalLoading">
          <el-form-item label="TopK">
            <el-input-number v-model="retrieval.topK" :min="1" :max="50" />
            <span class="form-tip">单次召回的候选知识条数</span>
          </el-form-item>
          <el-form-item label="相似度阈值">
            <el-input-number v-model="retrieval.scoreThreshold" :min="0" :max="1" :step="0.05" :precision="2" />
            <span class="form-tip">低于该分数的结果会被丢弃</span>
          </el-form-item>
          <el-form-item label="召回策略">
            <el-radio-group v-model="retrieval.strategy">
              <el-radio value="vector">纯向量</el-radio>
              <el-radio value="hybrid">混合检索</el-radio>
              <el-radio value="rerank">向量 + 重排</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="引用模板">
            <el-input v-model="retrieval.citationTemplate" type="textarea" :rows="3" placeholder="例如：依据：{{question}}" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingRetrieval" @click="submitRetrieval">保存检索参数</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- ===================== 模板编辑 ===================== -->
    <el-dialog :title="tplForm.id ? '编辑提示词模板' : '新建提示词模板'" v-model="tplVisible" width="860px">
      <el-form :model="tplForm" label-width="110px">
        <el-form-item label="模板名称" required>
          <el-input v-model="tplForm.name" maxlength="30" placeholder="如：电商售后专用" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="tplForm.description" placeholder="选填：写清适用场景，便于他人复用" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="tplForm.enabled" :active-value="1" :inactive-value="0" />
          <span class="form-tip">停用的模板不可套用，但保留配置</span>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">模板内容</el-divider>
      <PromptConfigForm v-model="tplConfig" :presets="presets" />

      <template #footer>
        <el-button @click="tplVisible = false">取消</el-button>
        <el-button type="primary" :loading="tplSaving" @click="submitTemplate">保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 提示词与检索：模板 → 当前配置 → 版本与发布 → 效果评测 → 检索参数。
 * 前四步是「改提示词」的完整链路：存模板复用、改当前配置、存版本待发布、跑评测把关；
 * 检索参数独立一层，管的是召回而不是话术。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPromptConfig, savePromptConfig, listPromptPresets, resetPromptConfig,
  getRetrievalConfig, saveRetrievalConfig,
  listPromptTemplates, savePromptTemplate, deletePromptTemplate,
  previewFullPrompt, previewMessages, previewSystemPrompt, getPresetDetail
} from '../api'
import PromptConfigForm from './knowledge/PromptConfigForm.vue'
import PromptVersionPanel from './knowledge/PromptVersionPanel.vue'
import PromptEvalPanel from './knowledge/PromptEvalPanel.vue'
import TbBtn from '../components/TbBtn.vue'

const tab = ref('template')

/** 后端 GET 返回嵌套结构、POST 收扁平结构，这里做双向转换，避免两处各写一遍字段映射 */
const flattenConfig = (d) => {
  const src = d || {}
  const role = src.roleConfig || {}
  const fmt = src.formatConfig || {}
  const cot = src.cotConfig || {}
  const fewShot = src.fewShotConfig || {}
  const bd = src.boundaryConfig || {}
  const ctx = src.contextConfig || {}
  const vars = src.variablesConfig || {}
  return {
    enabled: src.enabled ?? true,
    debugLog: src.debugLog ?? false,
    preset: src.preset || '',
    systemRole: role.systemRole || '',
    roleDescription: role.roleDescription || '',
    roleDomain: role.roleDomain || '',
    outputFormat: fmt.outputFormat || 'text',
    strictOutput: fmt.strictOutput ?? false,
    cotEnabled: cot.cotEnabled ?? false,
    cotInstruction: cot.cotInstruction || '',
    fewShotEnabled: fewShot.fewShotEnabled ?? false,
    variablesEnabled: vars.variablesEnabled ?? true,
    // 每行拷一份，避免模板之间共用同一个变量对象
    variables: Array.isArray(vars.variables) ? vars.variables.map((v) => ({ ...v })) : [],
    boundaryEnabled: bd.boundaryEnabled ?? true,
    noDataReply: bd.noDataReply || '',
    noFabrication: bd.noFabrication ?? true,
    additionalConstraints: bd.additionalConstraints || '',
    sensitiveReply: bd.sensitiveReply || '',
    injectionGuard: bd.injectionGuard ?? true,
    contextWindowSize: ctx.contextWindowSize ?? 5,
    contextOnlyReply: ctx.contextOnlyReply ?? true
  }
}

const nestConfig = (f) => ({
  enabled: f.enabled,
  debugLog: f.debugLog,
  preset: f.preset,
  roleConfig: { systemRole: f.systemRole, roleDescription: f.roleDescription, roleDomain: f.roleDomain },
  formatConfig: { outputFormat: f.outputFormat, strictOutput: f.strictOutput },
  cotConfig: { cotEnabled: f.cotEnabled, cotInstruction: f.cotInstruction },
  fewShotConfig: { fewShotEnabled: f.fewShotEnabled },
  variablesConfig: {
    variablesEnabled: f.variablesEnabled,
    // 名称为空的行是没填完的草稿，提交时丢掉
    variables: (f.variables || []).filter((v) => String(v.name || '').trim())
  },
  boundaryConfig: {
    boundaryEnabled: f.boundaryEnabled,
    noDataReply: f.noDataReply,
    noFabrication: f.noFabrication,
    additionalConstraints: f.additionalConstraints,
    sensitiveReply: f.sensitiveReply,
    injectionGuard: f.injectionGuard
  },
  contextConfig: { contextWindowSize: f.contextWindowSize, contextOnlyReply: f.contextOnlyReply }
})

// ===================== 当前配置 =====================
const prompt = reactive(flattenConfig({}))
/** 版本快照要的是嵌套结构，这里给版本面板一份当前配置 */
const currentNestedConfig = computed(() => nestConfig(prompt))
const presets = ref([])
const loading = ref(false)
const saving = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res = await getPromptConfig()
    Object.assign(prompt, flattenConfig(res.data))
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

const loadPresets = async () => {
  try {
    const res = await listPromptPresets()
    const d = res.data || {}
    // 后端返回 { availablePresets: [...], currentPreset }，兼容数组形态
    const names = Array.isArray(d) ? d : (d.availablePresets || [])
    presets.value = names.map((n) => (typeof n === 'string' ? { name: n, label: n } : n))
  } catch {
    presets.value = []
  }
}

const submit = async () => {
  saving.value = true
  try {
    await savePromptConfig({ ...prompt })
    ElMessage.success('当前配置已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

// ===================== 提示词模板 =====================
const templates = ref([])
const tplLoading = ref(false)
const templateImplemented = ref(true)
const tplVisible = ref(false)
const tplSaving = ref(false)
const tplForm = reactive({ id: null, name: '', description: '', enabled: 1 })
const tplConfig = reactive(flattenConfig({}))

const loadTemplates = async () => {
  tplLoading.value = true
  try {
    const res = await listPromptTemplates()
    const data = res.data
    templates.value = Array.isArray(data) ? data : (data?.records || [])
    templateImplemented.value = res.data?.implemented !== false
  } catch {
    templates.value = []
  } finally {
    tplLoading.value = false
  }
}

/** row 为空=新建；传 row=编辑；传 { asCopy: true } 时以该模板内容新建一份 */
const openTemplate = (row, asCopy = false) => {
  Object.assign(tplForm, {
    id: asCopy ? null : (row?.id ?? null),
    name: asCopy ? `${row?.name || ''} 副本` : (row?.name || ''),
    description: row?.description || '',
    enabled: row?.enabled ?? 1
  })
  Object.assign(tplConfig, flattenConfig(row?.config || {}))
  tplVisible.value = true
}

/** 把「当前配置」原样另存成模板 */
const saveAsTemplate = () => {
  Object.assign(tplForm, { id: null, name: '', description: '', enabled: 1 })
  Object.assign(tplConfig, { ...prompt })
  tplVisible.value = true
}

const duplicateTemplate = (row) => openTemplate(row, true)

const submitTemplate = async () => {
  if (!tplForm.name.trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  tplSaving.value = true
  try {
    const res = await savePromptTemplate({
      ...tplForm,
      name: tplForm.name.trim(),
      config: nestConfig(tplConfig)
    })
    ElMessage.success(res?.data || '模板已保存')
    tplVisible.value = false
    loadTemplates()
  } catch {
    /* 拦截器已提示 */
  } finally {
    tplSaving.value = false
  }
}

const removeTemplate = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除模板「${row.name}」？当前生效的配置不受影响。`, '删除模板', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePromptTemplate(row.id)
    ElMessage.success('已删除')
    loadTemplates()
  } catch {
    /* 拦截器已提示 */
  }
}

/** 套用模板：把模板内容写进当前配置并落库 */
const applyTemplate = async (row) => {
  try {
    await ElMessageBox.confirm(`确认把「${row.name}」套用为当前配置？会覆盖现在生效的提示词。`, '套用模板', { type: 'warning' })
  } catch {
    return
  }
  try {
    const flat = flattenConfig(row.config)
    await savePromptConfig(flat)
    Object.assign(prompt, flat)
    ElMessage.success('已套用为当前配置')
    tab.value = 'prompt'
  } catch {
    /* 拦截器已提示 */
  }
}

// ===================== 检索参数 =====================
const retrieval = reactive({ topK: 5, scoreThreshold: 0.35, strategy: 'hybrid', citationTemplate: '' })
const retrievalLoading = ref(false)
const savingRetrieval = ref(false)
const retrievalImplemented = ref(true)

const loadRetrieval = async () => {
  retrievalLoading.value = true
  try {
    const res = await getRetrievalConfig()
    const d = res.data || {}
    retrieval.topK = d.topK ?? 5
    retrieval.scoreThreshold = d.scoreThreshold ?? 0.35
    retrieval.strategy = d.strategy || 'hybrid'
    retrieval.citationTemplate = d.citationTemplate || ''
    retrievalImplemented.value = d.implemented !== false
  } catch {
    retrievalImplemented.value = true
  } finally {
    retrievalLoading.value = false
  }
}

const submitRetrieval = async () => {
  savingRetrieval.value = true
  try {
    await saveRetrievalConfig({ ...retrieval })
    ElMessage.success(retrievalImplemented.value ? '检索参数已保存' : '已提交，该配置暂未生效')
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingRetrieval.value = false
  }
}

// ===================== Prompt 预览 =====================
const previewLoading = ref('')
const previewQuestion = ref('退款多久能到账？')
const previewPreset = ref('')
const previewText = ref('')
const previewMeta = ref('')
const presetDetail = ref(null)

const loadSystemPreview = async () => {
  previewLoading.value = 'system'
  try {
    const res = await previewSystemPrompt()
    previewText.value = res.data?.systemPrompt || ''
    previewMeta.value = `System Prompt 长度 ${res.data?.length || 0} 字`
    presetDetail.value = null
  } catch {
    previewText.value = ''
  } finally {
    previewLoading.value = ''
  }
}

const loadFullPreview = async () => {
  previewLoading.value = 'full'
  try {
    const res = await previewFullPrompt({ question: previewQuestion.value })
    previewText.value = res.data?.fullPrompt || ''
    previewMeta.value = `共 ${res.data?.totalLength || 0} 字，注入 ${res.data?.contextDocCount || 0} 条检索片段`
    presetDetail.value = null
  } catch {
    previewText.value = ''
  } finally {
    previewLoading.value = ''
  }
}

const loadMessagesPreview = async () => {
  previewLoading.value = 'messages'
  try {
    const res = await previewMessages({ question: previewQuestion.value })
    // messages 是数组，转成可读文本展示，避免再写一套渲染
    previewText.value = (res.data?.messages || [])
      .map((m) => `【${m.role}】\n${m.content}`)
      .join('\n\n')
    previewMeta.value = `共 ${res.data?.messageCount || 0} 条消息，合计 ${res.data?.totalLength || 0} 字`
    presetDetail.value = null
  } catch {
    previewText.value = ''
  } finally {
    previewLoading.value = ''
  }
}

/** 选预设时展示它的明细，方便和当前配置对照 */
const loadPresetDetail = async (name) => {
  if (!name) {
    presetDetail.value = null
    return
  }
  try {
    const res = await getPresetDetail(name)
    presetDetail.value = res.data
  } catch {
    presetDetail.value = null
  }
}

const reset = async () => {
  try {
    await ElMessageBox.confirm('确认取消预设模板并恢复默认配置？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await resetPromptConfig()
    ElMessage.success('已重置')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  load()
  loadPresets()
  loadRetrieval()
  loadTemplates()
})
</script>

<style scoped>
.hint {
  color: #6b7280;
  line-height: 1.6;
  font-size: 13px;
  margin-bottom: 12px;
}
.strong-text {
  font-weight: 600;
  color: #1c2b4a;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
/* Prompt 预览：等宽字体保留缩进，方便看清拼装结构 */
.preview-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
  flex-wrap: wrap;
}
.preview-box {
  margin: 10px 0 0;
  padding: 14px 16px;
  background: #f7f9fc;
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.8;
  color: #1c2b4a;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 420px;
  overflow: auto;
}
.preview-meta {
  margin: 8px 0 0;
  font-size: 12.5px;
  color: #98a2b3;
}
.preset-box { margin-top: 16px; }
</style>
