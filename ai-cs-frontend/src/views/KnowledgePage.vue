<template>
  <div class="knowledge-container">
    <div class="header">
      <h2>知识库管理</h2>
      <div class="actions">
        <el-button type="primary" @click="showAddDialog = true">新增FAQ</el-button>
        <el-button @click="batchVectorize">批量向量化</el-button>
        <el-button @click="importVisible = true">导入 JSON</el-button>
        <el-upload
          class="upload-btn"
          :show-file-list="false"
          accept=".json,.txt,.md"
          :http-request="uploadFaqFile"
        >
          <el-button type="success">上传文档</el-button>
        </el-upload>
      </div>
    </div>

    <div class="health" v-if="health">
      向量库 {{ health.ready ? '就绪' : '不可用' }} · 集合 {{ health.collection }} · Embedding {{ health.embeddingModel }}
      <span v-if="!health.ready">（{{ health.milvus }}）</span>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="tenantCode" placeholder="租户编码，默认 default" style="width: 180px;" />
      <el-input v-model="searchKeyword" placeholder="搜索问题或答案" style="width: 300px;" />
      <el-select v-model="categoryFilter" placeholder="分类筛选">
        <el-option label="全部" value="" />
        <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="状态筛选">
        <el-option label="全部" value="" />
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="loadFaqs">搜索</el-button>
    </div>

    <!-- 语义检索 -->
    <div class="search-section">
      <el-input
        v-model="searchQuestion"
        placeholder="输入问题进行语义检索..."
        style="width: 400px;"
        @keyup.enter="semanticSearch"
      />
      <el-button type="primary" @click="semanticSearch">语义检索</el-button>
      <div v-if="searchResult" class="search-result">
        <h4>检索结果：</h4>
        <p>{{ searchResult }}</p>
        <div v-if="searchCitations.length" class="citations">
          <el-tag v-for="c in searchCitations" :key="c.faqId" size="small" style="margin-right: 6px;">
            #{{ c.faqId }} {{ c.question }}
          </el-tag>
        </div>
      </div>
    </div>

    <el-table :data="faqList" border empty-text="暂无 FAQ 或加载失败">
      <el-table-column prop="id" label="ID" />
      <el-table-column prop="tenantCode" label="租户" width="100" />
      <el-table-column prop="question" label="问题" />
      <el-table-column prop="answer" label="答案" />
      <el-table-column prop="category" label="分类" />
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="milvusId" label="Milvus ID" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editFaq(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteFaq(scope.row.id)">删除</el-button>
          <el-button size="small" :type="scope.row.milvusId ? 'warning' : 'primary'" @click="vectorizeFaq(scope.row.id)">
            {{ scope.row.milvusId ? '重新向量化' : '向量化' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3 class="miss-title">未命中回收</h3>
    <el-table :data="missList" stripe empty-text="暂无未命中记录" size="small">
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
    <el-dialog :title="isEdit ? '编辑FAQ' : '新增FAQ'" v-model="showAddDialog">
      <el-form :model="form" label-width="80px">
        <el-form-item label="租户">
          <el-input v-model="form.tenantCode" placeholder="default" />
        </el-form-item>
        <el-form-item label="问题">
          <el-input v-model="form.question" />
        </el-form-item>
        <el-form-item label="答案">
          <el-textarea v-model="form.answer" rows="4" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择或输入">
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
        <el-button type="primary" @click="saveFaq">确定</el-button>
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { importFaqs, listKnowledgeMiss, getKnowledgeHealth } from '../api'

const faqList = ref([])
const showAddDialog = ref(false)
const isEdit = ref(false)
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

const form = ref({
  id: '',
  tenantCode: 'default',
  question: '',
  answer: '',
  category: '',
  status: 1
})

const loadFaqs = async () => {
  try {
    const params = {}
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (tenantCode.value) params.tenantCode = tenantCode.value
    if (categoryFilter.value) params.category = categoryFilter.value
    if (statusFilter.value !== '') params.status = statusFilter.value
    const response = await request.get('/knowledge/list', { params })
    const data = response.data
    faqList.value = Array.isArray(data) ? data : (data?.records || [])
    loadCategories()
    loadMiss()
  } catch (error) {
    faqList.value = []
  }
}

const loadCategories = () => {
  const cats = [...new Set(faqList.value.map(item => item.category).filter(Boolean))]
  categories.value = cats
}

const saveFaq = async () => {
  try {
    if (isEdit.value) {
      await request.put('/knowledge/update', form.value)
      alert('修改成功')
    } else {
      await request.post('/knowledge/save', form.value)
      alert('保存成功')
    }
    showAddDialog.value = false
    form.value = { id: '', tenantCode: tenantCode.value || 'default', question: '', answer: '', category: '', status: 1 }
    isEdit.value = false
    loadFaqs()
  } catch (error) {
    console.error('保存FAQ失败:', error)
  }
}

const editFaq = (row) => {
  isEdit.value = true
  form.value = { ...row }
  showAddDialog.value = true
}

const deleteFaq = async (id) => {
  if (!confirm('确定要删除这个FAQ吗？')) return
  try {
    await request.delete(`/knowledge/delete/${id}`)
    loadFaqs()
    alert('删除成功')
  } catch (error) {
    console.error('删除FAQ失败:', error)
  }
}

const vectorizeFaq = async (id) => {
  try {
    const response = await request.post(`/knowledge/vectorize/${id}`)
    alert(response.data)
    loadFaqs()
  } catch (error) {
    console.error('向量化失败:', error)
  }
}

const batchVectorize = async () => {
  try {
    const response = await request.post('/knowledge/vectorize/batch', null, {
      params: { tenantCode: tenantCode.value }
    })
    alert(response.data)
    loadFaqs()
  } catch (error) {
    console.error('批量向量化失败:', error)
  }
}

const semanticSearch = async () => {
  if (!searchQuestion.value.trim()) return
  try {
    const response = await request.get('/knowledge/rag/search', {
      params: { question: searchQuestion.value, tenantCode: tenantCode.value }
    })
    const data = response.data
    if (typeof data === 'string') {
      searchResult.value = data
      searchCitations.value = []
    } else {
      searchResult.value = data?.reply || ''
      searchCitations.value = data?.citations || []
    }
    loadMiss()
  } catch (error) {
    console.error('语义检索失败:', error)
  }
}

const loadMiss = async () => {
  try {
    const res = await listKnowledgeMiss({ tenantCode: tenantCode.value, page: missPage.value, size: 10 })
    missList.value = res.data?.records || []
    missTotal.value = res.data?.total || 0
  } catch (error) {
    console.error('获取未命中记录失败:', error)
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
    loadFaqs()
  } catch (error) {
    console.error('导入失败:', error)
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
    await request.post('/knowledge/save', {
      tenantCode: tenantCode.value || 'default',
      question: file.name.replace(/\.[^.]+$/, ''),
      answer: text,
      category: '导入',
      status: 1
    })
    ElMessage.success('已按 FAQ 保存文本文件')
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
.knowledge-container {
  padding: 16px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.header h2 {
  margin: 0;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.upload-btn {
  display: inline-block;
}
.health {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 12px;
}
.search-bar {
  display: flex;
  gap: 12px;
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
.search-result {
  margin-top: 16px;
  padding: 16px;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
}
.miss-title {
  margin: 20px 0 12px;
  font-size: 16px;
}
.pager {
  margin: 12px 0 20px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}
@media (max-width: 640px) {
  .knowledge-container {
    padding: 12px;
  }
  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .header h2 {
    font-size: 18px;
  }
  .search-bar,
  .search-section {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .search-bar .el-input,
  .search-bar .el-select,
  .search-section .el-input {
    width: 100% !important;
  }
  .search-section {
    padding: 12px;
  }
  .pager {
    justify-content: center;
  }
  :deep(.el-table) {
    font-size: 13px;
  }
}
</style>