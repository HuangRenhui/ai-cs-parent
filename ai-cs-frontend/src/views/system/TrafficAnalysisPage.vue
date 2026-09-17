<template>
  <div class="page-card" v-loading="loading">
    <p class="hint">{{ pageDesc }}</p>
    <!-- 顶栏切片已标明本页，不再重复「流量分析」标题 -->
    <div class="page-toolbar is-actions-only">
      <el-button @click="$router.push('/ops/audit')">查看操作审计</el-button>
    </div>

    <el-form inline @submit.prevent="search">
      <el-form-item label="时间">
        <!-- 非超管禁用一个月以外的日期，与操作审计同一套可见窗口 -->
        <el-date-picker
          v-model="query.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          :disabled-date="disabledDate"
        />
      </el-form-item>
      <el-form-item>
        <FilterActions @search="search" @reset="resetSearch" />
      </el-form-item>
    </el-form>

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="card in statCards" :key="card.title">
        <div class="stat-card">
          <div class="stat-title">{{ card.title }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <div class="chart-panel">
          <div class="panel-title">请求量趋势（按小时）</div>
          <div ref="trafficChartRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="10">
        <div class="chart-panel">
          <div class="panel-title">模块分布</div>
          <div ref="moduleChartRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <div class="chart-panel">
          <div class="panel-title">来源 IP 排行</div>
          <el-table :data="topIps" stripe size="small" empty-text="暂无来源地址" table-layout="fixed" max-height="320">
            <el-table-column type="index" label="#" min-width="50" />
            <el-table-column prop="ip" label="来源地址" min-width="140" />
            <el-table-column prop="count" label="请求次数" min-width="90" />
            <el-table-column label="失败" min-width="70">
              <template #default="{ row }">{{ row.fail || 0 }}</template>
            </el-table-column>
            <el-table-column label="失败率" min-width="80">
              <template #default="{ row }">{{ failRate(row) }}</template>
            </el-table-column>
            <el-table-column prop="lastTime" label="最近出现" min-width="160" />
            <el-table-column label="操作" min-width="88">
              <template #default="{ row }">
                <div class="table-actions">
                  <TblAct act="detail" label="审计" @click="openLogs(row.ip)" />
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :md="10">
        <div class="chart-panel">
          <div class="panel-title">需关注的 IP</div>
          <p class="risk-hint">失败占比达到 30% 且请求不少于 3 次的来源地址。</p>
          <el-table :data="riskIps" stripe size="small" empty-text="当前窗口没有高失败来源" table-layout="fixed" max-height="280">
            <el-table-column prop="ip" label="来源地址" min-width="140" />
            <el-table-column prop="fail" label="失败" min-width="64" />
            <el-table-column prop="count" label="总次数" min-width="72" />
            <el-table-column label="操作" min-width="88">
              <template #default="{ row }">
                <div class="table-actions">
                  <TblAct act="detail" label="审计" @click="openLogs(row.ip)" />
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
/**
 * 流量与 IP 分析页：把操作审计聚成请求量、来源地址和模块分布，点 IP 可下钻到审计列表。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getAccessAnalysis } from '../../api'
import { isSuperAdmin } from '../../utils/auth'
import { displayText, LOG_MODULE_TEXT } from '../../utils/selectOptions'

const router = useRouter()
const loading = ref(false)
const trafficChartRef = ref(null)
const moduleChartRef = ref(null)
let charts = []

const data = ref({
  total: 0,
  success: 0,
  fail: 0,
  uniqueIps: 0,
  avgDurationMs: 0,
  traffic: [],
  topIps: [],
  modules: [],
  riskIps: []
})

const query = reactive({ timeRange: defaultRange() })
const topIps = computed(() => data.value.topIps || [])
const riskIps = computed(() => data.value.riskIps || [])
const canSeeAll = computed(() => isSuperAdmin())
const pageDesc = computed(() => canSeeAll.value
  ? '按操作审计汇总请求量与来源 IP。超级管理员可看全部时间。'
  : '按操作审计汇总请求量与来源 IP。当前角色仅能分析近一个月的记录。')

const statCards = computed(() => [
  { title: '请求次数', value: data.value.total || 0, sub: '次' },
  { title: '独立 IP', value: data.value.uniqueIps || 0, sub: '个来源' },
  { title: '成功', value: data.value.success || 0, sub: '次' },
  { title: '失败', value: data.value.fail || 0, sub: failShare(data.value) },
  { title: '平均耗时', value: data.value.avgDurationMs || 0, sub: '毫秒' }
])

/** 默认看近 7 天，和后端未传 beginTime 时的窗口对齐 */
function defaultRange() {
  const end = new Date()
  const start = new Date(end.getTime() - 7 * 24 * 3600 * 1000)
  return [fmt(start), fmt(end)]
}

function fmt(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 一个月前的零点，给非超管做日期禁用 */
const monthAgo = () => {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  d.setHours(0, 0, 0, 0)
  return d
}

/** 非超管不能点选一个月以外的日期 */
const disabledDate = (date) => {
  if (canSeeAll.value) return false
  const from = monthAgo()
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  return date.getTime() < from.getTime() || date.getTime() > end.getTime()
}

const failRate = (row) => {
  if (!row || !row.count) return '—'
  return `${((row.fail || 0) * 100 / row.count).toFixed(1)}%`
}

const failShare = (dto) => {
  if (!dto || !dto.total) return '占比 —'
  return `占比 ${((dto.fail || 0) * 100 / dto.total).toFixed(1)}%`
}

/** 点 IP 跳到操作审计，带上来源地址筛选 */
const openLogs = (ip) => {
  if (!ip || ip === '未知') return
  router.push({ path: '/ops/audit', query: { ip } })
}

const disposeCharts = () => {
  charts.forEach((c) => c && c.dispose())
  charts = []
}

const renderTrafficChart = (rows) => {
  if (!trafficChartRef.value) return
  const chart = echarts.init(trafficChartRef.value)
  charts.push(chart)
  const list = rows || []
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['请求', '失败'], right: 8 },
    xAxis: {
      type: 'category',
      data: list.map((d) => d.bucket),
      axisLabel: { rotate: 30, fontSize: 11 }
    },
    yAxis: { type: 'value', minInterval: 1 },
    grid: { left: 40, right: 16, top: 32, bottom: 48 },
    series: [
      { name: '请求', type: 'line', smooth: true, areaStyle: {}, data: list.map((d) => d.count) },
      { name: '失败', type: 'line', smooth: true, itemStyle: { color: '#f04438' }, data: list.map((d) => d.fail) }
    ]
  })
}

const renderModuleChart = (rows) => {
  if (!moduleChartRef.value) return
  const chart = echarts.init(moduleChartRef.value)
  charts.push(chart)
  const list = rows || []
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      data: list.map((d) => ({
        name: displayText(LOG_MODULE_TEXT, d.module, d.module),
        value: d.count
      }))
    }]
  })
}

const handleResize = () => {
  charts.forEach((c) => c && c.resize())
}

const load = async () => {
  loading.value = true
  try {
    const params = {}
    if (query.timeRange && query.timeRange.length === 2) {
      params.beginTime = query.timeRange[0]
      params.endTime = query.timeRange[1]
    }
    const res = await getAccessAnalysis(params)
    data.value = res.data || {}
    await nextTick()
    disposeCharts()
    renderTrafficChart(data.value.traffic)
    renderModuleChart(data.value.modules)
  } catch {
    data.value = { total: 0, success: 0, fail: 0, uniqueIps: 0, avgDurationMs: 0, traffic: [], topIps: [], modules: [], riskIps: [] }
  } finally {
    loading.value = false
  }
}

const search = () => load()

/** 清空时间后回到默认近 7 天 */
const resetSearch = () => {
  query.timeRange = defaultRange()
  load()
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.stats-row {
  margin-bottom: 8px;
}
.stat-card {
  background: linear-gradient(180deg, #fbfcfe 0%, #f5f8fc 100%);
  border: 1px solid #e7edf5;
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 16px;
  box-sizing: border-box;
}
.stat-title {
  font-size: 13px;
  color: #667085;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #1c2b4a;
  line-height: 1.3;
  margin-top: 4px;
}
.stat-sub {
  font-size: 12px;
  color: #98a2b3;
  margin-top: 2px;
}
.chart-panel {
  background: linear-gradient(180deg, #fbfcfe 0%, #f5f8fc 100%);
  border: 1px solid #e7edf5;
  border-radius: 14px;
  padding: 16px 18px;
  margin-bottom: 16px;
  box-sizing: border-box;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #2a3f5f;
  margin-bottom: 12px;
}
.chart-box {
  width: 100%;
  height: 280px;
}
.risk-hint {
  margin: -4px 0 10px;
  font-size: 12px;
  color: #98a2b3;
}
@media (max-width: 640px) {
  .chart-box {
    height: 220px;
  }
}
</style>
