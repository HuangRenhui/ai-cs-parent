<template>
  <div>
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="item in cards" :key="item.label">
        <div class="stat-card" @click="$router.push(item.path)">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-desc">{{ item.desc }}</div>
        </div>
      </el-col>
    </el-row>
    <div class="page-card">
      <p class="hint">{{ overview.hint || '一线坐席不进本模块。登录与 OPS_VIEW 权限尚未接入，当前仅内网可用。' }}</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getOpsOverview } from '../../api'

const overview = ref({})
const cards = ref([
  { label: '服务存活', value: '-', desc: '健康探测', path: '/ops/health' },
  { label: '服务宕机', value: '-', desc: '不可达', path: '/ops/health' },
  { label: '日志文件', value: '-', desc: '本地适配器', path: '/ops/logs' },
  { label: '错误/警告', value: '-', desc: '已扫描行', path: '/ops/logs' },
  { label: '未恢复告警', value: '-', desc: '临时事件', path: '/ops/alerts' },
  { label: '适配器', value: '-', desc: '查询后端', path: '/ops/logs' }
])

const load = async () => {
  const res = await getOpsOverview()
  const data = res.data || {}
  overview.value = data
  cards.value = [
    { label: '服务存活', value: `${data.servicesUp ?? 0}/${data.servicesTotal ?? 0}`, desc: '健康探测', path: '/ops/health' },
    { label: '服务宕机', value: data.servicesDown ?? 0, desc: '不可达', path: '/ops/health' },
    { label: '日志文件', value: data.logFiles ?? 0, desc: '本地适配器', path: '/ops/logs' },
    { label: '错误/警告', value: data.recentErrorCount ?? 0, desc: '已扫描行', path: '/ops/logs' },
    { label: '未恢复告警', value: data.pendingAlerts ?? 0, desc: '临时事件', path: '/ops/alerts' },
    { label: '适配器', value: data.adapter || 'file', desc: '查询后端', path: '/ops/logs' }
  ]
}

onMounted(load)
</script>

<style scoped>
.stat-row { margin-bottom: 16px; }
.stat-card {
  background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 16px; cursor: pointer;
  box-shadow: 0 8px 24px rgba(31, 42, 55, 0.04);
}
.stat-card:hover { transform: translateY(-2px); }
.stat-label { color: #6b7280; font-size: 13px; }
.stat-value { font-size: 28px; font-weight: 700; margin: 8px 0 4px; }
.stat-desc { color: #9aa3b2; font-size: 12px; }
.hint { color: #6b7280; line-height: 1.6; font-size: 13px; }
@media (max-width: 640px) {
  .stat-card { padding: 12px; margin-bottom: 12px; }
  .stat-value { font-size: 22px; }
}
</style>
