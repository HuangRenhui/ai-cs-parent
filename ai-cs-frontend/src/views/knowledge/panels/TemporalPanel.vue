<template>
  <div>
    <p class="panel-desc">
      给关系加上生效/失效时间后，图谱就有了时间维度：能回看某个时间点的状态，
      也能看某个概念的热度是涨还是落。历史导入的数据常缺时间戳，用「校验」能查出来。
    </p>

    <div class="page-toolbar is-actions-only">
      <div class="table-actions">
        <el-button type="primary" :loading="busy === 'relation'" @click="relationVisible = true">新增时序关系</el-button>
        <el-button :loading="busy === 'validate'" @click="validate">数据校验</el-button>
        <el-button :loading="busy === 'global'" @click="loadGlobal">加载全局时间线</el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="全局时间线" name="global">
        <p class="muted">按天统计图谱的增删改，突增通常意味着有批量导入或爬取任务跑过。</p>
        <el-table :data="global" stripe v-loading="busy === 'global'" class="mini-table" table-layout="fixed">
          <el-table-column prop="date" label="日期" min-width="130" />
          <el-table-column prop="added" label="新增" min-width="90" />
          <el-table-column prop="changed" label="变更" min-width="90" />
          <el-table-column prop="removed" label="移除" min-width="90" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="时点快照" name="snapshot">
        <p class="muted">指定一个时间点，看当时的图谱长什么样，用于对比「改动前后」。</p>
        <div class="form-row">
          <el-date-picker v-model="snapshotAt" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          <el-button type="primary" :loading="busy === 'snapshot'" @click="loadSnapshot">查询快照</el-button>
        </div>
        <el-descriptions v-if="snapshot" :column="2" border size="small" class="mini-table">
          <el-descriptions-item label="时点">{{ snapshot.at }}</el-descriptions-item>
          <el-descriptions-item label="节点数">{{ snapshot.nodeCount }}</el-descriptions-item>
          <el-descriptions-item label="关系数">{{ snapshot.relationCount }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="snapshot" :data="snapshot.nodes || []" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="name" label="当时的节点" min-width="140" />
          <el-table-column prop="label" label="类型" min-width="110" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="变更流水" name="changes">
        <p class="muted">按时间倒序列出图谱发生过什么，出问题时按时间点回溯。</p>
        <el-table :data="changes" stripe v-loading="busy === 'changes'" class="mini-table" table-layout="fixed">
          <el-table-column prop="time" label="时间" min-width="130" />
          <el-table-column prop="type" label="类型" min-width="110">
            <template #default="{ row }"><TypeTag :text="row.type" /></template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip />
        </el-table>
        <el-button :loading="busy === 'changes'" @click="loadChanges">加载变更</el-button>
      </el-tab-pane>

      <el-tab-pane label="节点时间线 / 热度预测" name="node">
        <div class="form-row">
          <el-input v-model="nodeId" placeholder="节点ID，例如 n-1" style="width: 200px" />
          <el-button type="primary" :loading="busy === 'timeline'" @click="loadTimeline">时间线</el-button>
          <el-button :loading="busy === 'predict'" @click="predict">热度预测</el-button>
        </div>
        <el-table v-if="timeline.length" :data="timeline" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="time" label="时间" min-width="130" />
          <el-table-column prop="event" label="事件" min-width="110">
            <template #default="{ row }"><TypeTag :text="row.event" /></template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="300" show-overflow-tooltip />
        </el-table>
        <el-descriptions v-if="forecast" :column="3" border size="small" class="mini-table">
          <el-descriptions-item label="趋势">
            <el-tag :type="forecast.trend === 'up' ? 'danger' : 'success'" size="small">
              {{ forecast.trend === 'up' ? '上升' : '下降' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-for="f in forecast.forecast" :key="f.period" :label="f.period">
            {{ f.mentions }} 次
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增时序关系 -->
    <el-dialog title="新增时序关系" v-model="relationVisible" width="520px">
      <el-form label-width="110px">
        <el-form-item label="起点"><el-input v-model="relForm.source" placeholder="例如 退款" /></el-form-item>
        <el-form-item label="终点"><el-input v-model="relForm.target" placeholder="例如 订单" /></el-form-item>
        <el-form-item label="关系类型"><el-input v-model="relForm.relType" placeholder="例如 关联" /></el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="relForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填视为立即生效" />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker v-model="relForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填视为长期有效" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'relation'" @click="submitRelation">保存</el-button>
      </template>
    </el-dialog>

    <!-- 校验结果 -->
    <el-dialog title="时序数据校验" v-model="validateVisible" width="620px">
      <el-descriptions v-if="validateResult" :column="3" border size="small">
        <el-descriptions-item label="关系总数">{{ validateResult.total }}</el-descriptions-item>
        <el-descriptions-item label="合规">{{ validateResult.valid }}</el-descriptions-item>
        <el-descriptions-item label="异常">{{ validateResult.invalid }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="validateResult?.issues || []" stripe class="mini-table" table-layout="fixed">
        <el-table-column prop="type" label="问题类型" min-width="150" />
        <el-table-column prop="count" label="条数" min-width="80" />
        <el-table-column prop="sample" label="示例" min-width="240" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
/** 时序图谱：全局时间线、时点快照、变更流水、节点演化与热度预测 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getGlobalTimeline, getNodeTimeline, getTemporalChanges, getTemporalSnapshot,
  predictTemporalTrend, saveTemporalRelation, validateTemporal
} from '../../../api'

const tab = ref('global')
const busy = ref('')
const global = ref([])
const changes = ref([])
const snapshot = ref(null)
const snapshotAt = ref('2026-09-15')
const timeline = ref([])
const forecast = ref(null)
const nodeId = ref('n-1')
const relationVisible = ref(false)
const validateVisible = ref(false)
const validateResult = ref(null)
const relForm = reactive({ source: '', target: '', relType: '', startTime: '', endTime: '' })

const loadGlobal = async () => {
  busy.value = 'global'
  try {
    const res = await getGlobalTimeline()
    global.value = Array.isArray(res.data) ? res.data : []
  } catch {
    global.value = []
  } finally {
    busy.value = ''
  }
}

const loadChanges = async () => {
  busy.value = 'changes'
  try {
    const res = await getTemporalChanges({})
    changes.value = Array.isArray(res.data) ? res.data : []
  } catch {
    changes.value = []
  } finally {
    busy.value = ''
  }
}

const loadSnapshot = async () => {
  busy.value = 'snapshot'
  try {
    const res = await getTemporalSnapshot({ at: snapshotAt.value })
    snapshot.value = res.data
  } catch {
    snapshot.value = null
  } finally {
    busy.value = ''
  }
}

const loadTimeline = async () => {
  busy.value = 'timeline'
  try {
    const res = await getNodeTimeline(nodeId.value)
    timeline.value = Array.isArray(res.data?.events) ? res.data.events : []
  } catch {
    timeline.value = []
  } finally {
    busy.value = ''
  }
}

const predict = async () => {
  busy.value = 'predict'
  try {
    const res = await predictTemporalTrend({ nodeId: nodeId.value })
    forecast.value = res.data
  } catch {
    forecast.value = null
  } finally {
    busy.value = ''
  }
}

const validate = async () => {
  busy.value = 'validate'
  try {
    const res = await validateTemporal()
    validateResult.value = res.data
    validateVisible.value = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const submitRelation = async () => {
  if (!relForm.source || !relForm.target) {
    ElMessage.warning('请填写起点与终点')
    return
  }
  busy.value = 'relation'
  try {
    const res = await saveTemporalRelation({ ...relForm })
    ElMessage.success(res.msg || '已保存')
    relationVisible.value = false
    Object.assign(relForm, { source: '', target: '', relType: '', startTime: '', endTime: '' })
    loadChanges()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

onMounted(() => {
  loadGlobal()
  loadChanges()
})
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
.muted { color: #98a2b3; font-size: 12.5px; margin: 0 0 8px; }
</style>
