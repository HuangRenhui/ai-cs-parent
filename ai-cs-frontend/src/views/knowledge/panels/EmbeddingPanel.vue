<template>
  <div>
    <p class="panel-desc">
      把节点转成向量后，可以算「哪些节点该连却没连」（补链接）、「哪些节点该合却分着」（聚类），
      也能按向量找语义相近的节点。换 embedding 模型后需要重新生成。
    </p>

    <div class="page-toolbar is-actions-only">
      <div class="table-actions">
        <el-button type="primary" :loading="busy === 'generate'" @click="generate">生成 / 重建向量</el-button>
        <el-button :loading="busy === 'cluster'" @click="cluster">节点聚类</el-button>
        <el-button :loading="busy === 'missing'" @click="loadMissing">待补链接</el-button>
        <el-button :loading="busy === 'export'" @click="doExport">导出向量</el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="节点向量" name="node">
        <div class="form-row">
          <el-input v-model="nodeId" placeholder="节点ID，例如 n-1" style="width: 200px" />
          <el-button type="primary" :loading="busy === 'node'" @click="loadNode">查看向量</el-button>
          <el-button :loading="busy === 'similar'" @click="loadSimilar">相似节点</el-button>
          <el-button :loading="busy === 'predict'" @click="predict">预测链接</el-button>
        </div>
        <el-alert v-if="nodeEmbedding" type="info" :closable="false" class="mini-table">
          <template #title>
            节点 {{ nodeEmbedding.nodeId }}　维度 {{ nodeEmbedding.dimension }}
          </template>
          <template #default>
            <code class="vec">{{ (nodeEmbedding.vector || []).join(', ') }} …</code>
            <p class="muted">{{ nodeEmbedding.note }}</p>
          </template>
        </el-alert>
        <el-table v-if="similar.length" :data="similar" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="name" label="相似节点" min-width="140" />
          <el-table-column prop="label" label="类型" min-width="110" />
          <el-table-column prop="similarity" label="相似度" min-width="100" />
        </el-table>
        <el-table v-if="predictions.length" :data="predictions" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="target" label="可能关联的节点" min-width="160" />
          <el-table-column prop="score" label="可能性" min-width="100" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="待补链接" name="missing">
        <p class="muted">向量上很接近但图中没有直连的节点对，通常意味着关系漏标。</p>
        <el-table :data="missing" stripe v-loading="busy === 'missing'" class="mini-table" table-layout="fixed">
          <el-table-column prop="source" label="节点 A" min-width="140" />
          <el-table-column prop="target" label="节点 B" min-width="140" />
          <el-table-column prop="score" label="相似度" min-width="90" />
          <el-table-column prop="reason" label="判断依据" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="聚类结果" name="cluster">
        <p class="muted">向量空间里自然聚成几团，每团的关键词能看出这一簇在讲什么。</p>
        <el-table :data="clusters" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="id" label="簇" min-width="70" />
          <el-table-column prop="size" label="节点数" min-width="90" />
          <el-table-column label="关键词" min-width="300">
            <template #default="{ row }">
              <el-tag v-for="k in row.keywords" :key="k" size="small" effect="plain" style="margin-right: 4px">{{ k }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <p v-if="silhouette" class="muted">轮廓系数 {{ silhouette }}（越接近 1 说明分簇越清晰）</p>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/** 图嵌入：向量生成、相似节点、链接预测、补链接、聚类与导出 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  clusterNodes, exportEmbeddings, generateEmbedding, getMissingLinks,
  getNodeEmbedding, getSimilarNodes, predictLink
} from '../../../api'

const tab = ref('node')
const busy = ref('')
const nodeId = ref('n-1')
const nodeEmbedding = ref(null)
const similar = ref([])
const predictions = ref([])
const missing = ref([])
const clusters = ref([])
const silhouette = ref(null)

const generate = async () => {
  busy.value = 'generate'
  try {
    const res = await generateEmbedding({})
    ElMessage.success(res.msg || '已生成')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const loadNode = async () => {
  busy.value = 'node'
  try {
    const res = await getNodeEmbedding(nodeId.value)
    nodeEmbedding.value = res.data
  } catch {
    nodeEmbedding.value = null
  } finally {
    busy.value = ''
  }
}

const loadSimilar = async () => {
  busy.value = 'similar'
  try {
    const res = await getSimilarNodes(nodeId.value)
    similar.value = Array.isArray(res.data) ? res.data : []
  } catch {
    similar.value = []
  } finally {
    busy.value = ''
  }
}

const predict = async () => {
  busy.value = 'predict'
  try {
    const res = await predictLink({ nodeId: nodeId.value })
    predictions.value = Array.isArray(res.data) ? res.data : []
  } catch {
    predictions.value = []
  } finally {
    busy.value = ''
  }
}

const loadMissing = async () => {
  busy.value = 'missing'
  try {
    const res = await getMissingLinks()
    missing.value = Array.isArray(res.data) ? res.data : []
  } catch {
    missing.value = []
  } finally {
    busy.value = ''
  }
}

const cluster = async () => {
  busy.value = 'cluster'
  try {
    const res = await clusterNodes({})
    clusters.value = res.data?.clusters || []
    silhouette.value = res.data?.silhouette ?? null
    tab.value = 'cluster'
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const doExport = async () => {
  busy.value = 'export'
  try {
    const res = await exportEmbeddings({ format: 'jsonl' })
    ElMessage.success(res.msg || '导出任务已提交')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

onMounted(loadMissing)
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.mini-table { margin: 10px 0; max-width: 820px; }
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
  flex-wrap: wrap;
}
.vec {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #1f4bd8;
  word-break: break-all;
  line-height: 1.7;
}
.muted { color: #98a2b3; font-size: 12.5px; margin: 6px 0 0; }
</style>
