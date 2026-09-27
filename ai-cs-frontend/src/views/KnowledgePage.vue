<template>
  <div class="page-card">
    <p class="hint">维护问答、向量化与语义检索，为智能回复提供依据。</p>
    <!-- 顶栏切片已标明本页，不再重复「知识库管理」标题 -->
    <div class="page-toolbar is-actions-only" v-if="section === 'faq'">
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增" @click="openAddDialog" />
        <TbBtn act="import" label="导入" @click="openImport" />
        <TbBtn act="export" @click="exportVisible = true" />
        <el-button @click="handleBatchVectorize">
          <el-icon><MagicStick /></el-icon>
          批量向量化
        </el-button>
        <el-button @click="goDocument">
          <el-icon><Document /></el-icon>
          文档知识库
        </el-button>
        <el-upload class="upload-btn" :show-file-list="false" accept=".json,.txt,.md" :http-request="uploadFaqFile">
          <el-button>
            <el-icon><Document /></el-icon>
            导入 FAQ
          </el-button>
        </el-upload>
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <template v-if="section === 'faq'">
    <!-- 筛选与语义检索入口同一行，检索本身进弹窗 -->
    <div class="filter-bar">
      <TenantSelect v-model="tenantCode" placeholder="选择租户" style="width: 160px" />
      <el-input v-model="searchKeyword" placeholder="搜索问题或答案" clearable style="width: 200px" @keyup.enter="loadFaqs" />
      <el-select v-model="categoryFilter" placeholder="分类筛选" clearable style="width: 130px">
        <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <FilterActions @search="searchFaqs" @reset="resetFaqs" />
      <el-button type="primary" plain @click="openRagDialog">
        <el-icon><Aim /></el-icon>
        语义检索
      </el-button>
    </div>

    <el-table :data="faqList" stripe v-loading="loading" empty-text="暂无问答或加载失败" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column label="租户" min-width="88">
        <template #default="{ row }">{{ displayText(TENANT_TEXT, row.tenantCode) }}</template>
      </el-table-column>
      <el-table-column label="问题" min-width="160">
        <template #default="{ row }">
          <CellText title="问题" :text="row.question" />
        </template>
      </el-table-column>
      <el-table-column label="答案" min-width="160">
        <template #default="{ row }">
          <CellText title="答案" :text="row.answer" />
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" min-width="80" />
      <el-table-column label="状态" min-width="72">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核" min-width="88">
        <template #default="scope">
          <el-tag :type="auditType(scope.row.auditStatus)">{{ auditText(scope.row.auditStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="赞/踩/浏览" min-width="110">
        <template #default="scope">
          <span class="stat-cell">{{ scope.row.likeCount || 0 }} / {{ scope.row.dislikeCount || 0 }} / {{ scope.row.viewCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="向量" min-width="92">
        <template #default="{ row }">
          <el-tag v-if="statusKnown && vectorizeStatus.has(row.id)"
                  :type="vectorizeStatus.get(row.id) ? 'success' : 'info'" size="small">
            {{ vectorizeStatus.get(row.id) ? '已向量化' : '未向量化' }}
          </el-tag>
          <!-- 向量库不可用时状态未知，显示「—」而不是谎报「未向量化」 -->
          <span v-else class="stat-cell">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="168" />
      <el-table-column label="操作" width="258">
        <template #default="scope">
          <div class="table-actions">
            <TblAct act="edit" @click="editFaq(scope.row)" />
            <!-- 「重新向量化」比「向量化」长两字，固定槽宽免得后面的删除逐行错位 -->
            <TblAct
              class="act-slot is-long"
              :act="vectorizeStatus.get(scope.row.id) ? 'revector' : 'vector'"
              @click="handleVectorizeFaq(scope.row.id)"
            />
            <TblAct act="delete" @click="handleDeleteFaq(scope.row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="faqPage" v-model:size="faqSize" :total="faqTotal" @change="loadFaqs" />
    <ExportPickDialog
      v-model="exportVisible"
      title="导出问答"
      :fields="exportFields"
      :selected-count="selectedRows.length"
      :page-count="faqList.length"
      @confirm="doExport"
    />
    </template>

    <template v-else>
    <el-table :data="missList" size="small" stripe empty-text="暂无未命中记录" table-layout="fixed" max-height="680">
      <el-table-column prop="createTime" label="时间" min-width="170" />
      <el-table-column label="问题" min-width="220">
        <template #default="{ row }">
          <CellText title="问题" :text="row.question" />
        </template>
      </el-table-column>
      <el-table-column prop="topScore" label="最高分" min-width="80" />
      <el-table-column label="状态" min-width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="small">
            {{ scope.row.status === 1 ? '已转问' : '待处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="88">
        <template #default="scope">
          <div class="table-actions">
            <TblAct v-if="scope.row.status !== 1" act="convert" @click="openConvertDialog(scope.row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="missPage" v-model:size="missSize" :total="missTotal" @change="loadMiss" />
    </template>

    <!-- 添加/编辑问答弹窗 -->
    <el-dialog :title="isEdit ? '编辑问答' : '新增问答'" v-model="showAddDialog" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="租户">
          <TenantSelect v-model="form.tenantCode" />
        </el-form-item>
        <el-form-item label="问题">
          <el-input v-model="form.question" placeholder="必填" />
        </el-form-item>
        <el-form-item label="答案">
          <el-input v-model="form.answer" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择或输入" filterable allow-create default-first-option>
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">启用</el-radio-button>
            <el-radio-button :label="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveFaq">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入：粘贴 JSON 数组，或上传 .json / .txt 文件 -->
    <el-dialog title="导入问答" v-model="importVisible" width="640px" @closed="resetImport">
      <el-radio-group v-model="importMode" class="import-mode">
        <el-radio-button label="paste">粘贴输入</el-radio-button>
        <el-radio-button label="file">上传文件</el-radio-button>
      </el-radio-group>
      <p v-if="importMode === 'paste'" class="hint">请粘贴问答数组，每项含问题、答案，可选分类、租户、状态。单次最多 200 条。</p>
      <p v-else class="hint">支持问答数组文件，或纯文本（文件名作为问题、全文作为答案）。</p>
      <el-input
        v-if="importMode === 'paste'"
        v-model="importText"
        type="textarea"
        :rows="10"
        placeholder='[{"question":"如何退款？","answer":"7天无理由…","category":"售后"}]'
      />
      <el-upload
        v-else
        drag
        :auto-upload="false"
        accept=".json,.txt,.md"
        :show-file-list="true"
        :on-change="onImportFileChange"
      >
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">已选文件会按问答数组或纯文本导入</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 语义检索弹窗：输入问题后走 RAG，结果与引用在此展示 -->
    <el-dialog title="语义检索" v-model="ragVisible" width="640px">
      <p class="hint">按当前筛选租户检索相近问答，用于核对向量召回效果。</p>
      <div class="search-row">
        <el-input v-model="searchQuestion" placeholder="输入问题，如：如何退款？" clearable @keyup.enter="semanticSearch" />
        <el-button type="primary" :loading="ragSearching" @click="semanticSearch">检索</el-button>
      </div>
      <div v-if="searchResult" class="search-result">
        <p>{{ searchResult }}</p>
        <div v-if="searchCitations.length" class="citations">
          <el-tag v-for="c in searchCitations" :key="c.faqId" size="small" style="margin-right: 6px;">
            #{{ c.faqId }} {{ c.question }}
          </el-tag>
        </div>
      </div>
    </el-dialog>

    <!-- 未命中转问弹窗 -->
    <el-dialog title="未命中转问学习" v-model="convertVisible" width="560px">
      <p class="hint">问题：{{ convertRow?.question }}</p>
      <el-form label-width="80px">
        <el-form-item label="答案">
          <el-input v-model="convertForm.answer" type="textarea" :rows="4" placeholder="必填，填写标准答案后转为正式问答并向量化" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="convertForm.category" placeholder="请选择或输入" filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="converting" @click="handleConvert">确定转问</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, Document, MagicStick } from '@element-plus/icons-vue'
import {
  batchVectorize as batchVectorizeApi,
  convertMiss as convertMissApi,
  deleteFaq as deleteFaqApi,
  faqVectorizeStatus,
  importFaqs,
  listFaqs,
  listKnowledgeMiss,
  ragSearchKnowledge,
  saveFaq as saveFaqApi,
  updateFaq as updateFaqApi,
  vectorizeFaq as vectorizeFaqApi
} from '../api'
import TenantSelect from '../components/TenantSelect.vue'
import TablePager from '../components/TablePager.vue'
import SectionSwitch from '../components/SectionSwitch.vue'
import ExportPickDialog from '../components/ExportPickDialog.vue'
import { TENANT_TEXT, displayText } from '../utils/selectOptions'
import { rowsToCsv, downloadTextFile } from '../utils/listCsv'
import { useRouter } from 'vue-router'

const router = useRouter()
const faqList = ref([])
/** id -> 是否已向量化；statusKnown=false 表示向量库不可用，状态未知 */
const vectorizeStatus = ref(new Map())
const statusKnown = ref(true)
/** 问答列表 / 未命中回收横排切片 */
const section = ref('faq')
const sectionOptions = [
  { value: 'faq', label: '问答列表' },
  { value: 'miss', label: '未命中回收' }
]
const showAddDialog = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const searchQuestion = ref('')
const searchResult = ref('')
const searchCitations = ref([])
const ragVisible = ref(false)
const ragSearching = ref(false)
const searchKeyword = ref('')
const tenantCode = ref('default')
const categoryFilter = ref('')
const statusFilter = ref('')
const faqPage = ref(1)
const faqSize = ref(20)
const faqTotal = ref(0)
const categories = ref([])
const missList = ref([])
const missPage = ref(1)
const missSize = ref(10)
const missTotal = ref(0)
const importVisible = ref(false)
const exportVisible = ref(false)
const batchRemoving = ref(false)
const selectedRows = ref([])
/** paste=粘贴 JSON；file=上传文本/JSON 文件 */
const importMode = ref('paste')
const importText = ref('')
/** 上传文件时的文件名，纯文本导入用作问题标题 */
const importFileName = ref('')
const importing = ref(false)
const convertVisible = ref(false)
const converting = ref(false)
const convertRow = ref(null)
const convertForm = ref({ answer: '', category: '' })

const defaultForm = () => ({
  id: '',
  tenantCode: tenantCode.value || 'default',
  question: '',
  answer: '',
  category: '',
  status: 1
})
const form = ref(defaultForm())

const loadFaqs = async () => {
  loading.value = true
  try {
    const params = { page: faqPage.value, size: faqSize.value }
    if (tenantCode.value) params.tenantCode = tenantCode.value
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    if (statusFilter.value !== '' && statusFilter.value != null) params.status = statusFilter.value
    if (categoryFilter.value) params.category = categoryFilter.value
    const res = await listFaqs(params)
    const data = res.data
    const rows = Array.isArray(data) ? data : (data?.records || [])
    faqTotal.value = Array.isArray(data) ? rows.length : (data?.total || 0)
    // 后端 list 暂不支持 category 条件，前端过滤兜底
    faqList.value = categoryFilter.value
      ? rows.filter(item => item.category === categoryFilter.value)
      : rows
    loadVectorizeStatus(faqList.value)
    loadCategories()
    loadMiss()
  } catch {
    faqList.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 批量查当前页的向量化状态。
 * 失败时整体降级为「未知」：列表照常可用，按钮仍可按幂等语义执行（upsert 可重复调用）
 */
const loadVectorizeStatus = async (rows) => {
  const ids = (rows || []).map(r => r.id).filter(id => id != null)
  if (!ids.length) {
    vectorizeStatus.value = new Map()
    statusKnown.value = true
    return
  }
  try {
    const res = await faqVectorizeStatus(ids, tenantCode.value || undefined)
    const data = res?.data || {}
    statusKnown.value = data.available !== false
    const set = new Set(data.vectorizedIds || [])
    vectorizeStatus.value = new Map(ids.map(id => [id, set.has(id)]))
  } catch {
    statusKnown.value = false
    vectorizeStatus.value = new Map()
  }
}

const loadCategories = () => {
  const cats = [...new Set(faqList.value.map(item => item.category).filter(Boolean))]
  categories.value = cats
}

const searchFaqs = () => {
  faqPage.value = 1
  loadFaqs()
}

/** 清空筛选后回到第一页 */
const resetFaqs = () => {
  searchKeyword.value = ''
  tenantCode.value = 'default'
  categoryFilter.value = ''
  statusFilter.value = ''
  searchFaqs()
}

const openAddDialog = () => {
  isEdit.value = false
  form.value = defaultForm()
  showAddDialog.value = true
}

const editFaq = (row) => {
  isEdit.value = true
  form.value = { ...row }
  showAddDialog.value = true
}

const handleSaveFaq = async () => {
  if (!form.value.question || !form.value.question.trim()) {
    ElMessage.warning('请填写问题')
    return
  }
  if (!form.value.answer || !form.value.answer.trim()) {
    ElMessage.warning('请填写答案')
    return
  }
  saving.value = true
  try {
    let res
    if (isEdit.value) {
      res = await updateFaqApi(form.value)
    } else {
      res = await saveFaqApi(form.value)
    }
    ElMessage.success(res?.data || (isEdit.value ? '修改成功' : '保存成功'))
    showAddDialog.value = false
    form.value = defaultForm()
    isEdit.value = false
    loadFaqs()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const handleDeleteFaq = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这条问答吗？删除后将同步清理向量数据。', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteFaqApi(id)
    ElMessage.success('删除成功')
    loadFaqs()
  } catch {
    /* 拦截器已提示 */
  }
}

const onSelect = (rows) => {
  selectedRows.value = rows
}

/** 按勾选行逐条删除问答 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 条问答吗？向量数据会一并清理。`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await deleteFaqApi(row.id)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已删除 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('删除失败')
    selectedRows.value = []
    loadFaqs()
  } finally {
    batchRemoving.value = false
  }
}

/** 导出可选字段，状态/审核转成中文 */
const exportFields = [
  { key: 'id', label: '编号' },
  { key: 'tenantCode', label: '租户', value: (r) => displayText(TENANT_TEXT, r.tenantCode) },
  { key: 'question', label: '问题' },
  { key: 'answer', label: '答案' },
  { key: 'category', label: '分类' },
  { key: 'status', label: '状态', value: (r) => (r.status === 1 ? '启用' : '禁用') },
  { key: 'auditStatus', label: '审核', value: (r) => auditText(r.auditStatus) },
  { key: 'likeCount', label: '赞' },
  { key: 'dislikeCount', label: '踩' },
  { key: 'viewCount', label: '浏览' },
  { key: 'createTime', label: '创建时间' }
]

/** 按弹窗勾选的范围和字段写出 CSV */
const doExport = async ({ scope, keys }) => {
  const cols = exportFields.filter((f) => keys.includes(f.key))
  if (!cols.length) {
    ElMessage.warning('请至少勾选一个字段')
    return
  }
  try {
    let list = []
    if (scope === 'selected') {
      list = selectedRows.value
    } else if (scope === 'page') {
      list = faqList.value
    } else {
      const params = { page: 1, size: 9999 }
      if (tenantCode.value) params.tenantCode = tenantCode.value
      if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
      if (statusFilter.value !== '' && statusFilter.value != null) params.status = statusFilter.value
      if (categoryFilter.value) params.category = categoryFilter.value
      const res = await listFaqs(params)
      const data = res.data
      const rows = Array.isArray(data) ? data : (data?.records || [])
      list = categoryFilter.value ? rows.filter((item) => item.category === categoryFilter.value) : rows
    }
    if (!list.length) {
      ElMessage.warning('没有可导出的问答')
      return
    }
    const stamp = new Date().toISOString().slice(0, 10)
    downloadTextFile(`问答列表_${stamp}.csv`, rowsToCsv(list, cols))
    ElMessage.success(`已导出 ${list.length} 条`)
  } catch {
    /* 拦截器已提示 */
  }
}

const handleVectorizeFaq = async (id) => {
  try {
    const res = await vectorizeFaqApi(id)
    ElMessage.success(res?.data || '向量化完成')
    loadFaqs()
  } catch {
    /* 拦截器已提示 */
  }
}

const handleBatchVectorize = async () => {
  try {
    const res = await batchVectorizeApi(tenantCode.value || undefined)
    ElMessage.success(res?.data || '批量向量化完成')
    loadFaqs()
  } catch {
    /* 拦截器已提示 */
  }
}

/** 打开导入弹窗，默认回到粘贴输入 */
const openImport = () => {
  resetImport()
  importVisible.value = true
}

/** 关闭导入弹窗时清空两种来源的内容 */
const resetImport = () => {
  importMode.value = 'paste'
  importText.value = ''
  importFileName.value = ''
}

/** 读取所选文件到 importText，导入时再按 JSON 或纯文本分流 */
const onImportFileChange = async (uploadFile) => {
  const raw = uploadFile?.raw
  if (!raw) {
    importText.value = ''
    importFileName.value = ''
    return
  }
  importFileName.value = raw.name || ''
  try {
    importText.value = await raw.text()
  } catch {
    importText.value = ''
    ElMessage.error('读取文件失败')
  }
}

/** 打开语义检索弹窗，保留上次结果方便对照 */
const openRagDialog = () => {
  ragVisible.value = true
}

const semanticSearch = async () => {
  if (!searchQuestion.value.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
  ragSearching.value = true
  try {
    const res = await ragSearchKnowledge(searchQuestion.value.trim(), tenantCode.value || undefined)
    const data = res.data
    if (typeof data === 'string') {
      searchResult.value = data
      searchCitations.value = []
    } else {
      searchResult.value = data?.reply || ''
      searchCitations.value = data?.citations || []
    }
    loadMiss()
  } catch {
    /* 拦截器已提示 */
  } finally {
    ragSearching.value = false
  }
}

const loadMiss = async () => {
  try {
    const res = await listKnowledgeMiss({ tenantCode: tenantCode.value || undefined, page: missPage.value, size: missSize.value })
    missList.value = res.data?.records || []
    missTotal.value = res.data?.total || 0
  } catch {
    missList.value = []
  }
}

const auditText = (v) => (v === 2 ? '已发布' : v === 3 ? '已驳回' : '待审核')
const auditType = (v) => (v === 2 ? 'success' : v === 3 ? 'danger' : 'info')

const openConvertDialog = (row) => {
  convertRow.value = row
  convertForm.value = { answer: '', category: '' }
  convertVisible.value = true
}

const handleConvert = async () => {
  if (!convertForm.value.answer || !convertForm.value.answer.trim()) {
    ElMessage.warning('请填写答案')
    return
  }
  converting.value = true
  try {
    const res = await convertMissApi(convertRow.value.id, convertForm.value.answer.trim(), convertForm.value.category || undefined)
    ElMessage.success(res?.data || '已转问为问答')
    convertVisible.value = false
    loadMiss()
    loadFaqs()
  } catch {
    /* 拦截器已提示 */
  } finally {
    converting.value = false
  }
}

/** 把 JSON 数组提交给批量导入接口 */
const importJsonArray = async (payload) => {
  if (!Array.isArray(payload)) {
    ElMessage.error('必须是问答数组')
    return false
  }
  const rows = payload.map(item => ({ ...item, tenantCode: item.tenantCode || tenantCode.value }))
  const res = await importFaqs(rows)
  ElMessage.success(res.data || '导入完成')
  return true
}

/** 纯文本文件：文件名作问题，全文作答案，走单条保存 */
const importPlainText = async (text, fileName) => {
  const question = (fileName || '导入文档').replace(/\.[^.]+$/, '')
  const res = await saveFaqApi({
    tenantCode: tenantCode.value || 'default',
    question,
    answer: text,
    category: '导入',
    status: 1
  })
    ElMessage.success(res?.data || '已按问答保存文本')
}

const doImport = async () => {
  const text = (importText.value || '').trim()
  if (!text) {
    ElMessage.warning(importMode.value === 'file' ? '请先选择文件' : '请粘贴问答数据')
    return
  }
  importing.value = true
  try {
    // 能解析成数组就走批量导入；上传文件且不是对象数组时按纯文本建一条问答
    try {
      const payload = JSON.parse(text)
      const ok = await importJsonArray(payload)
      if (!ok) return
    } catch {
      if (importMode.value !== 'file') {
        ElMessage.error('数据无法解析，请检查格式')
        return
      }
      await importPlainText(text, importFileName.value)
    }
    importVisible.value = false
    resetImport()
    loadFaqs()
  } catch {
    /* 拦截器已提示 */
  } finally {
    importing.value = false
  }
}

/** 跳转到真正的文档切片入库页面 */
const goDocument = () => router.push('/knowledge/document')

const uploadFaqFile = async ({ file }) => {
  const name = (file.name || '').toLowerCase()
  try {
    const text = await file.text()
    if (name.endsWith('.json')) {
      const payload = JSON.parse(text)
      if (!Array.isArray(payload)) {
        ElMessage.error('文件必须是问答数组')
        return
      }
      const rows = payload.map(item => ({ ...item, tenantCode: item.tenantCode || tenantCode.value }))
      const res = await importFaqs(rows)
      ElMessage.success(res.data || '导入完成')
      loadFaqs()
      return
    }
    const res = await saveFaqApi({
      tenantCode: tenantCode.value || 'default',
      question: file.name.replace(/\.[^.]+$/, ''),
      answer: text,
      category: '导入',
      status: 1
    })
    ElMessage.success(res?.data || '已按问答保存文本文件')
    loadFaqs()
  } catch (e) {
    ElMessage.error('上传失败：' + (e.message || '请检查文件格式'))
  }
}

onMounted(() => {
  loadFaqs()
  loadMiss()
})
</script>

<style scoped>
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.upload-btn {
  display: inline-block;
}
.import-mode {
  margin-bottom: 12px;
}
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: nowrap;
}
.search-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.search-row .el-input {
  flex: 1;
  min-width: 200px;
}
.search-result {
  margin-top: 12px;
  padding: 12px 16px;
  background: #f7f9fc;
  border-radius: 10px;
  border: 1px solid #e7edf5;
}
.search-result p {
  color: #1c2b4a;
  line-height: 1.6;
  margin: 0;
}
.citations {
  margin-top: 8px;
}
.section-title {
  margin: 20px 0 12px;
}
.section-title h4 {
  font-size: 15px;
  color: #2a3f5f;
}
.pager {
  margin: 12px 0 4px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}
.stat-cell {
  font-size: 12px;
  color: #6b7280;
}
@media (max-width: 640px) {
  .toolbar-actions {
    width: 100%;
  }
  .filter-bar {
    flex-wrap: wrap;
  }
  .filter-bar .el-input,
  .filter-bar .el-select,
  .search-row .el-input {
    width: 100% !important;
  }
  .filter-bar .el-button {
    flex: 1;
  }
  .search-row {
    flex-direction: column;
    align-items: stretch;
  }
  .pager {
    justify-content: center;
  }
}
</style>
