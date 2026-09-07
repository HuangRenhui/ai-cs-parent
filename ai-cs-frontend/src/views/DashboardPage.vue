<template>
  <div class="dashboard-container">
    <h2 class="page-title">数据概览</h2>
    
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6" v-for="card in statCards" :key="card.title">
        <el-card class="stat-card" :style="{ borderTopColor: card.color }">
          <div class="stat-icon" :style="{ backgroundColor: card.color }">
            <el-icon :size="28"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-title">{{ card.title }}</div>
            <div class="stat-sub">{{ card.sub }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header><span>聊天会话趋势（近30天）</span></template>
          <div ref="chatChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header><span>工单状态分布</span></template>
          <div ref="orderChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工单处理排行 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header><span>坐席处理工单排行</span></template>
          <el-table :data="agentRank" style="width: 100%" size="small">
            <el-table-column type="index" label="排名" width="60" />
            <el-table-column prop="name" label="坐席" />
            <el-table-column prop="count" label="处理工单数" width="120" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header><span>客户增长趋势（近30天）</span></template>
          <div ref="customerChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { User, ChatDotRound, Tickets, TrendCharts } from '@element-plus/icons-vue'
import request from '../utils/request'
import * as echarts from 'echarts'

const chatChartRef = ref(null)
const orderChartRef = ref(null)
const customerChartRef = ref(null)

const statCards = ref([
  { title: '今日会话', value: 0, sub: '次', icon: 'ChatDotRound', color: '#409eff' },
  { title: '今日工单', value: 0, sub: '个', icon: 'Tickets', color: '#67c23a' },
  { title: '新增客户', value: 0, sub: '人', icon: 'User', color: '#e6a23c' },
  { title: '本周会话', value: 0, sub: '次', icon: 'TrendCharts', color: '#f56c6c' }
])

const agentRank = ref([])

const loadDashboard = async () => {
  try {
    const res = await request.get('/statistics/dashboard')
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
      renderChatChart(data.chatTrend || [])
      renderOrderChart(workOrderStats.byStatus || [])
      renderCustomerChart(data.customerTrend || [])
    }
  } catch (e) {
    console.error('加载仪表盘数据失败:', e)
  }
}

const renderChatChart = (data) => {
  if (!chatChartRef.value) return
  const chart = echarts.init(chatChartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map(d => d.date) },
    yAxis: { type: 'value' },
    series: [{ data: data.map(d => d.count), type: 'line', smooth: true, areaStyle: {} }]
  })
}

const renderOrderChart = (data) => {
  if (!orderChartRef.value) return
  const statusMap = { '1': '待处理', '2': '处理中', '3': '已完成', '4': '已关闭' }
  const chart = echarts.init(orderChartRef.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: data.map(d => ({ name: statusMap[d.status] || d.status, value: d.count }))
    }]
  })
}

const renderCustomerChart = (data) => {
  if (!customerChartRef.value) return
  const chart = echarts.init(customerChartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map(d => d.date) },
    yAxis: { type: 'value' },
    series: [{ data: data.map(d => d.count), type: 'bar', itemStyle: { color: '#67c23a' } }]
  })
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}
.page-title {
  margin-bottom: 20px;
  color: #2a3f5f;
}
.stats-row {
  margin-bottom: 20px;
}
.stat-card {
  border-top: 3px solid #409eff;
  display: flex;
  align-items: center;
}
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 20px;
}
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-right: 16px;
}
.stat-info {
  flex: 1;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
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
.chart-row {
  margin-bottom: 20px;
}
.chart-box {
  width: 100%;
  height: 300px;
}

/* 移动端适配 */
@media (max-width: 640px) {
  .dashboard-container {
    padding: 12px;
  }
  .page-title {
    font-size: 18px;
    margin-bottom: 12px;
  }
  .stat-card :deep(.el-card__body) {
    padding: 12px;
  }
  .stat-icon {
    width: 40px;
    height: 40px;
    border-radius: 8px;
    margin-right: 10px;
  }
  .stat-value {
    font-size: 20px;
  }
  .chart-box {
    height: 220px;
  }
  .chart-row {
    margin-bottom: 12px;
  }
}

/* 平板：两列卡片 */
@media (min-width: 641px) and (max-width: 1024px) {
  .chart-box {
    height: 260px;
  }
}
</style>
