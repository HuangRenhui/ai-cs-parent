<template>
  <div>
    <p class="panel-desc">
      图谱是活的：每天有新增节点、过期关系。这里做日常养护——删掉没人指向的孤立节点、
      清理已经失效的关系、重算核心节点，并给图谱打一个健康分。
    </p>

    <!-- 健康分 -->
    <div class="health-box" v-loading="busy === 'health'">
      <div class="score-ring">
        <span class="score-num">{{ health ? health.score : '—' }}</span>
        <span class="score-label">{{ health ? health.level : '未检测' }}</span>
      </div>
      <div class="metrics">
        <div v-for="m in health ? health.metrics : []" :key="m.name" class="metric-item">
          <span class="m-name">{{ m.name }}</span>
          <span class="m-value" :class="m.status">{{ m.value }}</span>
        </div>
      </div>
      <el-button :loading="busy === 'health'" @click="loadHealth">重新体检</el-button>
    </div>

    <!-- 养护动作 -->
    <h5 class="sub-title">日常养护</h5>
    <div class="op-grid">
      <div class="op-card">
        <h6>增量更新</h6>
        <p class="muted">只处理上次同步后变更的节点与关系，开销小，适合每天跑。</p>
        <el-button type="primary" :loading="busy === 'incremental-update'" @click="run('incremental-update', incrementalUpdateGraph)">执行</el-button>
      </div>
      <div class="op-card">
        <h6>重算核心节点</h6>
        <p class="muted">按度数重算哪些是核心节点，影响 PageRank 与召回权重。</p>
        <el-button :loading="busy === 'recalculate-core'" @click="run('recalculate-core', recalculateGraphCore)">执行</el-button>
      </div>
      <div class="op-card">
        <h6>清理失效关系</h6>
        <p class="muted">移除已过期或权重低于阈值的关系，减少噪音。</p>
        <el-button :loading="busy === 'cleanup-relations'" @click="cleanupRelations">执行</el-button>
      </div>
      <div class="op-card">
        <h6>清理孤立节点</h6>
        <p class="muted">删掉没有任何关系的节点，它们检索时永远召不回来。</p>
        <el-button :loading="busy === 'cleanup-isolated'" @click="run('cleanup-isolated', cleanupIsolatedNodes)">执行</el-button>
      </div>
      <div class="op-card danger">
        <h6>全量重算</h6>
        <p class="muted">重建全部节点的图嵌入与索引，耗时较长，建议低峰期执行。</p>
        <el-button type="warning" :loading="busy === 'trigger-full'" @click="triggerFull">触发全量</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" class="mt">
      <el-tab-pane label="演化趋势" name="trend">
        <p class="muted">节点与关系的增长曲线，突然变平通常意味着采集任务挂了。</p>
        <el-table :data="trend" stripe v-loading="busy === 'trend'" class="mini-table" table-layout="fixed">
          <el-table-column prop="date" label="日期" min-width="130" />
          <el-table-column prop="nodes" label="节点数" min-width="110" />
          <el-table-column prop="relations" label="关系数" min-width="110" />
          <el-table-column label="较前一天" min-width="140">
            <template #default="{ row, $index }">
              <span v-if="$index === 0" class="muted">—</span>
              <span v-else :class="row.nodes >= trend[$index - 1].nodes ? 'up' : 'down'">
                {{ row.nodes >= trend[$index - 1].nodes ? '+' : '' }}{{ row.nodes - trend[$index - 1].nodes }} 节点
              </span>
            </template>
          </el-table-column>
        </el-table>
        <el-button :loading="busy === 'trend'" @click="loadTrend">加载趋势</el-button>
      </el-tab-pane>

      <el-tab-pane label="演化日志" name="log">
        <p class="muted">谁在什么时候做了什么养护动作，出问题时按时间回溯。</p>
        <el-table :data="log" stripe v-loading="busy === 'log'" class="mini-table" table-layout="fixed">
          <el-table-column prop="time" label="时间" min-width="150" />
          <el-table-column prop="action" label="动作" min-width="110">
            <template #default="{ row }"><TypeTag :text="row.action" /></template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip />
          <el-table-column prop="operator" label="执行人" min-width="110" show-overflow-tooltip />
        </el-table>
        <el-button :loading="busy === 'log'" @click="loadLog">加载日志</el-button>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/** 图谱演化：健康体检、日常养护动作、趋势与日志 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cleanupGraphRelations, cleanupIsolatedNodes, getEvolutionLog, getEvolutionTrend,
  getGraphHealth, incrementalUpdateGraph, recalculateGraphCore, triggerFullEvolution
} from '../../../api'

const busy = ref('')
const tab = ref('trend')
const health = ref(null)
const trend = ref([])
const log = ref([])

const loadHealth = async () => {
  busy.value = 'health'
  try {
    const res = await getGraphHealth()
    health.value = res.data
  } catch {
    health.value = null
  } finally {
    busy.value = ''
  }
}

const loadTrend = async () => {
  busy.value = 'trend'
  try {
    const res = await getEvolutionTrend()
    trend.value = Array.isArray(res.data) ? res.data : []
  } catch {
    trend.value = []
  } finally {
    busy.value = ''
  }
}

const loadLog = async () => {
  busy.value = 'log'
  try {
    const res = await getEvolutionLog({ limit: 20 })
    log.value = Array.isArray(res.data) ? res.data : []
  } catch {
    log.value = []
  } finally {
    busy.value = ''
  }
}

/** 无参动作统一执行，省得每个都写一遍 loading 收尾 */
const run = async (key, fn) => {
  busy.value = key
  try {
    const res = await fn()
    ElMessage.success(res.msg || '已执行')
    loadHealth()
    loadLog()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const cleanupRelations = async () => {
  try {
    await ElMessageBox.confirm('将移除已过期与低权重关系，此操作会写回图谱，确认执行？', '清理失效关系', { type: 'warning' })
  } catch {
    return
  }
  run('cleanup-relations', () => cleanupGraphRelations({}))
}

const triggerFull = async () => {
  try {
    await ElMessageBox.confirm('全量重算耗时较长（演示约 2 分钟），确认在低峰期执行？', '全量重算', { type: 'warning' })
  } catch {
    return
  }
  run('trigger-full', triggerFullEvolution)
}

onMounted(() => {
  loadHealth()
  loadTrend()
  loadLog()
})
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.sub-title { margin: 18px 0 10px; font-size: 13.5px; color: #1c2b4a; }
.health-box {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  padding: 16px 20px;
  border-radius: 10px;
  background: #f7f9fc;
}
.score-ring {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background: #fff;
  box-shadow: inset 0 0 0 3px #2f6bff33;
  flex-shrink: 0;
}
.score-num { font-size: 26px; font-weight: 750; color: #1f4fd8; }
.score-label { font-size: 12px; color: #667085; }
.metrics {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 240px;
}
.metric-item { display: flex; flex-direction: column; gap: 4px; }
.m-name { font-size: 12px; color: #98a2b3; }
.m-value { font-size: 15px; font-weight: 700; color: #1c2b4a; }
.m-value.good { color: #067647; }
.m-value.warn { color: #b54708; }
.m-value.bad { color: #b42318; }
.op-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.op-card {
  padding: 14px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
}
.op-card.danger { border-color: #fedf89; background: #fffcf5; }
.op-card h6 { margin: 0 0 6px; font-size: 13.5px; color: #1c2b4a; }
.mt { margin-top: 8px; }
.mini-table { margin: 10px 0; max-width: 820px; }
.up { color: #067647; font-weight: 600; }
.down { color: #b42318; font-weight: 600; }
.muted { color: #98a2b3; font-size: 12.5px; margin: 0 0 8px; }
</style>
