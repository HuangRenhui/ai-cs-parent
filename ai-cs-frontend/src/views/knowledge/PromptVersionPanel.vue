<template>
  <div class="ver-panel">
    <p class="hint">
      每次改动存成一个版本，先当草稿留着，评测通过再放量。线上答歪了直接回滚到上一个正常版本，
      不用手改回来。
    </p>

    <div class="page-toolbar is-actions-only">
      <div class="table-actions">
        <el-button type="primary" @click="openSave">保存为新版本</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无版本记录，先保存一个版本试试"
      table-layout="fixed" max-height="520">
      <el-table-column label="版本" min-width="96">
        <template #default="{ row }">
          <span class="ver-no">{{ row.version }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="104">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small" effect="plain">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效流量" min-width="110">
        <template #default="{ row }">
          <template v-if="row.status === 'published'">全部</template>
          <template v-else-if="row.status === 'gray'">
            <span class="gray-num">{{ row.grayScale }}%</span>
          </template>
          <template v-else>未生效</template>
        </template>
      </el-table-column>
      <el-table-column prop="target" label="适用对象" min-width="130" show-overflow-tooltip />
      <el-table-column prop="changeNote" label="变更说明" min-width="240" show-overflow-tooltip />
      <el-table-column prop="creator" label="创建人" min-width="100" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="150" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" :disabled="list.length < 2" @click="openCompare(row)">对比</el-button>
            <!-- 灰度中的显示「调整放量」，比「发布」长两字，固定槽宽让后面的回滚/删除逐行对齐 -->
            <el-button class="act-slot" link type="primary" :disabled="row.status === 'published'" @click="openPublish(row)">
              {{ row.status === 'gray' ? '调整放量' : '发布' }}
            </el-button>
            <el-button link type="warning" :disabled="row.status === 'published' || row.status === 'rolled_back'"
              @click="rollback(row)">回滚</el-button>
            <el-button link type="danger" :disabled="row.status === 'published' || row.status === 'gray'"
              @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 保存为新版本 -->
    <el-dialog title="保存为新版本" v-model="saveVisible" width="560px">
      <p class="dialog-lead">
        会把「当前配置」此刻的内容存成一份快照，状态为草稿；草稿不对外生效，可以反复改。
      </p>
      <el-form :model="saveForm" label-width="90px">
        <el-form-item label="版本号" required>
          <el-input v-model="saveForm.version" placeholder="例如 v1.5.0" maxlength="20" />
        </el-form-item>
        <el-form-item label="适用对象">
          <el-input v-model="saveForm.target" placeholder="留空表示为平台默认" maxlength="40" />
        </el-form-item>
        <el-form-item label="变更说明" required>
          <el-input v-model="saveForm.changeNote" type="textarea" :rows="3"
            placeholder="写清这次改了什么、为什么改，出问题时靠它回溯" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发布 / 放量 -->
    <el-dialog :title="`发布 ${publishForm.version}`" v-model="publishVisible" width="520px">
      <p class="dialog-lead">
        先小比例放量观察，没问题再逐步拉满。拖动到 100% 即为全量发布。
      </p>
      <el-form label-width="110px">
        <el-form-item label="生效流量">
          <el-slider v-model="publishForm.grayScale" :step="10" show-stops :marks="GRAY_MARKS" />
        </el-form-item>
        <el-form-item label="当前选择">
          <span class="gray-num">{{ publishForm.grayScale }}%</span>
          <span class="form-tip">{{ publishForm.grayScale >= 100 ? '全量用户都会用这一版' : '只有部分对话会用到这一版' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 版本对比 -->
    <el-dialog title="版本对比" v-model="compareVisible" width="820px">
      <el-form label-width="80px" class="compare-pick">
        <el-form-item label="对比">
          <el-select v-model="compareAId" style="width: 200px">
            <el-option v-for="v in list" :key="v.id" :label="v.version" :value="v.id" />
          </el-select>
          <span class="vs">对比</span>
          <el-select v-model="compareBId" style="width: 200px">
            <el-option v-for="v in list" :key="v.id" :label="v.version" :value="v.id" />
          </el-select>
          <span class="form-tip">只列有差异的项</span>
        </el-form-item>
      </el-form>
      <el-table :data="diffRows" stripe max-height="420" empty-text="两个版本内容完全一致" table-layout="fixed">
        <el-table-column prop="label" label="配置项" min-width="150" />
        <el-table-column prop="a" label="左侧版本" min-width="200" show-overflow-tooltip />
        <el-table-column prop="b" label="右侧版本" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="diff-stat">
        共 {{ diffRows.length }} 项差异
      </div>
    </el-dialog>

    <!-- 版本详情 -->
    <el-dialog :title="`版本详情 ${detail?.version || ''}`" v-model="detailVisible" width="680px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="状态">{{ statusText(detail?.status) }}</el-descriptions-item>
        <el-descriptions-item label="适用对象">{{ detail?.target || '平台默认' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail?.creator || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail?.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="变更说明" :span="2">{{ detail?.changeNote || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="detail-fields">
        <div v-for="f in detailRows" :key="f.label" class="detail-row">
          <span class="detail-label">{{ f.label }}</span>
          <span class="detail-value">{{ f.value }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 提示词版本与发布。
 * 解决的是「改提示词不敢直接上线」：先存草稿，评测通过再灰度，出问题一键回滚。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPromptVersions, savePromptVersion, publishPromptVersion, rollbackPromptVersion, deletePromptVersion
} from '../../api'

const props = defineProps({
  /** 当前生效的配置（嵌套结构），「保存为新版本」时作为快照内容 */
  currentConfig: { type: Object, default: () => ({}) }
})

const STATUS_TEXT = { draft: '草稿', gray: '灰度中', published: '已发布', rolled_back: '已回滚' }
const STATUS_TAG = { draft: 'info', gray: 'warning', published: 'success', rolled_back: 'danger' }
const GRAY_MARKS = { 0: '0', 50: '50%', 100: '100%' }

/** 只对比业务上有意义的字段，排除内部键，免得对比表全是噪音 */
const FIELD_LABELS = [
  { path: ['enabled'], label: 'Prompt 调优' },
  { path: ['preset'], label: '预设模板' },
  { path: ['roleConfig', 'systemRole'], label: '角色设定' },
  { path: ['roleConfig', 'roleDomain'], label: '所属领域' },
  { path: ['roleConfig', 'roleDescription'], label: '角色描述' },
  { path: ['formatConfig', 'outputFormat'], label: '输出格式' },
  { path: ['formatConfig', 'strictOutput'], label: '严格按格式输出' },
  { path: ['cotConfig', 'cotEnabled'], label: '启用思维链' },
  { path: ['fewShotConfig', 'fewShotEnabled'], label: '启用少样本示例' },
  { path: ['variablesConfig', 'variablesEnabled'], label: '启用变量替换' },
  { path: ['boundaryConfig', 'boundaryEnabled'], label: '启用边界约束' },
  { path: ['boundaryConfig', 'noFabrication'], label: '禁止编造' },
  { path: ['boundaryConfig', 'injectionGuard'], label: '提示词注入防护' },
  { path: ['contextConfig', 'contextWindowSize'], label: '上下文条数' },
  { path: ['contextConfig', 'contextOnlyReply'], label: '仅依据上下文回答' },
  { path: ['boundaryConfig', 'noDataReply'], label: '无依据时回复' },
  { path: ['boundaryConfig', 'sensitiveReply'], label: '违规问题拒答话术' },
  { path: ['boundaryConfig', 'additionalConstraints'], label: '附加约束' }
]

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const saveVisible = ref(false)
const publishVisible = ref(false)
const compareVisible = ref(false)
const detailVisible = ref(false)
const detail = ref(null)

const saveForm = reactive({ version: '', target: '', changeNote: '' })
const publishForm = reactive({ id: null, version: '', grayScale: 100 })
const compareAId = ref(null)
const compareBId = ref(null)

const statusText = (v) => STATUS_TEXT[v] || v || '—'
const statusTag = (v) => STATUS_TAG[v] || 'info'

const load = async () => {
  loading.value = true
  try {
    const res = await listPromptVersions()
    list.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 版本号按现有最大次版本 +1，省得每次手敲 */
const nextVersion = () => {
  const nums = list.value
    .map((v) => /^v(\d+)\.(\d+)\.(\d+)$/.exec(v.version || ''))
    .filter(Boolean)
    .map((m) => Number(m[2]))
  return `v1.${nums.length ? Math.max(...nums) + 1 : 0}.0`
}

const openSave = () => {
  saveForm.version = nextVersion()
  saveForm.target = list.value[0]?.target || '电商售后专用'
  saveForm.changeNote = ''
  saveVisible.value = true
}

const submitSave = async () => {
  if (!saveForm.version.trim() || !saveForm.changeNote.trim()) {
    ElMessage.warning('请填写版本号与变更说明')
    return
  }
  saving.value = true
  try {
    await savePromptVersion({
      version: saveForm.version.trim(),
      target: saveForm.target.trim() || '平台默认',
      changeNote: saveForm.changeNote.trim(),
      status: 'draft',
      grayScale: 0,
      config: JSON.parse(JSON.stringify(props.currentConfig || {}))
    })
    ElMessage.success('已存为草稿')
    saveVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const openPublish = (row) => {
  publishForm.id = row.id
  publishForm.version = row.version
  publishForm.grayScale = row.status === 'gray' ? row.grayScale : 100
  publishVisible.value = true
}

const submitPublish = async () => {
  publishing.value = true
  try {
    const res = await publishPromptVersion(publishForm.id, publishForm.grayScale)
    ElMessage.success(res?.msg || '已发布')
    publishVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    publishing.value = false
  }
}

const rollback = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认回滚到 ${row.version}？会把这一版的内容重新设为生效，当前在线版本被替换。`,
      '回滚版本',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const res = await rollbackPromptVersion(row.id)
    ElMessage.success(res?.msg || '已回滚')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除版本 ${row.version}？删除后无法再回滚到这一版。`, '删除版本', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePromptVersion(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

const openCompare = (row) => {
  compareBId.value = row.id
  // 默认拿上一版做对照，这是最常见的「这次改了什么」场景
  const idx = list.value.findIndex((v) => v.id === row.id)
  compareAId.value = list.value[idx + 1]?.id ?? list.value.find((v) => v.id !== row.id)?.id ?? null
  compareVisible.value = true
}

const versionById = (id) => list.value.find((v) => v.id === id)
const pickPath = (obj, path) => path.reduce((o, k) => (o == null ? undefined : o[k]), obj)

/** 布尔显示开关、数组显示条数，其它原样；空值统一破折号 */
const fmtValue = (v) => {
  if (v === undefined || v === null || v === '') return '—'
  if (typeof v === 'boolean') return v ? '开' : '关'
  if (Array.isArray(v)) return `${v.length} 项`
  return String(v)
}

const diffRows = computed(() => {
  const a = versionById(compareAId.value)
  const b = versionById(compareBId.value)
  if (!a || !b || a.id === b.id) return []
  return FIELD_LABELS
    .map((f) => {
      const va = pickPath(a.config, f.path)
      const vb = pickPath(b.config, f.path)
      return { label: f.label, a: fmtValue(va), b: fmtValue(vb), same: JSON.stringify(va ?? null) === JSON.stringify(vb ?? null) }
    })
    .filter((r) => !r.same)
})

const openDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}

const detailRows = computed(() => {
  const cfg = detail.value?.config
  if (!cfg) return []
  return FIELD_LABELS.map((f) => ({ label: f.label, value: fmtValue(pickPath(cfg, f.path)) }))
})

onMounted(load)
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
.dialog-lead {
  margin: 0 0 14px;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.ver-no {
  font-weight: 650;
  color: #1c2b4a;
}
.gray-num {
  font-weight: 650;
  color: #b54708;
}
.compare-pick {
  margin-bottom: 4px;
}
.vs {
  margin: 0 10px;
  color: #667085;
  font-size: 13px;
}
.diff-stat {
  margin-top: 10px;
  font-size: 12.5px;
  color: #667085;
  text-align: right;
}
.detail-fields {
  margin-top: 14px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  overflow: hidden;
}
.detail-row {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 12px;
  padding: 9px 14px;
  font-size: 13px;
  border-bottom: 1px solid #f2f5f9;
}
.detail-row:last-child {
  border-bottom: none;
}
.detail-label {
  color: #667085;
}
.detail-value {
  color: #1c2b4a;
  word-break: break-word;
}
</style>
