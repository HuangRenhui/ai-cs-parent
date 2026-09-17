<template>
  <div>
    <p class="panel-desc">
      GraphRAG = 向量检索 + 图谱扩展。先用向量找到候选，再顺着图把相关实体一起带进来，
      解决「客户问退款，但知识库里写的是到账时效」这种字面对不上、语义上相关的情况。
    </p>

    <div class="metric-row" v-if="stats">
      <div class="metric"><span class="m-label">累计问答</span><span class="m-value">{{ stats.qaCount }}</span></div>
      <div class="metric"><span class="m-label">平均实体数</span><span class="m-value">{{ stats.avgEntities }}</span></div>
      <div class="metric"><span class="m-label">平均路径数</span><span class="m-value">{{ stats.avgPaths }}</span></div>
      <div class="metric"><span class="m-label">平均耗时</span><span class="m-value">{{ stats.avgElapsedMs }} ms</span></div>
      <div class="metric"><span class="m-label">命中率</span><span class="m-value ok">{{ (stats.hitRate * 100).toFixed(0) }}%</span></div>
    </div>

    <h5 class="sub-title">问答</h5>
    <div class="form-row">
      <el-input v-model="question" placeholder="例如：退款多久能到账？" clearable @keyup.enter="ask" />
      <el-button type="primary" :loading="busy === 'qa'" @click="ask">提问</el-button>
    </div>
    <div v-if="answer" class="answer-card">
      <p class="answer-text">{{ answer.answer }}</p>
      <div class="answer-meta">
        <el-tag size="small" type="success">置信度 {{ answer.confidence }}</el-tag>
        <el-tag v-for="e in answer.entities" :key="e" size="small" effect="plain">{{ e }}</el-tag>
      </div>
      <p class="muted">图谱路径：{{ (answer.graphPaths || []).map((p) => p.join(' → ')).join('；') }}</p>
    </div>

    <el-tabs v-model="tab" class="mt">
      <el-tab-pane label="实体增强检索" name="entity">
        <p class="muted">把问题里的实体识别出来，再沿图扩展一圈相关概念，扩大召回面。</p>
        <div class="form-row">
          <el-input v-model="query" placeholder="检索语句" />
          <el-button type="primary" :loading="busy === 'entity'" @click="entityRetrieval">检索</el-button>
        </div>
        <template v-if="entityResult">
          <div class="chip-row">
            <span class="chip-label">识别实体</span>
            <el-tag v-for="e in entityResult.entities" :key="e" size="small">{{ e }}</el-tag>
          </div>
          <div class="chip-row">
            <span class="chip-label">图扩展</span>
            <el-tag v-for="e in entityResult.expanded" :key="e" size="small" type="warning" effect="plain">{{ e }}</el-tag>
          </div>
          <el-table :data="entityResult.hits" stripe class="mini-table" table-layout="fixed">
            <el-table-column prop="doc" label="召回内容" min-width="220" show-overflow-tooltip />
            <el-table-column prop="score" label="得分" min-width="90" />
            <el-table-column prop="from" label="来源" min-width="110">
              <template #default="{ row }"><TypeTag :text="row.from" /></template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <el-tab-pane label="引导式检索" name="guided">
        <p class="muted">按图谱路径一步步收敛，每步只保留最相关的一小批，避免一次召回太多噪音。</p>
        <el-button type="primary" :loading="busy === 'guided'" @click="guided">开始引导检索</el-button>
        <template v-if="guidedResult">
          <p class="muted">路径：{{ (guidedResult.guide || []).join(' → ') }}</p>
          <el-table :data="guidedResult.stepHits || []" stripe class="mini-table" table-layout="fixed">
            <el-table-column prop="step" label="步骤" min-width="150" />
            <el-table-column prop="hits" label="剩余候选" min-width="110" />
          </el-table>
        </template>
      </el-tab-pane>

      <el-tab-pane label="增强排序" name="ranking">
        <p class="muted">最终排序同时看向量得分和图谱得分，两者都高才排前面，减少「字面像但实际不相关」。</p>
        <el-button type="primary" :loading="busy === 'ranking'" @click="ranking">查看排序</el-button>
        <el-table :data="rankingRows" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="doc" label="内容" min-width="220" show-overflow-tooltip />
          <el-table-column prop="vectorScore" label="向量分" min-width="90" />
          <el-table-column prop="graphScore" label="图谱分" min-width="90" />
          <el-table-column label="最终分" min-width="100">
            <template #default="{ row }">
              <span class="final-score">{{ row.finalScore }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/** GraphRAG：图谱增强的问答与检索 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getGraphRagStats, graphRagEnhancedRanking, graphRagEntityRetrieval,
  graphRagGuidedRetrieval, graphRagQa
} from '../../../api'

const tab = ref('entity')
const busy = ref('')
const stats = ref(null)
const question = ref('退款多久能到账？')
const answer = ref(null)
const query = ref('退款多久到账')
const entityResult = ref(null)
const guidedResult = ref(null)
const rankingRows = ref([])

const loadStats = async () => {
  try {
    const res = await getGraphRagStats()
    stats.value = res.data
  } catch {
    stats.value = null
  }
}

const ask = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  busy.value = 'qa'
  try {
    const res = await graphRagQa({ question: question.value.trim() })
    answer.value = res.data
    loadStats()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const entityRetrieval = async () => {
  busy.value = 'entity'
  try {
    const res = await graphRagEntityRetrieval({ query: query.value })
    entityResult.value = res.data
  } catch {
    entityResult.value = null
  } finally {
    busy.value = ''
  }
}

const guided = async () => {
  busy.value = 'guided'
  try {
    const res = await graphRagGuidedRetrieval({ query: query.value })
    guidedResult.value = res.data
  } catch {
    guidedResult.value = null
  } finally {
    busy.value = ''
  }
}

const ranking = async () => {
  busy.value = 'ranking'
  try {
    const res = await graphRagEnhancedRanking({ query: query.value })
    rankingRows.value = Array.isArray(res.data) ? res.data : []
  } catch {
    rankingRows.value = []
  } finally {
    busy.value = ''
  }
}

onMounted(loadStats)
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.sub-title { margin: 18px 0 10px; font-size: 13.5px; color: #1c2b4a; }
.metric-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 16px;
  border-radius: 8px;
  background: #f7f9fc;
  min-width: 108px;
}
.m-label { font-size: 12px; color: #98a2b3; }
.m-value { font-size: 15px; font-weight: 700; color: #1c2b4a; }
.m-value.ok { color: #067647; }
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  max-width: 720px;
  margin-bottom: 10px;
}
.answer-card {
  padding: 14px 16px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #d1fadf;
  max-width: 720px;
}
.answer-text { margin: 0 0 10px; font-size: 13.5px; line-height: 1.8; color: #1c2b4a; }
.answer-meta { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 6px; }
.mt { margin-top: 8px; }
.mini-table { margin: 10px 0; max-width: 780px; }
.chip-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin: 8px 0;
}
.chip-label { font-size: 12.5px; color: #667085; margin-right: 4px; }
.final-score { font-weight: 700; color: #1f4fd8; }
.muted { color: #98a2b3; font-size: 12.5px; margin: 0 0 8px; }
</style>
