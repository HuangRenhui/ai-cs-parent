<template>
  <div class="page-card">
    <p class="hint">探测各服务端口是否可达。管理端口不对外暴露，本页经网关访问；异常服务会自动置顶。</p>
    <!-- 运维顶栏切片已标明「服务健康」，不再重复标题 -->

    <div class="page-toolbar is-actions-only">
      <span v-if="lastCheckedAt" class="checked-at">上次探测 {{ lastCheckedAt }}</span>
      <el-radio-group v-model="filter" size="small">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="down">仅异常</el-radio-button>
      </el-radio-group>
      <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" />
      <el-button :loading="loading" @click="load">重新探测</el-button>
    </div>

    <div class="metric-row">
      <div class="metric"><span class="metric-num">{{ list.length }}</span><span class="metric-label">服务总数</span></div>
      <div class="metric"><span class="metric-num up">{{ upCount }}</span><span class="metric-label">存活</span></div>
      <div class="metric"><span class="metric-num down">{{ downCount }}</span><span class="metric-label">异常</span></div>
      <div class="metric"><span class="metric-num">{{ avgLatency }} ms</span><span class="metric-label">平均延迟</span></div>
    </div>

    <el-table
      :data="records"
      stripe
      v-loading="loading"
      empty-text="无法探测，请确认运维服务已启动"
      table-layout="fixed"
      max-height="600"
    >
      <el-table-column label="服务" min-width="132" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="dot" :class="row.status === 'UP' ? 'dot-up' : 'dot-down'"></span>
          {{ displayText(SERVICE_NAME_TEXT, row.name) }}
        </template>
      </el-table-column>
      <el-table-column label="探测地址" min-width="210">
        <template #default="{ row }">
          <span class="addr">{{ row.url }}</span>
          <el-button link type="primary" @click="copy(row.url)">复制</el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="row.status === 'UP' ? 'success' : 'danger'" effect="plain">
            {{ displayText(HEALTH_UP_TEXT, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="httpStatus" label="状态码" width="84" />
      <el-table-column label="延迟" min-width="180">
        <template #default="{ row }">
          <div v-if="row.status === 'UP'" class="latency">
            <div class="latency-bar">
              <i :style="{ width: barWidth(row.latencyMs), background: barColor(row.latencyMs) }"></i>
            </div>
            <span class="latency-num">{{ row.latencyMs }} ms</span>
          </div>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="140">
        <template #default="{ row }">
          <CellText title="说明" :text="row.message" />
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />
  </div>
</template>

<script setup>
/**
 * 服务健康：一屏看清哪些服务活着、哪个慢。
 * 异常置顶 + 延迟条形可视化 + 可开自动刷新（30s）。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getOpsHealth } from '../../api'
import { displayText, HEALTH_UP_TEXT, SERVICE_NAME_TEXT } from '../../utils/selectOptions'
import { useClientPager } from '../../composables/useClientPager'
import TablePager from '../../components/TablePager.vue'

const AUTO_REFRESH_MS = 30000

const list = ref([])
const loading = ref(false)
const filter = ref('all')
const autoRefresh = ref(false)
const lastCheckedAt = ref('')

const upCount = computed(() => list.value.filter((s) => s.status === 'UP').length)
const downCount = computed(() => list.value.length - upCount.value)

/** 平均延迟只统计存活服务，宕机的没有参考价值 */
const avgLatency = computed(() => {
  const ups = list.value.filter((s) => s.status === 'UP' && Number(s.latencyMs) > 0)
  if (!ups.length) return 0
  return Math.round(ups.reduce((a, b) => a + Number(b.latencyMs), 0) / ups.length)
})

const maxLatency = computed(() =>
  Math.max(1, ...list.value.filter((s) => s.status === 'UP').map((s) => Number(s.latencyMs) || 0))
)

/** 异常置顶，同状态内按延迟降序，慢的排前面 */
const sorted = computed(() => {
  const arr = [...list.value].sort((a, b) => {
    if (a.status !== b.status) return a.status === 'UP' ? 1 : -1
    return (Number(b.latencyMs) || 0) - (Number(a.latencyMs) || 0)
  })
  return filter.value === 'down' ? arr.filter((s) => s.status !== 'UP') : arr
})

const { page, size, total, records } = useClientPager(sorted)

const barWidth = (v) => `${Math.min(100, Math.round(((Number(v) || 0) / maxLatency.value) * 100))}%`
const barColor = (v) => {
  const n = Number(v) || 0
  if (n > 200) return '#f04438'
  if (n > 80) return '#f79009'
  return '#12b76a'
}

const copy = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制探测地址')
  } catch {
    ElMessage.warning('浏览器不允许复制，请手动选中')
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await getOpsHealth()
    list.value = res.data || []
    lastCheckedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    /* 拦截器已提示；自动刷新失败时保留上一次结果，避免表被清空 */
  } finally {
    loading.value = false
  }
}

let timer = null
const stopTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, (on) => {
  stopTimer()
  if (on) timer = setInterval(load, AUTO_REFRESH_MS)
})

onMounted(load)

onBeforeUnmount(stopTimer)
</script>

<style scoped>
.hint {
  color: #6b7280;
  line-height: 1.6;
  font-size: 13px;
  margin-bottom: 12px;
}
.checked-at {
  margin-right: auto;
  font-size: 12px;
  color: #98a2b3;
}
.metric-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.metric {
  flex: 1 1 130px;
  min-width: 130px;
  padding: 12px 14px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-num {
  font-size: 20px;
  font-weight: 700;
  color: #1c2b4a;
}
.metric-num.up {
  color: #12b76a;
}
.metric-num.down {
  color: #f04438;
}
.metric-label {
  font-size: 12px;
  color: #6b7280;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.dot-up {
  background: #12b76a;
  box-shadow: 0 0 0 3px rgba(18, 183, 106, 0.15);
}
.dot-down {
  background: #f04438;
  box-shadow: 0 0 0 3px rgba(240, 68, 56, 0.15);
}
.addr {
  color: #667085;
  font-size: 12px;
  margin-right: 8px;
}
.latency {
  display: flex;
  align-items: center;
  gap: 10px;
}
.latency-bar {
  flex: 1;
  height: 8px;
  min-width: 60px;
  border-radius: 999px;
  background: #eef1f6;
  overflow: hidden;
}
.latency-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}
.latency-num {
  font-size: 12px;
  color: #667085;
  font-variant-numeric: tabular-nums;
  min-width: 58px;
  text-align: right;
}
.muted {
  color: #98a2b3;
}
</style>
