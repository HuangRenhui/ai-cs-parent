<template>
  <div class="page-card">
    <!-- 运维顶栏切片已标明「总览」，不再重复标题 -->
    <div class="page-toolbar is-actions-only">
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="item in cards" :key="item.label">
        <div class="stat-card" @click="$router.push(item.path)">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-desc">{{ item.desc }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 第一行：服务健康 + 告警事件 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="block">
          <div class="block-head">
            <span class="block-title">服务健康</span>
            <el-button link type="primary" @click="$router.push('/ops/health')">查看全部</el-button>
          </div>
          <div v-if="!health.length" class="empty-tip">暂无探测结果</div>
          <div v-for="s in health" :key="s.name" class="health-row">
            <span class="dot" :class="s.status === 'UP' ? 'dot-up' : 'dot-down'"></span>
            <span class="health-name">{{ s.name }}</span>
            <span class="health-url">{{ s.url }}</span>
            <span class="health-latency">{{ s.status === 'UP' ? `${s.latencyMs} ms` : '-' }}</span>
            <el-tag size="small" :type="s.status === 'UP' ? 'success' : 'danger'" effect="plain">
              {{ s.status }}
            </el-tag>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :md="12">
        <div class="block">
          <div class="block-head">
            <span class="block-title">告警事件</span>
            <el-button link type="primary" @click="$router.push('/ops/alerts')">查看全部</el-button>
          </div>
          <div v-if="!events.length" class="empty-tip">暂无告警事件</div>
          <div v-for="(e, i) in events" :key="i" class="event-row">
            <el-tag size="small" :type="e.status === 'open' ? 'danger' : 'success'" effect="plain">
              {{ e.status === 'open' ? '未恢复' : '已恢复' }}
            </el-tag>
            <span class="event-title">{{ e.title }}</span>
            <span class="event-time">{{ e.time }}</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 第二行：服务耗时图 + 最新日志 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="block">
          <div class="block-head">
            <span class="block-title">服务响应耗时</span>
            <span class="block-sub">仅统计存活服务</span>
          </div>
          <div v-show="upServices.length" ref="latencyChartRef" class="chart"></div>
          <div v-if="!upServices.length" class="empty-tip">暂无可用的耗时数据</div>
        </div>
      </el-col>

      <el-col :xs="24" :md="12">
        <div class="block">
          <div class="block-head">
            <span class="block-title">最新日志</span>
            <el-button link type="primary" @click="$router.push('/ops/logs')">查看全部</el-button>
          </div>
          <div v-if="!logs.length" class="empty-tip">暂无日志</div>
          <div v-for="(l, i) in logs" :key="i" class="log-row">
            <el-tag size="small" :type="LEVEL_TAG[l.level] || 'info'" effect="plain">{{ l.level }}</el-tag>
            <span class="log-service">{{ l.service }}</span>
            <span class="log-msg">{{ l.message || l.methodName }}</span>
            <span class="log-time">{{ l.timestamp }}</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <p class="hint">{{ overview.hint || '一线坐席不进入本模块。当前仅内网可用。' }}</p>
  </div>
</template>

<script setup>
/**
 * 运维总览：顶部聚合指标 + 服务健康 / 告警事件 / 响应耗时 / 最新日志。
 * 数据全部来自已有 ops 接口，不额外新增后端。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getOpsOverview, getOpsHealth, getOpsAlerts, getOpsLogs } from '../../api'
import { ADAPTER_TEXT, displayText } from '../../utils/selectOptions'

const LEVEL_TAG = { INFO: 'info', WARN: 'warning', ERROR: 'danger', DEBUG: '' }

const loading = ref(false)
const overview = ref({})
const health = ref([])
const events = ref([])
const logs = ref([])
const latencyChartRef = ref(null)

let charts = []

const cards = ref([
  { label: '服务存活', value: '-', desc: '健康探测', path: '/ops/health' },
  { label: '服务宕机', value: '-', desc: '不可达', path: '/ops/health' },
  { label: '日志文件', value: '-', desc: '本地适配器', path: '/ops/logs' },
  { label: '错误/警告', value: '-', desc: '已扫描行', path: '/ops/logs' },
  { label: '未恢复告警', value: '-', desc: '临时事件', path: '/ops/alerts' },
  { label: '适配器', value: '-', desc: '查询后端', path: '/ops/logs' }
])

/** 存活服务用于画耗时图，宕机的没有耗时数据 */
const upServices = computed(() => health.value.filter((s) => s.status === 'UP' && Number(s.latencyMs) > 0))

const disposeCharts = () => {
  charts.forEach((c) => c && c.dispose())
  charts = []
}

/** 横向柱状：一眼看出哪个服务慢 */
const renderLatencyChart = (rows) => {
  if (!latencyChartRef.value) return
  const list = rows || []
  if (!list.length) return
  const chart = echarts.init(latencyChartRef.value)
  charts.push(chart)
  chart.setOption({
    grid: { left: 8, right: 52, top: 10, bottom: 10, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}：{c} ms' },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef1f6' } }, axisLine: { show: false } },
    yAxis: { type: 'category', data: list.map((r) => r.name), axisTick: { show: false }, axisLine: { show: false } },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: list.map((r) => r.latencyMs),
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          // 超过 200ms 标红、超过 80ms 标黄，便于快速定位
          color: (params) => {
            const v = list[params.dataIndex].latencyMs
            if (v > 200) return '#f04438'
            if (v > 80) return '#f79009'
            return '#2f6bff'
          }
        },
        label: { show: true, position: 'right', formatter: '{c} ms', fontSize: 11, color: '#667085' }
      }
    ]
  })
}

const handleResize = () => charts.forEach((c) => c && c.resize())

const load = async () => {
  loading.value = true
  try {
    const [ov, he, al, lg] = await Promise.all([
      getOpsOverview(),
      getOpsHealth(),
      getOpsAlerts(),
      getOpsLogs({ page: 1, size: 6 })
    ])

    const data = ov.data || {}
    overview.value = data
    cards.value = [
      { label: '服务存活', value: `${data.servicesUp ?? 0}/${data.servicesTotal ?? 0}`, desc: '健康探测', path: '/ops/health' },
      { label: '服务宕机', value: data.servicesDown ?? 0, desc: '不可达', path: '/ops/health' },
      { label: '日志文件', value: data.logFiles ?? 0, desc: '本地适配器', path: '/ops/logs' },
      { label: '错误/警告', value: data.recentErrorCount ?? 0, desc: '已扫描行', path: '/ops/logs' },
      { label: '未恢复告警', value: data.pendingAlerts ?? 0, desc: '临时事件', path: '/ops/alerts' },
      { label: '适配器', value: displayText(ADAPTER_TEXT, data.adapter, data.adapter || '文件'), desc: '查询后端', path: '/ops/logs' }
    ]

    health.value = Array.isArray(he.data) ? he.data : []
    events.value = ((al.data || {}).events || []).slice(0, 5)
    logs.value = ((lg.data || {}).list || []).slice(0, 6)

    await nextTick()
    disposeCharts()
    renderLatencyChart(upServices.value)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 4px;
}
.stat-card {
  background: linear-gradient(180deg, #fbfcfe 0%, #f5f8fc 100%);
  border: 1px solid #e7edf5;
  border-radius: 14px;
  padding: 16px 18px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  border-color: #c4d4ff;
  box-shadow: 0 10px 24px rgba(47, 107, 255, 0.08);
}
.stat-label {
  color: #6b7280;
  font-size: 13px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  margin: 8px 0 4px;
  color: #1c2b4a;
}
.stat-desc {
  color: #9aa3b2;
  font-size: 12px;
}

/* 区块容器 */
.block {
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 14px 16px 6px;
  margin-bottom: 16px;
  min-height: 232px;
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 10px;
  margin-bottom: 6px;
  border-bottom: 1px solid #f2f5f9;
}
.block-title {
  font-size: 14px;
  font-weight: 650;
  color: #1c2b4a;
}
.block-sub {
  font-size: 12px;
  color: #9aa3b2;
}
.empty-tip {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #9aa3b2;
}
.chart {
  height: 190px;
}

/* 服务健康行 */
.health-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px dashed #f2f5f9;
  font-size: 13px;
}
.health-row:last-child {
  border-bottom: none;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-up {
  background: #12b76a;
  box-shadow: 0 0 0 3px rgba(18, 183, 106, 0.15);
}
.dot-down {
  background: #f04438;
  box-shadow: 0 0 0 3px rgba(240, 68, 56, 0.15);
}
.health-name {
  color: #1c2b4a;
  font-weight: 600;
  min-width: 76px;
}
.health-url {
  color: #98a2b3;
  font-size: 12px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.health-latency {
  color: #667085;
  font-variant-numeric: tabular-nums;
  min-width: 56px;
  text-align: right;
}

/* 告警事件行 */
.event-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px dashed #f2f5f9;
  font-size: 13px;
}
.event-row:last-child {
  border-bottom: none;
}
.event-title {
  color: #1c2b4a;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-time {
  color: #9aa3b2;
  font-size: 12px;
}

/* 日志行 */
.log-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed #f2f5f9;
  font-size: 13px;
}
.log-row:last-child {
  border-bottom: none;
}
.log-service {
  color: #667085;
  font-size: 12px;
  min-width: 92px;
}
.log-msg {
  color: #4b5563;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-time {
  color: #9aa3b2;
  font-size: 12px;
}

.hint {
  color: #6b7280;
  line-height: 1.6;
  font-size: 13px;
}

@media (max-width: 640px) {
  .stat-card {
    padding: 12px;
  }
  .stat-value {
    font-size: 22px;
  }
  .block {
    min-height: 0;
  }
  .health-url,
  .log-service {
    display: none;
  }
}
</style>
