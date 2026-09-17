<template>
  <div class="page-card">
    <p class="hint">
      多模态问答与检索：客户发图、发语音、发视频时怎么答，以及「用文字找图、用图找知识」这类跨模态检索。
      与「多模态」页的分工：那边管知识怎么入库，这里管入库之后怎么被问到。
    </p>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <!-- 分模态问答 -->
    <template v-if="section === 'qa'">
      <el-tabs v-model="qaMode">
        <el-tab-pane label="图片问答" name="image" />
        <el-tab-pane label="音频问答" name="audio" />
        <el-tab-pane label="视频问答" name="video" />
        <el-tab-pane label="混合问答" name="mixed" />
      </el-tabs>

      <div class="form-row">
        <el-input v-model="question" :placeholder="qaPlaceholder" clearable @keyup.enter="doQa" />
        <el-button type="primary" :loading="busy === 'qa'" @click="doQa">提问</el-button>
      </div>

      <div v-if="qaResult" class="answer-card">
        <p class="answer-text">{{ qaResult.answer }}</p>
        <div class="answer-meta">
          <el-tag size="small" type="success">置信度 {{ qaResult.confidence }}</el-tag>
          <el-tag v-for="r in qaResult.references || []" :key="r" size="small" effect="plain">{{ r }}</el-tag>
          <el-tag v-for="m in qaResult.usedModalities || []" :key="m" size="small" type="warning" effect="plain">
            用了 {{ m }}
          </el-tag>
        </div>
      </div>
    </template>

    <!-- 跨模态检索 -->
    <template v-else-if="section === 'search'">
      <p class="panel-desc">
        混合检索与跨模态检索的区别：混合检索是一次查多种模态，跨模态检索是「用 A 模态找 B 模态」，
        比如用一句话找相关的图或音频。
      </p>
      <div class="form-row">
        <el-input v-model="searchQuery" placeholder="检索语句，例如：退款流程" clearable @keyup.enter="doSearch" />
        <el-radio-group v-model="searchMode">
          <el-radio-button value="hybrid">混合检索</el-radio-button>
          <el-radio-button value="cross">跨模态检索</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :loading="busy === 'search'" @click="doSearch">检索</el-button>
      </div>

      <el-table :data="hits" stripe v-loading="busy === 'search'" empty-text="暂无命中结果" class="mini-table" table-layout="fixed">
        <el-table-column prop="title" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column label="模态" min-width="90">
          <template #default="{ row }"><TypeTag :text="mediaLabel(row.mediaType)" /></template>
        </el-table-column>
        <el-table-column prop="score" label="得分" min-width="90" />
        <el-table-column label="命中方式" min-width="110">
          <template #default="{ row }">{{ row.matchType || '—' }}</template>
        </el-table-column>
        <el-table-column prop="knowledgeId" label="资源ID" min-width="120" />
      </el-table>

      <div class="metric-row" v-if="searchStats">
        <div class="metric"><span class="m-label">图片</span><span class="m-value">{{ searchStats.imageCount }}</span></div>
        <div class="metric"><span class="m-label">音频</span><span class="m-value">{{ searchStats.audioCount }}</span></div>
        <div class="metric"><span class="m-label">视频</span><span class="m-value">{{ searchStats.videoCount }}</span></div>
        <div class="metric"><span class="m-label">向量总数</span><span class="m-value">{{ searchStats.vectorCount }}</span></div>
        <div class="metric"><span class="m-label">累计检索</span><span class="m-value">{{ searchStats.searchCount }}</span></div>
        <div class="metric"><span class="m-label">平均耗时</span><span class="m-value">{{ searchStats.avgElapsedMs }} ms</span></div>
      </div>
    </template>

    <!-- 视频分析 -->
    <template v-else>
      <p class="panel-desc">
        视频内容分析：按时间轴切出场景段落并生成摘要，视频入库后就能被「讲到了什么」检索到。
      </p>
      <div class="form-row">
        <el-input v-model="videoId" placeholder="视频资源ID" style="width: 220px" />
        <el-button type="primary" :loading="busy === 'video'" @click="doAnalyzeVideo">开始分析</el-button>
      </div>

      <template v-if="videoResult">
        <el-descriptions :column="3" border size="small" class="mini-table">
          <el-descriptions-item label="总时长">{{ videoResult.duration }} 秒</el-descriptions-item>
          <el-descriptions-item label="场景数">{{ (videoResult.scenes || []).length }}</el-descriptions-item>
          <el-descriptions-item label="含字幕">{{ videoResult.hasSubtitle ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert type="success" :closable="false" class="mini-table" :title="videoResult.summary" />
        <el-table :data="videoResult.scenes || []" stripe class="mini-table" table-layout="fixed">
          <el-table-column label="时间段" min-width="150">
            <template #default="{ row }">{{ row.start }} - {{ row.end }} 秒</template>
          </el-table-column>
          <el-table-column prop="label" label="场景内容" min-width="280" />
        </el-table>
      </template>
    </template>

    <!-- 详情 -->
    <el-dialog title="多模态知识详情" v-model="detailVisible" width="560px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="知识ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="模态">{{ mediaLabel(detail.mediaType) }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="已向量化">{{ detail.vectorized === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ detail.description }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
/** 多模态问答与检索：分模态问答、跨模态检索、视频分析 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SectionSwitch from '../../components/SectionSwitch.vue'
import {
  analyzeVideo, crossModalSearch, getMultimodalDetail, getMultimodalSearchStats,
  multimodalAudioQa, multimodalHybridSearch, multimodalImageQa, multimodalMixedQa,
  multimodalVideoQa
} from '../../api'

const section = ref('qa')
const sectionOptions = [
  { value: 'qa', label: '分模态问答' },
  { value: 'search', label: '跨模态检索' },
  { value: 'video', label: '视频分析' }
]

const busy = ref('')
const qaMode = ref('image')
const question = ref('这张图里的订单可以退款吗？')
const qaResult = ref(null)
const searchMode = ref('hybrid')
const searchQuery = ref('退款流程')
const hits = ref([])
const searchStats = ref(null)
const videoId = ref('vid-4001')
const videoResult = ref(null)
const detail = ref(null)
const detailVisible = ref(false)

const qaPlaceholder = computed(() => ({
  image: '例如：这张图里的订单可以退款吗？',
  audio: '例如：这段录音里客户想做什么？',
  video: '例如：这个视频讲的是哪类问题？',
  mixed: '例如：结合我发的图和描述，我能退款吗？'
}[qaMode.value]))

const mediaLabel = (t) => ({ image: '图片', audio: '音频', video: '视频', text: '文本' }[t] || t)

/** 四种模态的问答走不同接口，这里按当前选项分发 */
const QA_FN = {
  image: multimodalImageQa,
  audio: multimodalAudioQa,
  video: multimodalVideoQa,
  mixed: multimodalMixedQa
}

const doQa = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  busy.value = 'qa'
  try {
    const res = await QA_FN[qaMode.value]({ question: question.value.trim(), knowledgeId: 'img-2001' })
    qaResult.value = res.data
  } catch {
    qaResult.value = null
  } finally {
    busy.value = ''
  }
}

const doSearch = async () => {
  busy.value = 'search'
  try {
    const fn = searchMode.value === 'hybrid' ? multimodalHybridSearch : crossModalSearch
    const res = await fn({ query: searchQuery.value })
    hits.value = res.data?.hits || []
  } catch {
    hits.value = []
  } finally {
    busy.value = ''
  }
}

const loadStats = async () => {
  try {
    const res = await getMultimodalSearchStats()
    searchStats.value = res.data
  } catch {
    searchStats.value = null
  }
}

const doAnalyzeVideo = async () => {
  busy.value = 'video'
  try {
    const res = await analyzeVideo({ knowledgeId: videoId.value })
    videoResult.value = res.data
    ElMessage.success(res.msg || '分析完成')
  } catch {
    videoResult.value = null
  } finally {
    busy.value = ''
  }
}

onMounted(loadStats)
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
  flex-wrap: wrap;
}
.answer-card {
  padding: 14px 16px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #d1fadf;
  max-width: 760px;
  margin-top: 10px;
}
.answer-text { margin: 0 0 10px; font-size: 13.5px; line-height: 1.8; color: #1c2b4a; }
.answer-meta { display: flex; gap: 6px; flex-wrap: wrap; }
.mini-table { margin: 12px 0; max-width: 880px; }
.metric-row { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 14px; }
.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 16px;
  border-radius: 8px;
  background: #f7f9fc;
  min-width: 104px;
}
.m-label { font-size: 12px; color: #98a2b3; }
.m-value { font-size: 15px; font-weight: 700; color: #1c2b4a; }
</style>
