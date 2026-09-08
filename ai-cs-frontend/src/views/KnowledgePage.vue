<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>知识库管理</h3>
      <div class="toolbar-actions">
        <el-button type="primary" @click="openAddDialog">新增FAQ</el-button>
        <el-button @click="handleBatchVectorize">批量向量化</el-button>
        <el-button @click="importVisible = true">导入 JSON</el-button>
        <el-upload class="upload-btn" :show-file-list="false" accept=".json,.txt,.md" :http-request="uploadFaqFile">
          <el-button type="success">上传文档</el-button>
        </el-upload>
      </div>
    </div>

    <div class="health-tip" v-if="health">
      <el-tag :type="health.ready ? 'success' : 'danger'" size="small">
        向量库 {{ health.ready ? '就绪' : '不可用' }}
      </el-tag>
      <span class="health-text">
        集合 {{ health.collection }} · Embedding {{ health.embeddingModel }}
        <template v-if="!health.ready">（{{ health.milvus }}）</template>
      </span>
    </div>

    <div class="filter-bar">
      <el-input v-model="tenantCode" placeholder="租户编码，默认 default" style="width: 200px" />
      <el-input v-model="searchKeyword" placeholder="搜索问题或答案" clearable style="width: 240px" @keyup.enter="loadFaqs" />
      <el-select v-model="categoryFilter" placeholder="分类筛选" clearable style="width: 140px">
        <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 130px">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="loadFaqs">查询</el-button>
    </div>

    <!-- 语义检索 -->
    <div class="search-section">
      <div class="section-label">语义检索（Milvus RAG）</div>
      <div class="search-row">
        <el-input v-model="searchQuestion" placeholder="输入问题进行语义检索..." clearable @keyup.enter="semanticSearch" />
        <el-button type="primary" @click="semanticSearch">语义检索</el-button>
      </div>
      <div v-if="searchResult" class="search-result">
        <p>{{ searchResult }}</p>
        <div v-if="searchCitations.length" class="citations">
          <el-tag v-for="c in searchCitations" :key="c.faqId" size="small" style="margin-right: 6px;">
            #{{ c.faqId }} {{ c.question }}
          </el-tag>
        </div>
      </div>
    </div>

    <el-table :data="faqList" stripe v-loading="loading" empty-text="暂无 FAQ 或加载失败">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="tenantCode" label="租户" width="100" />
      <el-table-column prop="question" label="问题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" min-width="200" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="90" />
      <el-table-column label="状态" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="milvusId" label="Milvus ID" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="editFaq(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDeleteFaq(scope.row.id)">删除</el-button>
          <el-button size="small" :type="scope.row.milvusId ? 'warning' : 'primary'" @click="handleVectorizeFaq(scope.row.id)">
            {{ scope.row.milvusId ? '重新向量化' : '向量化' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-title">
      <h4>未命中回收</h4>
    </div>
    <el-table :data="missList" size="small" stripe empty-text="暂无未命中记录">
      <el-table-column prop="createTime" label="时间" width="180" />
      <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
      <el-table-column prop="topScore" label="最高分" width="90" />
      <el-table-column prop="sessionId" label="会话" min-width="140" show-overflow-tooltip />
    </el-table>
    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="missTotal"
        v-model:current-page="missPage"
        :page-size="10"
        @current-change="loadMiss"
      />
    </div>

    <!-- 添加/编辑FAQ弹窗 -->
    <el-dialog :title="isEdit ? '编辑FAQ' : '新增FAQ'" v-model="showAddDialog" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="租户">
          <el-input v-model="form.tenantCode" placeholder="default" />
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
          <el-select v-model="form.status">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveFaq">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog title="导入 FAQ JSON" v-model="importVisible" width="640px">
      <p class="hint">数组，每项含 question、answer，可选 category / tenantCode / status。单次最多 200 条。</p>
      <el-input v-model="importText" type="textarea" :rows="10" placeholder='[{"question":"如何退款？","answer":"7天无理由…","category":"售后"}]' />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchVectorize as batchVectorizeApi,
  deleteFaq as deleteFaqApi,
  getKnowledgeHealth,
  importFaqs,
  listFaqs,
  listKnowledgeMiss,
  ragSearchKnowledge,
  saveFaq as saveFaqApi,
  updateFaq as updateFaqApi,
  vectorizeFaq as vectorizeFaqApi
} from '../api'

const faqList = ref([])
const showAddDialog = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const searchQuestion = ref('')
const searchResult = ref('')
const searchCitations = ref([])
const searchKeyword = ref('')
const tenantCode = ref('default')
const categoryFilter = ref('')
const statusFilter = ref('')
const categories = ref([])
const missList = ref([])
const missPage = ref(1)
const missTotal = ref(0)
const importVisible = ref(false)
const importText = ref('')
const importing = ref(false)
const health = ref(null)

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
    const params = {}
    if (tenantCode.value) params.tenantCode = tenantCode.value
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    if (statusFilter.value !== '' && statusFilter.value != null) params.status = statusFilter.value
    if (categoryFilter.value) params.category = categoryFilter.value
    const res = await listFaqs(params)
    const data = res.data
    const rows = Array.isArray(data) ? data : (data?.records || [])
    // 后端 list 暂不支持 category 条件，前端过滤兜底
    faqList.value = categoryFilter.value
      ? rows.filter(item => item.category === categoryFilter.value)
      : rows
    loadCategories()
    loadMiss()
  } catch {
    faqList.value = []
  } finally {
    loading.value = false
  }
}

const loadCategories = () => {
  const cats = [...new Set(faqList.value.map(item => item.category).filter(Boolean))]
  categories.value = cats
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
    await ElMessageBox.confirm('确定要删除这个FAQ吗？删除后将同步清理向量数据。', '提示', { type: 'warning' })
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

const semanticSearch = async () => {
  if (!searchQuestion.value.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
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
  }
}

const loadMiss = async () => {
  try {
    const res = await listKnowledgeMiss({ tenantCode: tenantCode.value || undefined, page: missPage.value, size: 10 })
    missList.value = res.data?.records || []
    missTotal.value = res.data?.total || 0
  } catch {
    missList.value = []
  }
}

const doImport = async () => {
  let payload
  try {
    payload = JSON.parse(importText.value)
  } catch (e) {
    ElMessage.error('JSON 无法解析')
    return
  }
  if (!Array.isArray(payload)) {
    ElMessage.error('必须是 JSON 数组')
    return
  }
  importing.value = true
  try {
    const rows = payload.map(item => ({ ...item, tenantCode: item.tenantCode || tenantCode.value }))
    const res = await importFaqs(rows)
    ElMessage.success(res.data || '导入完成')
    importVisible.value = false
    importText.value = ''
    loadFaqs()
  } catch {
    /* 拦截器已提示 */
  } finally {
    importing.value = false
  }
}

const uploadFaqFile = async ({ file }) => {
  const name = (file.name || '').toLowerCase()
  try {
    const text = await file.text()
    if (name.endsWith('.json')) {
      const payload = JSON.parse(text)
      if (!Array.isArray(payload)) {
        ElMessage.error('JSON 必须是 FAQ 数组')
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
    ElMessage.success(res?.data || '已按 FAQ 保存文本文件')
    loadFaqs()
  } catch (e) {
    ElMessage.error('上传失败：' + (e.message || '请检查文件格式'))
  }
}

const loadHealth = async () => {
  try {
    const res = await getKnowledgeHealth()
    health.value = res.data
  } catch (e) {
    health.value = { ready: false, milvus: '知识库服务不可达', collection: '-', embeddingModel: '-' }
  }
}

onMounted(() => {
  loadHealth()
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
.health-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
}
.health-text {
  color: #6b7280;
}
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}
.search-section {
  margin-bottom: 16px;
  padding: 16px;
  background-color: #f0f5ff;
  border-radius: 8px;
}
.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #2a3f5f;
  margin-bottom: 10px;
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
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}
.search-result p {
  color: #303133;
  line-height: 1.6;
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
@media (max-width: 640px) {
  .toolbar-actions {
    width: 100%;
  }
  .filter-bar .el-input,
  .filter-bar .el-select,
  .search-row .el-input {
    width: 100% !important;
  }
  .filter-bar .el-button {
    width: 100%;
  }
  .search-section {
    padding: 12px;
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
