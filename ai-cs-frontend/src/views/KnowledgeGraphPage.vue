<template>
  <div class="page-card">
    <p class="hint">从文本抽取实体与关系构建图谱，用于 GraphRAG 问答。</p>
    <!-- 顶栏切片已标明本页，不再重复「知识图谱」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="table-actions">
        <el-button type="primary" :loading="building" @click="build">构建图谱</el-button>
        <el-button :loading="extracting" @click="extract">抽取实体</el-button>
      </div>
    </div>

    <el-card shadow="never" class="source-card">
      <template #header>
        <span class="card-title">文本来源</span>
      </template>
      <el-radio-group v-model="sourceType" @change="onSourceTypeChange">
        <el-radio value="manual">手动输入</el-radio>
        <el-radio value="faq">引用知识库 FAQ</el-radio>
      </el-radio-group>

      <div class="source-body">
        <el-input
          v-if="sourceType === 'manual'"
          v-model="manualText"
          type="textarea"
          :rows="6"
          placeholder="粘贴需要抽取的文本，例如：退款政策全文、产品说明等"
        />
        <template v-else>
          <el-select
            v-model="faqId"
            filterable
            clearable
            placeholder="选择一条 FAQ，用它的答案作为抽取文本"
            style="width: 100%"
          >
            <el-option v-for="f in faqs" :key="f.id" :label="f.question" :value="f.id" />
          </el-select>
          <div v-if="faqPreview" class="faq-preview">{{ faqPreview }}</div>
        </template>
      </div>
      <div class="source-tip">
        当前文本长度：{{ resolvedText.length }} 字
        <span v-if="!resolvedText.length" class="warn"> · 构建/抽取前请先提供文本</span>
      </div>
    </el-card>

    <el-form inline>
      <el-form-item label="关键词">
        <el-input v-model="keyword" placeholder="实体 / 关系" clearable style="width: 200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button @click="load">查询节点</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="nodes" stripe v-loading="loading" empty-text="暂无图谱节点" table-layout="fixed" max-height="560">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="实体" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" min-width="110">
        <template #default="{ row }">
          <TypeTag :text="row.type || row.entityType || '未知'" />
        </template>
      </el-table-column>
      <el-table-column prop="relation" label="关系" min-width="140" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" min-width="160" show-overflow-tooltip />
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog title="抽取结果" v-model="resultVisible" width="680px">
      <pre class="result-box">{{ extractResult }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listKnowledgeGraphNodes, buildKnowledgeGraph, extractKnowledgeGraph, listFaqs } from '../api'
import TablePager from '../components/TablePager.vue'
import TypeTag from '../components/TypeTag.vue'
import { useClientPager } from '../composables/useClientPager'

const list = ref([])
const { page, size, total, records: nodes } = useClientPager(list)
const loading = ref(false)
const building = ref(false)
const extracting = ref(false)
const keyword = ref('')

const sourceType = ref('manual')
const manualText = ref('')
const faqs = ref([])
const faqId = ref(null)
const resultVisible = ref(false)
const extractResult = ref('')

/** 实际送去抽取的文本：手动输入优先，否则取所选 FAQ 的答案 */
const resolvedText = computed(() => {
  if (sourceType.value === 'manual') return manualText.value.trim()
  const f = faqs.value.find((i) => i.id === faqId.value)
  return f ? String(f.answer || '').trim() : ''
})

const faqPreview = computed(() => {
  const f = faqs.value.find((i) => i.id === faqId.value)
  if (!f) return ''
  const text = String(f.answer || '')
  return text.length > 300 ? text.slice(0, 300) + '…' : text
})

const onSourceTypeChange = () => {
  /* 切换来源时清空另一侧的残余选择，避免误用 */
  if (sourceType.value === 'manual') faqId.value = null
  else manualText.value = ''
}

const load = async () => {
  loading.value = true
  try {
    const res = await listKnowledgeGraphNodes({ keyword: keyword.value })
    const data = res.data
    list.value = Array.isArray(data) ? data : (data?.nodes || [])
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const loadFaqs = async () => {
  try {
    const res = await listFaqs({ page: 1, size: 200 })
    const data = res.data
    faqs.value = Array.isArray(data) ? data : (data?.records || [])
  } catch {
    faqs.value = []
  }
}

const build = async () => {
  if (!resolvedText.value) {
    ElMessage.warning('请先提供用于构建的文本')
    return
  }
  building.value = true
  try {
    const res = await buildKnowledgeGraph({ text: resolvedText.value })
    ElMessage.success(res?.data || '图谱构建完成')
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    building.value = false
  }
}

const extract = async () => {
  if (!resolvedText.value) {
    ElMessage.warning('请先提供用于抽取的文本')
    return
  }
  extracting.value = true
  try {
    const res = await extractKnowledgeGraph({ text: resolvedText.value })
    const data = res.data
    extractResult.value = typeof data === 'string' ? data : JSON.stringify(data, null, 2)
    resultVisible.value = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    extracting.value = false
  }
}

onMounted(() => {
  load()
  loadFaqs()
})
</script>

<style scoped>
.source-card { margin-bottom: 16px; }
.card-title { font-weight: 600; color: #1c2b4a; }
.source-body { margin-top: 12px; }
.faq-preview {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
  font-size: 12px;
  line-height: 1.7;
  color: #4b5563;
  max-height: 160px;
  overflow: auto;
}
.source-tip { margin-top: 10px; font-size: 12px; color: #6b7280; }
.source-tip .warn { color: #e6a23c; }
.result-box {
  max-height: 420px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f7f8fa;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
