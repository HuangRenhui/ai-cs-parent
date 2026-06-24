<template>
  <div class="knowledge-container">
    <div class="header">
      <h2>知识库管理</h2>
      <div class="actions">
        <el-button type="primary" @click="showAddDialog = true">新增FAQ</el-button>
        <el-button @click="batchVectorize">批量向量化</el-button>
        <el-upload
          class="upload-btn"
          :action="'/knowledge/upload'"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          accept=".txt,.md,.docx,.pdf"
        >
          <el-button type="success">上传文档</el-button>
        </el-upload>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
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
      </div>
    </div>

    <el-table :data="faqList" border>
      <el-table-column prop="id" label="ID" />
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

    <!-- 添加/编辑FAQ弹窗 -->
    <el-dialog :title="isEdit ? '编辑FAQ' : '新增FAQ'" v-model="showAddDialog">
      <el-form :model="form" label-width="80px">
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'

const faqList = ref([])
const showAddDialog = ref(false)
const isEdit = ref(false)
const searchQuestion = ref('')
const searchResult = ref('')
const searchKeyword = ref('')
const categoryFilter = ref('')
const statusFilter = ref('')
const categories = ref([])

const form = ref({
  id: '',
  question: '',
  answer: '',
  category: '',
  status: 1
})

const loadFaqs = async () => {
  try {
    const params = {}
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (categoryFilter.value) params.category = categoryFilter.value
    if (statusFilter.value !== '') params.status = statusFilter.value
    const response = await request.get('/knowledge/list', { params })
    faqList.value = response.data
    loadCategories()
  } catch (error) {
    console.error('获取FAQ列表失败:', error)
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
    form.value = { id: '', question: '', answer: '', category: '', status: 1 }
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
    const response = await request.post('/knowledge/vectorize/batch')
    alert(response.data)
    loadFaqs()
  } catch (error) {
    console.error('批量向量化失败:', error)
  }
}

const semanticSearch = async () => {
  if (!searchQuestion.value.trim()) return
  try {
    const response = await request.get('/knowledge/search', {
      params: { question: searchQuestion.value }
    })
    searchResult.value = response.data
  } catch (error) {
    console.error('语义检索失败:', error)
  }
}

const handleUploadSuccess = (response) => {
  alert('文件上传成功')
  loadFaqs()
}

const handleUploadError = (error) => {
  console.error('文件上传失败:', error)
  alert('文件上传失败')
}

onMounted(loadFaqs)
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
}
.upload-btn {
  display: inline-block;
}
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
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
</style>