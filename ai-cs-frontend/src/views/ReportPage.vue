<template>
  <div class="page-card">
    <StubBanner />
    <div class="page-toolbar">
      <div>
        <h3>报表中心</h3>
        <p class="page-desc">进线概览、满意度评价与服务时效，按技能组看 SLA 达标。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="metric-row">
      <div class="metric"><span class="metric-num">{{ overview.inbound ?? 0 }}</span><span class="metric-label">会话进线</span></div>
      <div class="metric"><span class="metric-num">{{ overview.aiHandled ?? 0 }}</span><span class="metric-label">AI 接待</span></div>
      <div class="metric"><span class="metric-num">{{ overview.transferHuman ?? 0 }}</span><span class="metric-label">转人工</span></div>
      <div class="metric"><span class="metric-num">{{ overview.aiDeflectRate ?? 0 }}%</span><span class="metric-label">AI 拦截率</span></div>
      <div class="metric"><span class="metric-num">{{ overview.avgFirstReplySeconds ?? 0 }}s</span><span class="metric-label">平均首响</span></div>
      <div class="metric"><span class="metric-num">{{ overview.satisfactionRate ?? 0 }}%</span><span class="metric-label">满意率</span></div>
    </div>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <template v-if="section === 'csat'">
      <div class="metric-row">
        <div class="metric"><span class="metric-num">{{ csat.csat ?? '-' }}</span><span class="metric-label">CSAT 均分</span></div>
        <div class="metric"><span class="metric-num">{{ csat.nps ?? '-' }}</span><span class="metric-label">NPS</span></div>
        <div class="metric"><span class="metric-num">{{ csat.evaluated ?? 0 }}</span><span class="metric-label">已评价</span></div>
        <div class="metric"><span class="metric-num">{{ csat.dissatisfied ?? 0 }}</span><span class="metric-label">不满意</span></div>
      </div>
      <el-table :data="csat.distribution || []" stripe empty-text="暂无评价">
        <el-table-column prop="name" label="评价" min-width="140" />
        <el-table-column prop="value" label="数量" min-width="120" />
      </el-table>
    </template>

    <template v-else-if="section === 'sla'">
      <el-table :data="sla.rows || []" stripe empty-text="暂无时效数据">
        <el-table-column prop="group" label="技能组" min-width="140" show-overflow-tooltip />
        <el-table-column label="首响(秒)" min-width="100">
          <template #default="{ row }">{{ row.frtSeconds }}</template>
        </el-table-column>
        <el-table-column label="平均处理(秒)" min-width="120">
          <template #default="{ row }">{{ row.ahtSeconds }}</template>
        </el-table-column>
        <el-table-column label="SLA 达标率" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.slaHitRate >= 98 ? 'success' : 'warning'" size="small">{{ row.slaHitRate }}%</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <p class="hint">目标首响 {{ sla.slaTargetSeconds ?? '-' }} 秒内，整体达标率 {{ sla.slaHitRate ?? '-' }}%。</p>
    </template>

    <!-- 工单分布与趋势：统计口径与概览页不同，这里看的是工单侧与增长曲线 -->
    <template v-else>
      <div class="metric-row">
        <div class="metric"><span class="metric-num">{{ stats.avgHandleHours ?? '-' }}h</span><span class="metric-label">平均处理时长</span></div>
        <div class="metric"><span class="metric-num">{{ stats.slaHitRate ?? '-' }}%</span><span class="metric-label">工单 SLA 达标</span></div>
        <div class="metric"><span class="metric-num">{{ todaySessions }}</span><span class="metric-label">今日会话</span></div>
        <div class="metric"><span class="metric-num">{{ overview2.monthSessions ?? 0 }}</span><span class="metric-label">本月会话</span></div>
      </div>

      <SectionSwitch v-model="statsTab" :options="statsTabOptions" />

      <el-table v-if="statsTab === 'status'" :data="stats.byStatus || []" stripe empty-text="暂无工单数据" table-layout="fixed">
        <el-table-column prop="label" label="状态" min-width="140" />
        <el-table-column prop="count" label="数量" min-width="100" />
        <el-table-column label="占比" min-width="240">
          <template #default="{ row }">
            <el-progress :percentage="share(row.count, statusTotal)" :stroke-width="6" />
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="statsTab === 'type'" :data="stats.byType || []" stripe empty-text="暂无工单数据" table-layout="fixed">
        <el-table-column prop="type" label="类型" min-width="140" />
        <el-table-column prop="count" label="数量" min-width="100" />
        <el-table-column label="占比" min-width="240">
          <template #default="{ row }">
            <el-progress :percentage="share(row.count, typeTotal)" :stroke-width="6" color="#7b6cff" />
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="trendRows" stripe empty-text="暂无趋势数据" table-layout="fixed" max-height="420">
        <el-table-column prop="date" label="日期" min-width="120" />
        <el-table-column prop="chat" label="会话量" min-width="110" />
        <el-table-column prop="customer" label="新增客户" min-width="110" />
      </el-table>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  getChatTrend, getCustomerTrend, getReportOverview, getReportCsat, getReportSla,
  getStatisticsOverview, getWorkOrderStats
} from '../api'
import SectionSwitch from '../components/SectionSwitch.vue'

const section = ref('csat')
const sectionOptions = [
  { value: 'csat', label: '满意度' },
  { value: 'sla', label: '服务时效' },
  { value: 'stats', label: '工单与趋势' }
]

const overview = ref({})
const csat = ref({})
const sla = ref({})
const overview2 = ref({})
const stats = ref({})
const statsTab = ref('status')
const statsTabOptions = [
  { value: 'status', label: '工单状态分布' },
  { value: 'type', label: '工单类型分布' },
  { value: 'trend', label: '近 14 天趋势' }
]
const chatTrend = ref([])
const customerTrend = ref([])

const load = async () => {
  try {
    const [o, c, s] = await Promise.all([getReportOverview(), getReportCsat(), getReportSla()])
    overview.value = o.data || {}
    csat.value = c.data || {}
    sla.value = s.data || {}
  } catch {
    /* 拦截器已提示 */
  }
  loadStats()
}

const loadStats = async () => {
  try {
    const [ov, ws, ct, cu] = await Promise.all([
      getStatisticsOverview(),
      getWorkOrderStats(),
      getChatTrend({ days: 14 }),
      getCustomerTrend({ days: 14 })
    ])
    overview2.value = ov.data || {}
    stats.value = ws.data || {}
    chatTrend.value = Array.isArray(ct.data) ? ct.data : []
    customerTrend.value = Array.isArray(cu.data) ? cu.data : []
  } catch {
    /* 拦截器已提示 */
  }
}

const todaySessions = computed(() => overview2.value.todaySessions ?? 0)
const statusTotal = computed(() => (stats.value.byStatus || []).reduce((s, x) => s + x.count, 0))
const typeTotal = computed(() => (stats.value.byType || []).reduce((s, x) => s + x.count, 0))

/** 占比按总数算，总数为 0 时不显示除零结果 */
const share = (count, total) => (total ? Math.round((count / total) * 100) : 0)

/** 两条趋势按日期对齐成一张表，省得并排两个表格 */
const trendRows = computed(() => {
  const map = new Map()
  for (const c of chatTrend.value) map.set(c.date, { date: c.date, chat: c.count, customer: 0 })
  for (const c of customerTrend.value) {
    const row = map.get(c.date) || { date: c.date, chat: 0, customer: 0 }
    row.customer = c.count
    map.set(c.date, row)
  }
  return [...map.values()]
})

onMounted(load)
</script>

<style scoped>
.metric-row { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.metric {
  flex: 1 1 120px;
  min-width: 120px;
  padding: 12px 14px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-num { font-size: 20px; font-weight: 700; color: #1c2b4a; }
.metric-label { font-size: 12px; color: #6b7280; }
.hint { color: #6b7280; font-size: 13px; margin: 12px 0 0; }
</style>
