<template>
  <div class="eval-panel">
    <p class="hint">
      把「标准问法 + 标准答案」固化成用例，每次改完提示词先跑一遍。通过率掉下来就说明这次改动有回退，
      先别发布。
    </p>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <!-- ===================== 评测集与用例 ===================== -->
    <template v-if="section === 'cases'">
      <div class="eval-body">
        <aside class="set-list">
          <div class="set-head">
            <span>评测集</span>
            <el-button link type="primary" @click="openSet()">+ 新建</el-button>
          </div>
          <button
            v-for="s in sets"
            :key="s.id"
            type="button"
            class="set-item"
            :class="{ active: s.id === activeSetId }"
            @click="selectSet(s)"
          >
            <div class="set-name">{{ s.name }}</div>
            <div class="set-meta">{{ s.caseCount || 0 }} 条用例</div>
          </button>
          <el-empty v-if="!sets.length" description="暂无评测集" :image-size="60" />
        </aside>

        <section class="case-main">
          <div class="page-toolbar is-actions-only">
            <div class="table-actions">
              <el-button type="primary" :disabled="!activeSet" @click="openCase()">新增用例</el-button>
              <el-button :disabled="!activeSet" @click="openSet(activeSet)">编辑评测集</el-button>
              <el-button type="danger" :disabled="!activeSet" @click="removeSet(activeSet)">删除评测集</el-button>
              <el-button type="success" :disabled="!activeSet || !cases.length" :loading="running" @click="openRun">开始评测</el-button>
            </div>
          </div>
          <el-table :data="cases" stripe v-loading="caseLoading" empty-text="该评测集还没有用例，先加几条标准问法"
            table-layout="fixed" max-height="460">
            <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="expected" label="期望答案" min-width="230" show-overflow-tooltip />
            <el-table-column prop="expectHit" label="期望命中知识" min-width="150" show-overflow-tooltip />
            <el-table-column label="要求" min-width="96">
              <template #default="{ row }">
                <el-tag v-if="row.mustRefuse === 1" size="small" type="danger" effect="plain">必须拒答</el-tag>
                <span v-else class="muted">正常作答</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="120">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openCase(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeCase(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </template>

    <!-- ===================== 历史报告 ===================== -->
    <template v-else>
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <el-button @click="loadRuns">刷新</el-button>
        </div>
      </div>
      <el-table :data="runs" stripe v-loading="runLoading" empty-text="还没有跑过评测" table-layout="fixed" max-height="520">
        <el-table-column prop="createTime" label="评测时间" min-width="155" />
        <el-table-column prop="setName" label="评测集" min-width="140" show-overflow-tooltip />
        <el-table-column prop="target" label="评测对象" min-width="140" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" min-width="90" />
        <el-table-column label="用例数" min-width="80">
          <template #default="{ row }">{{ row.total }}</template>
        </el-table-column>
        <el-table-column label="通过率" min-width="140">
          <template #default="{ row }">
            <div class="rate-cell">
              <span class="rate-num" :class="rateClass(row.passRate)">{{ row.passRate }}%</span>
              <el-progress :percentage="row.passRate" :show-text="false" :stroke-width="6"
                :color="rateColor(row.passRate)" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平均耗时" min-width="100">
          <template #default="{ row }">{{ row.avgLatencyMs }} 毫秒</template>
        </el-table-column>
        <el-table-column prop="runner" label="执行人" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" min-width="100" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openReport(row)">查看报告</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 评测集编辑 -->
    <el-dialog :title="setForm.id ? '编辑评测集' : '新建评测集'" v-model="setVisible" width="520px">
      <el-form :model="setForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="setForm.name" maxlength="30" placeholder="例如 售后高频问题" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="setForm.description" type="textarea" :rows="2" placeholder="这组用例覆盖什么场景" />
        </el-form-item>
        <el-form-item label="适用对象">
          <el-input v-model="setForm.target" maxlength="40" placeholder="留空表示为平台默认" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="setVisible = false">取消</el-button>
        <el-button type="primary" :loading="setSaving" @click="submitSet">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用例编辑 -->
    <el-dialog :title="caseForm.id ? '编辑用例' : '新增用例'" v-model="caseVisible" width="600px">
      <el-form :model="caseForm" label-width="110px">
        <el-form-item label="问题" required>
          <el-input v-model="caseForm.question" type="textarea" :rows="2" placeholder="客户会怎么问" />
        </el-form-item>
        <el-form-item label="期望答案">
          <el-input v-model="caseForm.expected" type="textarea" :rows="2" placeholder="按业务口径应有的回答要点" />
        </el-form-item>
        <el-form-item label="期望命中知识">
          <el-input v-model="caseForm.expectHit" placeholder="填知识库里对应的问题，留空表示不校验" />
        </el-form-item>
        <el-form-item label="必须拒答">
          <el-switch v-model="caseForm.mustRefuse" :active-value="1" :inactive-value="0" />
          <span class="form-tip">开启后，只要模型给了具体方案就算失败</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseVisible = false">取消</el-button>
        <el-button type="primary" :loading="caseSaving" @click="submitCase">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发起评测 -->
    <el-dialog title="开始评测" v-model="runVisible" width="520px">
      <p class="dialog-lead">
        将对「{{ activeSet?.name }}」的 {{ cases.length }} 条用例逐条跑一遍，结束后生成报告。
      </p>
      <el-form label-width="100px">
        <el-form-item label="评测对象">
          <el-radio-group v-model="runForm.mode">
            <el-radio value="current">当前配置</el-radio>
            <el-radio value="version">指定版本</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="runForm.mode === 'version'" label="版本号">
          <el-input v-model="runForm.version" placeholder="例如 v1.4.0" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runVisible = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="submitRun">开始</el-button>
      </template>
    </el-dialog>

    <!-- 评测报告 -->
    <el-dialog title="评测报告" v-model="reportVisible" width="900px">
      <div v-if="report" class="report-head">
        <el-tag>评测集 {{ report.setName }}</el-tag>
        <el-tag type="info">对象 {{ report.target }}</el-tag>
        <el-tag type="info">版本 {{ report.version }}</el-tag>
        <el-tag :type="rateTag(report.passRate)">通过率 {{ report.passRate }}%</el-tag>
        <el-tag type="success">通过 {{ report.passed }}</el-tag>
        <el-tag type="danger">未通过 {{ report.failed }}</el-tag>
        <el-tag type="warning">均耗时 {{ report.avgLatencyMs }} 毫秒</el-tag>
      </div>
      <el-table :data="report?.details || []" stripe max-height="420" table-layout="fixed">
        <el-table-column prop="question" label="问题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="expected" label="期望" min-width="180" show-overflow-tooltip />
        <el-table-column prop="actual" label="实际回答" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" min-width="80">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'" size="small">{{ row.passed ? '通过' : '未通过' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '—' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 提示词效果评测。
 * 提示词改动的把关口：有标准答案的用例集 + 跑分报告，避免靠感觉上线。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SectionSwitch from '../../components/SectionSwitch.vue'
import {
  listEvalSets, saveEvalSet, updateEvalSet, deleteEvalSet,
  listEvalCases, saveEvalCase, deleteEvalCase,
  runPromptEval, listEvalRuns, getEvalRunReport
} from '../../api'

const section = ref('cases')
const sectionOptions = [
  { value: 'cases', label: '评测集与用例' },
  { value: 'runs', label: '历史报告' }
]

const sets = ref([])
const cases = ref([])
const activeSetId = ref(null)
const caseLoading = ref(false)
const setVisible = ref(false)
const setSaving = ref(false)
const caseVisible = ref(false)
const caseSaving = ref(false)
const runVisible = ref(false)
const running = ref(false)
const reportVisible = ref(false)
const report = ref(null)
const runs = ref([])
const runLoading = ref(false)

const setForm = reactive({ id: null, name: '', description: '', target: '' })
const caseForm = reactive({ id: null, question: '', expected: '', expectHit: '', mustRefuse: 0 })
const runForm = reactive({ mode: 'current', version: '' })

const activeSet = computed(() => sets.value.find((s) => s.id === activeSetId.value) || null)

const rateColor = (v) => (v >= 90 ? '#12b76a' : v >= 70 ? '#f79009' : '#f04438')
const rateClass = (v) => (v >= 90 ? 'good' : v >= 70 ? 'warn' : 'bad')
const rateTag = (v) => (v >= 90 ? 'success' : v >= 70 ? 'warning' : 'danger')

const loadSets = async () => {
  try {
    const res = await listEvalSets()
    sets.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    // 首次进入自动选中第一个，免得多一步点击
    if (!activeSetId.value && sets.value.length) activeSetId.value = sets.value[0].id
  } catch {
    sets.value = []
  }
}

const loadCases = async () => {
  if (!activeSetId.value) {
    cases.value = []
    return
  }
  caseLoading.value = true
  try {
    const res = await listEvalCases(activeSetId.value)
    cases.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
  } catch {
    cases.value = []
  } finally {
    caseLoading.value = false
  }
}

const loadRuns = async () => {
  runLoading.value = true
  try {
    const res = await listEvalRuns()
    runs.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
  } catch {
    runs.value = []
  } finally {
    runLoading.value = false
  }
}

const selectSet = (s) => {
  activeSetId.value = s.id
  loadCases()
}

const openSet = (row) => {
  Object.assign(setForm, {
    id: row?.id ?? null,
    name: row?.name || '',
    description: row?.description || '',
    target: row?.target || ''
  })
  setVisible.value = true
}

const submitSet = async () => {
  if (!setForm.name.trim()) {
    ElMessage.warning('请填写评测集名称')
    return
  }
  setSaving.value = true
  try {
    const payload = { ...setForm, name: setForm.name.trim(), target: setForm.target.trim() || '平台默认' }
    if (setForm.id) await updateEvalSet(payload)
    else await saveEvalSet(payload)
    ElMessage.success('保存成功')
    setVisible.value = false
    await loadSets()
    loadCases()
  } catch {
    /* 拦截器已提示 */
  } finally {
    setSaving.value = false
  }
}

const removeSet = async (row) => {
  if (!row) return
  try {
    await ElMessageBox.confirm(`确认删除评测集「${row.name}」？其中的用例与历史报告会一起删除。`, '删除评测集', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteEvalSet(row.id)
    ElMessage.success('已删除')
    activeSetId.value = null
    cases.value = []
    await loadSets()
    loadCases()
    loadRuns()
  } catch {
    /* 拦截器已提示 */
  }
}

const openCase = (row) => {
  Object.assign(caseForm, {
    id: row?.id ?? null,
    question: row?.question || '',
    expected: row?.expected || '',
    expectHit: row?.expectHit || '',
    mustRefuse: row?.mustRefuse ?? 0
  })
  caseVisible.value = true
}

const submitCase = async () => {
  if (!caseForm.question.trim()) {
    ElMessage.warning('请填写问题')
    return
  }
  caseSaving.value = true
  try {
    await saveEvalCase({ ...caseForm, setId: activeSetId.value, question: caseForm.question.trim() })
    ElMessage.success('保存成功')
    caseVisible.value = false
    await loadCases()
    loadSets()
  } catch {
    /* 拦截器已提示 */
  } finally {
    caseSaving.value = false
  }
}

const removeCase = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除这条用例？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteEvalCase(row.id)
    ElMessage.success('已删除')
    await loadCases()
    loadSets()
  } catch {
    /* 拦截器已提示 */
  }
}

const openRun = () => {
  runForm.mode = 'current'
  runForm.version = ''
  runVisible.value = true
}

const submitRun = async () => {
  if (runForm.mode === 'version' && !runForm.version.trim()) {
    ElMessage.warning('请填写要评测的版本号')
    return
  }
  running.value = true
  try {
    const res = await runPromptEval({
      setId: activeSetId.value,
      target: activeSet.value?.target || '当前配置',
      version: runForm.mode === 'current' ? '当前配置' : runForm.version.trim()
    })
    ElMessage.success(res?.msg || '评测完成')
    runVisible.value = false
    report.value = res.data
    reportVisible.value = true
    await loadRuns()
    section.value = 'runs'
  } catch {
    /* 拦截器已提示 */
  } finally {
    running.value = false
  }
}

const openReport = async (row) => {
  try {
    const res = await getEvalRunReport(row.id)
    report.value = res.data
    reportVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(async () => {
  await loadSets()
  loadCases()
  loadRuns()
})
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
.muted {
  color: #98a2b3;
}
/* 左侧评测集列表 + 右侧用例表 */
.eval-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.set-list {
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 10px;
  background: #fbfcfe;
}
.set-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 650;
  color: #1c2b4a;
  padding: 0 4px 8px;
}
.set-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  border-radius: 8px;
  background: transparent;
  padding: 9px 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.set-item:hover {
  background: #eef3fd;
}
.set-item.active {
  background: #2f6bff;
}
.set-name {
  font-size: 13px;
  font-weight: 600;
  color: #1c2b4a;
}
.set-item.active .set-name {
  color: #fff;
}
.set-meta {
  margin-top: 3px;
  font-size: 12px;
  color: #98a2b3;
}
.set-item.active .set-meta {
  color: rgba(255, 255, 255, 0.8);
}
.case-main {
  min-width: 0;
}
.report-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.rate-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rate-num {
  font-weight: 650;
  width: 48px;
  flex-shrink: 0;
}
.rate-num.good { color: #067647; }
.rate-num.warn { color: #b54708; }
.rate-num.bad { color: #b42318; }
.rate-cell :deep(.el-progress) {
  flex: 1;
  min-width: 0;
}

@media (max-width: 860px) {
  .eval-body {
    grid-template-columns: 1fr;
  }
}
</style>
