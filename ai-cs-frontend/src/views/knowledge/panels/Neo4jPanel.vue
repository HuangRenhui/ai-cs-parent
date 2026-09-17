<template>
  <div>
    <p class="panel-desc">
      把知识图谱同步到图数据库后，可以跑图算法找关键节点与异常环路。
      Cypher 查询在演示环境**只允许只读**，写操作会被拦下。
    </p>

    <!-- 连接状态 -->
    <div class="stat-row" v-loading="statusLoading">
      <div class="stat-card">
        <span class="stat-label">连接状态</span>
        <span class="stat-value" :class="status && status.connected ? 'ok' : 'bad'">
          {{ status ? (status.connected ? '已连接' : '未连接') : '—' }}
        </span>
      </div>
      <div class="stat-card">
        <span class="stat-label">节点数</span>
        <span class="stat-value">{{ status ? status.nodeCount : '—' }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">关系数</span>
        <span class="stat-value">{{ status ? status.relationCount : '—' }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">查询延迟</span>
        <span class="stat-value">{{ status ? status.latencyMs + ' ms' : '—' }}</span>
      </div>
      <el-button type="primary" :loading="syncing" @click="doSync">同步图谱</el-button>
    </div>

    <!-- Cypher -->
    <h5 class="sub-title">Cypher 查询</h5>
    <el-input v-model="cypher" type="textarea" :rows="3"
      placeholder="MATCH (n) RETURN n.name, n.label, size((n)--()) AS degree LIMIT 20" />
    <div class="btn-row">
      <el-button type="primary" :loading="running" @click="runCypher">执行</el-button>
      <el-button @click="cypher = 'MATCH (n) RETURN n.name, n.label, size((n)--()) AS degree LIMIT 20'">填入示例</el-button>
    </div>
    <el-table v-if="cypherResult" :data="cypherResult.rows" stripe class="result-table" table-layout="fixed" max-height="260">
      <el-table-column v-for="(c, i) in cypherResult.columns" :key="c" :label="c" min-width="130">
        <template #default="{ row }">{{ row[i] }}</template>
      </el-table-column>
    </el-table>
    <p v-if="cypherResult" class="muted">
      返回 {{ cypherResult.rowCount }} 行，耗时 {{ cypherResult.elapsedMs }} 毫秒
    </p>

    <!-- 图算法 -->
    <h5 class="sub-title">图算法</h5>
    <div class="btn-row">
      <el-button :loading="busy === 'communities'" @click="loadAlgo('communities')">社区发现</el-button>
      <el-button :loading="busy === 'pagerank'" @click="loadAlgo('pagerank')">PageRank</el-button>
      <el-button :loading="busy === 'bridge'" @click="loadAlgo('bridge')">桥接节点</el-button>
      <el-button :loading="busy === 'circular'" @click="loadAlgo('circular')">环路检测</el-button>
      <el-button :loading="busy === 'stats'" @click="loadAlgo('stats')">图统计</el-button>
    </div>

    <!-- 路径分析 -->
    <h5 class="sub-title">路径分析</h5>
    <div class="form-row">
      <el-input v-model="pathForm.from" placeholder="起点，例如 退款" style="width: 180px" />
      <span class="arrow">→</span>
      <el-input v-model="pathForm.to" placeholder="终点，例如 发票" style="width: 180px" />
      <el-button type="primary" :loading="busy === 'path'" @click="runPath">最短路径</el-button>
    </div>
    <div class="form-row">
      <el-input v-model="hopForm.nodeId" placeholder="节点，例如 n-3" style="width: 180px" />
      <el-input-number v-model="hopForm.k" :min="1" :max="5" />
      <span class="muted">跳</span>
      <el-button type="primary" :loading="busy === 'hop'" @click="runHop">邻域展开</el-button>
    </div>

    <el-dialog :title="algoTitle" v-model="algoVisible" width="760px">
      <!-- 图统计 -->
      <el-descriptions v-if="algoKind === 'stats' && algoData" :column="3" border size="small">
        <el-descriptions-item label="节点数">{{ algoData.nodeCount }}</el-descriptions-item>
        <el-descriptions-item label="关系数">{{ algoData.relationCount }}</el-descriptions-item>
        <el-descriptions-item label="标签数">{{ algoData.labelCount }}</el-descriptions-item>
        <el-descriptions-item label="关系类型数">{{ algoData.relTypeCount }}</el-descriptions-item>
        <el-descriptions-item label="平均度数">{{ algoData.avgDegree }}</el-descriptions-item>
        <el-descriptions-item label="图密度">{{ algoData.density }}</el-descriptions-item>
      </el-descriptions>

      <!-- 社区 -->
      <el-table v-else-if="algoKind === 'communities'" :data="algoData || []" stripe table-layout="fixed">
        <el-table-column prop="name" label="社区" min-width="120" />
        <el-table-column prop="coreMember" label="核心成员" min-width="110" />
        <el-table-column prop="nodeCount" label="节点数" min-width="90" />
        <el-table-column prop="cohesion" label="内聚度" min-width="90" />
      </el-table>

      <!-- 其它算法统一按 name + 分数 + 说明展示 -->
      <el-table v-else :data="algoData || []" stripe table-layout="fixed">
        <el-table-column prop="name" label="节点" min-width="130" />
        <el-table-column label="得分 / 说明" min-width="260">
          <template #default="{ row }">
            {{ row.score != null ? row.score : (row.connects ? row.connects.join('、') : row.cycle ? row.cycle.join(' → ') : row.severity) }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-alert v-if="pathResult" type="success" :closable="false" class="path-box">
      <template #title>
        最短路径（{{ pathResult.length }} 跳）：{{ (pathResult.path || []).join(' → ') }}
      </template>
    </el-alert>

    <el-dialog :title="`「${hopForm.nodeId}」的 ${hopForm.k} 跳邻域`" v-model="hopVisible" width="680px">
      <el-table :data="hopResult?.nodes || []" stripe max-height="380" table-layout="fixed">
        <el-table-column prop="name" label="节点" min-width="130" />
        <el-table-column prop="label" label="类型" min-width="110" />
        <el-table-column prop="community" label="所属社区" min-width="110" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
/** 图数据库分析：连接状态、Cypher 查询、图算法、路径与邻域 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getNeo4jBridgeNodes, getNeo4jCircularDeps, getNeo4jCommunities, getNeo4jKHop,
  getNeo4jPageRank, getNeo4jShortestPath, getNeo4jStats, getNeo4jStatus,
  runNeo4jCypher, syncNeo4j
} from '../../../api'

const status = ref(null)
const statusLoading = ref(false)
const syncing = ref(false)
const running = ref(false)
const busy = ref('')
const cypher = ref('MATCH (n) RETURN n.name, n.label, size((n)--()) AS degree LIMIT 20')
const cypherResult = ref(null)
const pathForm = reactive({ from: '退款', to: '发票' })
const hopForm = reactive({ nodeId: 'n-3', k: 2 })
const pathResult = ref(null)
const hopResult = ref(null)
const hopVisible = ref(false)
const algoVisible = ref(false)
const algoTitle = ref('')
const algoKind = ref('')
const algoData = ref(null)

const loadStatus = async () => {
  statusLoading.value = true
  try {
    const res = await getNeo4jStatus()
    status.value = res.data
  } catch {
    status.value = null
  } finally {
    statusLoading.value = false
  }
}

const doSync = async () => {
  syncing.value = true
  try {
    const res = await syncNeo4j({})
    ElMessage.success(res.msg || '同步完成')
    loadStatus()
  } catch {
    /* 拦截器已提示 */
  } finally {
    syncing.value = false
  }
}

const runCypher = async () => {
  if (!cypher.value.trim()) {
    ElMessage.warning('请输入查询语句')
    return
  }
  running.value = true
  try {
    const res = await runNeo4jCypher({ cypher: cypher.value.trim() })
    cypherResult.value = res.data
  } catch {
    cypherResult.value = null
  } finally {
    running.value = false
  }
}

/** 各算法返回结构不同，这里按类型收口，弹窗按 kind 决定渲染方式 */
const loadAlgo = async (kind) => {
  busy.value = kind
  const ACTIONS = {
    communities: [getNeo4jCommunities, '社区发现'],
    pagerank: [getNeo4jPageRank, 'PageRank 重要度'],
    bridge: [getNeo4jBridgeNodes, '桥接节点'],
    circular: [getNeo4jCircularDeps, '环路检测'],
    stats: [getNeo4jStats, '图统计']
  }
  const [fn, title] = ACTIONS[kind]
  try {
    const res = await fn()
    algoKind.value = kind
    algoTitle.value = title
    algoData.value = res.data
    algoVisible.value = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const runPath = async () => {
  busy.value = 'path'
  try {
    const res = await getNeo4jShortestPath({ from: pathForm.from, to: pathForm.to })
    pathResult.value = res.data
  } catch {
    pathResult.value = null
  } finally {
    busy.value = ''
  }
}

const runHop = async () => {
  busy.value = 'hop'
  try {
    const res = await getNeo4jKHop(hopForm.nodeId, { k: hopForm.k })
    hopResult.value = res.data
    hopVisible.value = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.sub-title {
  margin: 20px 0 10px;
  font-size: 13.5px;
  color: #1c2b4a;
}
.stat-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 16px;
  border-radius: 8px;
  background: #f7f9fc;
  min-width: 108px;
}
.stat-label {
  font-size: 12px;
  color: #98a2b3;
}
.stat-value {
  font-size: 15px;
  font-weight: 700;
  color: #1c2b4a;
}
.stat-value.ok { color: #067647; }
.stat-value.bad { color: #b42318; }
.btn-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 10px 0;
}
.form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.arrow { color: #98a2b3; }
.result-table { margin-top: 12px; }
.path-box { margin-top: 12px; max-width: 720px; }
.muted { color: #98a2b3; font-size: 12.5px; }
</style>
