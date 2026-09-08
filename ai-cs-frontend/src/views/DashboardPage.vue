<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>数据概览</h3>
      <el-button :loading="loading" @click="loadDashboard">刷新</el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6" v-for="card in statCards" :key="card.title">
        <div class="stat-card">
          <div class="stat-icon" :style="{ backgroundColor: card.color }">
            <el-icon :size="24"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-title">{{ card.title }}</div>
            <div class="stat-sub">{{ card.sub }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="chart-panel">
          <div class="panel-title">聊天会话趋势（近30天）</div>
          <div ref="chatChartRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="chart-panel">
          <div class="panel-title">工单状态分布</div>
          <div ref="orderChartRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 工单处理排行 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <div class="chart-panel">
          <div class="panel-title">坐席处理工单排行</div>
          <el-table :data="agentRank" style="width: 100%" size="small" empty-text="暂无排行数据">
            <el-table-column type="index" label="排名" width="70" />
            <el-table-column prop="name" label="坐席" />
            <el-table-column prop="count" label="处理工单数" width="140" />
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="chart-panel">
          <div class="panel-title">客户增长趋势（近30天）</div>
          <div ref="customerChartRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChatDotRound, Tickets, TrendCharts, User } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardStatistics } from '../api'

const loading = ref(false)
const chatChartRef = ref(null)
const orderChartRef = ref(null)
const customerChartRef = ref(null)
let charts = []

const statCards = ref([
  { title: '今日会话', value: 0, sub: '次', icon: ChatDotRound, color: '#409eff' },
  { title: '今日工单', value: 0, sub: '个', icon: Tickets, color: '#67c23a' },
  { title: '新增客户', value: 0, sub: '人', icon: User, color: '#e6a23c' },
  { title: '本周会话', value: 0, sub: '次', icon: TrendCharts, color: '#f56c6c' }
])

const agentRank = ref([])

const disposeCharts = () => {
  charts.forEach(c => c && c.dispose())
  charts = []
}

const renderChatChart = (data) => {
  if (!chatChartRef.value) return
  const chart = echarts.init(chatChartRef.value)
  charts.push(chart)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map(d => d.date) },
    yAxis: { type: 'value', minInterval: 1 },
    grid: { left: 40, right: 16, top: 24, bottom: 28 },
    series: [{ data: data.map(d => d.count), type: 'line', smooth: true, areaStyle: {} }]
  })
}

const renderOrderChart = (data) => {
  if (!orderChartRef.value) return
  const statusMap = { '1': '待处理', '2': '处理中', '3': '已完成', '4': '已关闭' }
  const chart = echarts.init(orderChartRef.value)
  charts.push(chart)
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      data: data.map(d => ({ name: statusMap[d.status] || d.status, value: d.count }))
    }]
  })
}

const renderCustomerChart = (data) => {
  if (!customerChartRef.value) return
  const chart = echarts.init(customerChartRef.value)
  charts.push(chart)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map(d => d.date) },
    yAxis: { type: 'value', minInterval: 1 },
    grid: { left: 40, right: 16, top: 24, bottom: 28 },
    series: [{ data: data.map(d => d.count), type: 'bar', itemStyle: { color: '#67c23a' } }]
  })
}

const handleResize = () => {
  charts.forEach(c => c && c.resize())
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const res = await getDashboardStatistics()
    if (res.code === 200 && res.data) {
      const data = res.data
      const overview = data.overview || {}
      statCards.value[0].value = overview.todaySessions || 0
      statCards.value[1].value = overview.todayOrders || 0
      statCards.value[2].value = overview.todayCustomers || 0
      statCards.value[3].value = overview.weekSessions || 0

      const workOrderStats = data.workOrderStats || {}
      agentRank.value = workOrderStats.agentRank || []

      await nextTick()
      disposeCharts()
      renderChatChart(data.chatTrend || [])
      renderOrderChart(workOrderStats.byStatus || [])
      renderCustomerChart(data.customerTrend || [])
    }
  } catch (e) {
    console.error('加载仪表盘数据失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.stats-row {
  margin-bottom: 16px;
}
.stat-card {
  background: #f5f7fa;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  height: 100%;
  box-sizing: border-box;
  margin-bottom: 16px;
}
.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-right: 14px;
  flex-shrink: 0;
}
.stat-info {
  min-width: 0;
}
.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  line-height: 1.2;
}
.stat-title {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
.stat-sub {
  font-size: 12px;
  color: #c0c4cc;
}
.chart-panel {
  background: #f5f7fa;
  border-radius: 12px;
  padding: 16px;
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
  height: 300px;
}
@media (max-width: 640px) {
  .stat-card {
    padding: 12px;
  }
  .stat-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    margin-right: 10px;
  }
  .stat-value {
    font-size: 18px;
  }
  .chart-panel {
    padding: 12px;
  }
  .chart-box {
    height: 220px;
  }
}
@media (min-width: 641px) and (max-width: 1024px) {
  .chart-box {
    height: 260px;
  }
}
</style>
