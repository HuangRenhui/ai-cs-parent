<template>
  <div class="page-card">
    <p class="hint">高危操作（退款 / 发券 / 改单）必须由人显式确认后才会执行；列表按快过期的排在前面，租户以登录令牌为准。</p>

    <div class="page-toolbar is-actions-only">
      <el-switch v-model="autoRefresh" size="small" />
      <span class="refresh-label">自动刷新（5 秒）</span>
      <el-button size="small" :loading="loading" @click="load">立即刷新</el-button>
      <el-tag v-if="todoCount" type="danger" effect="dark" size="small">{{ todoCount }} 条待处理</el-tag>
      <span class="last-time">{{ lastCheckedAt ? `上次刷新 ${lastCheckedAt}` : '' }}</span>
    </div>

    <div class="page-toolbar">
      <el-form :inline="true" :model="query" @submit.prevent="search">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="(text, code) in CONFIRM_STATUS_TEXT" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="会话号">
          <el-input v-model="query.sessionId" clearable placeholder="会话 ID" />
        </el-form-item>
        <el-form-item label="租户（平台账号可选）">
          <el-input v-model="query.tenantCode" clearable placeholder="留空按令牌" style="width: 140px" />
        </el-form-item>
        <el-form-item><FilterActions @search="search" @reset="resetSearch" /></el-form-item>
      </el-form>
    </div>

    <el-table :data="records" stripe v-loading="loading" table-layout="fixed" max-height="680">
      <el-table-column label="工具 / 确认单" min-width="150">
        <template #default="{ row }">
          <div class="tool-name">{{ row.toolName || '—' }}</div>
          <div class="tool-sub">#{{ row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column label="风险" min-width="90">
        <template #default="{ row }"><TypeTag :text="displayText(RISK_TEXT, row.risk)" /></template>
      </el-table-column>
      <el-table-column label="参数摘要" min-width="220">
        <template #default="{ row }"><CellText title="参数摘要" :text="summaryOf(row)" /></template>
      </el-table-column>
      <el-table-column label="发起方" min-width="110">
        <template #default="{ row }">{{ row.requestedBy || '—' }}</template>
      </el-table-column>
      <el-table-column label="发起时间" min-width="160">
        <template #default="{ row }">{{ row.createTime || '—' }}</template>
      </el-table-column>
      <el-table-column label="剩余时间" min-width="120">
        <template #default="{ row }">
          <span :class="{ 'text-danger': remainMs(row.expireAt) <= 0 }">{{ remainText(row.expireAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTone(row.status)" effect="plain">
            {{ displayText(CONFIRM_STATUS_TEXT, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="180" fixed="right">
        <template #default="{ row }">
          <div class="table-actions" v-if="!terminal(row.status)">
            <el-button link type="primary" :disabled="remainMs(row.expireAt) <= 0 || busyId === row.id" @click="doConfirm(row)">同意</el-button>
            <el-button link type="danger" :disabled="remainMs(row.expireAt) <= 0 || busyId === row.id" @click="openReject(row)">拒绝</el-button>
            <el-button link :disabled="busyId === row.id" @click="doCancel(row)">取消</el-button>
            <el-button link @click="openDetail(row)">详情</el-button>
          </div>
          <div class="table-actions" v-else>
            <el-button link @click="openDetail(row)">详情</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="query.pageNum" v-model:size="query.pageSize" :total="total" @change="load" />

    <!-- 详情：只展示白名单字段，不把整个 requestJson 原样丢给确认人 -->
    <el-dialog v-model="detailVisible" title="确认详情（请核对参数）" width="560px">
      <el-descriptions :column="1" border size="small" v-if="detail">
        <el-descriptions-item label="工具">{{ detail.toolName }}</el-descriptions-item>
        <el-descriptions-item label="会话">{{ detail.sessionId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="风险">{{ displayText(RISK_TEXT, detail.risk) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ displayText(CONFIRM_STATUS_TEXT, detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ detail.requestedBy || '—' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ detail.expireAt || '长期有效' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.rejectedReason" label="拒绝理由">{{ detail.rejectedReason }}</el-descriptions-item>
      </el-descriptions>
      <div class="detail-title">关键参数（白名单）</div>
      <div class="detail-pairs">
        <div v-for="p in detailPairs" :key="p.label" class="detail-pair">
          <span class="detail-k">{{ p.label }}</span><span class="detail-v">{{ p.value }}</span>
        </div>
        <div v-if="!detailPairs.length" class="detail-empty">无可展示字段，请谨慎确认</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail && !terminal(detail.status) && remainMs(detail.expireAt) > 0" type="primary"
                   :loading="busyId === detail.id" @click="doConfirm(detail)">同意并执行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="拒绝该操作" width="440px">
      <p class="hint">理由会写入审计并回给用户，便于重新发起。</p>
      <el-input v-model="reason" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="如：金额与订单不符" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="!!busyId" @click="submitReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TablePager from '../../components/TablePager.vue'
import { pageToolPending, getToolPending, confirmOpenTool, rejectOpenTool, cancelOpenTool } from '../../api'
import { CONFIRM_STATUS_TEXT, RISK_TEXT, displayText } from '../../utils/selectOptions'
import { setItem } from '../../utils/safeStorage'

const REFRESH_MS = 5000
const TERMINAL = ['SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED', 'CANCELLED']
const WHITELIST = {
  entityId: '业务实体', orderNo: '订单号', amount: '金额', refundAmount: '退款金额',
  couponNo: '券号', productName: '商品', qty: '数量', reason: '原因'
}

const query = ref({ pageNum: 1, pageSize: 10, status: 'CONFIRMING', sessionId: '', tenantCode: '' })
const records = ref([])
const total = ref(0)
const todoCount = ref(0)
const loading = ref(false)
const autoRefresh = ref(true)
const busyId = ref(null)
const lastCheckedAt = ref('')
const now = ref(Date.now())
const detailVisible = ref(false)
const detail = ref(null)
const rejectVisible = ref(false)
const rejectTarget = ref(null)
const reason = ref('')
let timer = null
let clockTimer = null

const terminal = (s) => TERMINAL.includes(s)
const statusTone = (s) => ({
  SUCCEEDED: 'success', CONFIRMED: 'warning', CONFIRMING: 'warning',
  FAILED: 'danger', REJECTED: 'danger', EXPIRED: 'info', CANCELLED: 'info'
}[s] || 'info')

const parseAny = (raw) => {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try { return JSON.parse(raw) || {} } catch { return {} }
}
const whitelistPairs = (row) => Object.keys(WHITELIST)
  .filter((k) => row?.[k] != null && row[k] !== '')
  .map((k) => ({ label: WHITELIST[k], value: String(row[k]) }))
const summaryOf = (row) => {
  const merged = { ...parseAny(row.payload), ...parseAny(row.requestJson) }
  if (row.entityId) merged.entityId = row.entityId
  const pairs = whitelistPairs(merged)
  return pairs.length ? pairs.map((p) => `${p.label}=${p.value}`).join(' · ') : '（无可展示字段）'
}
const detailPairs = computed(() => whitelistPairs({
  ...parseAny(detail.value?.requestJson),
  ...parseAny(detail.value?.payload),
  entityId: detail.value?.entityId
}))

const remainMs = (expireAt) => {
  if (!expireAt) return Infinity
  const t = new Date(String(expireAt).replace(/-/g, '/')).getTime()
  return Number.isNaN(t) ? Infinity : t - now.value
}
const remainText = (expireAt) => {
  const ms = remainMs(expireAt)
  if (ms === Infinity) return '长期有效'
  if (ms <= 0) return '已过期'
  const m = Math.floor(ms / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return m > 0 ? `${m} 分 ${String(s).padStart(2, '0')} 秒` : `${s} 秒`
}
const publishBadge = (n) => {
  setItem('toolConfirmBadge', String(n))
  window.dispatchEvent(new CustomEvent('tool-confirm-count', { detail: n }))
}

const load = async () => {
  if (busyId.value) return
  loading.value = true
  try {
    const params = {
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      status: query.value.status || undefined,
      sessionId: query.value.sessionId || undefined,
      tenantCode: query.value.tenantCode || undefined
    }
    const res = await pageToolPending(params)
    const data = res.data || {}
    records.value = data.records || []
    total.value = Number(data.total || 0)
    if (query.value.status === 'CONFIRMING' || !query.value.status) {
      todoCount.value = total.value
    } else {
      const cnt = await pageToolPending({ pageNum: 1, pageSize: 1, status: 'CONFIRMING', tenantCode: params.tenantCode })
      todoCount.value = Number(cnt.data?.total || 0)
    }
    publishBadge(todoCount.value)
    lastCheckedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    /* 拦截器已提示；自动刷新失败时保留上一次结果 */
  } finally {
    loading.value = false
  }
}
const search = () => { query.value.pageNum = 1; load() }
const resetSearch = () => {
  query.value.status = 'CONFIRMING'
  query.value.sessionId = ''
  query.value.tenantCode = ''
  query.value.pageNum = 1
  load()
}

const openDetail = async (row) => {
  try {
    const res = await getToolPending(row.id, query.value.tenantCode || undefined)
    detail.value = res.data || row
    detailVisible.value = true
  } catch {
    detail.value = row
    detailVisible.value = true
  }
}

const doConfirm = async (row) => {
  busyId.value = row.id
  try {
    const res = await confirmOpenTool({ confirmId: row.id })
    const out = res.data || {}
    ElMessage.success(out.success ? '已同意并执行成功' : (out.output || '已提交确认'))
    detailVisible.value = false
    await load()
  } catch {
    /* 403 / 业务错误由拦截器提示 */
  } finally {
    busyId.value = null
  }
}
const openReject = (row) => {
  rejectTarget.value = row
  reason.value = ''
  rejectVisible.value = true
}
const submitReject = async () => {
  const r = rejectTarget.value
  if (!r || !reason.value.trim()) return
  busyId.value = r.id
  try {
    await rejectOpenTool({ confirmId: r.id, reason: reason.value.trim().slice(0, 255) })
    ElMessage.success('已拒绝')
    rejectVisible.value = false
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busyId.value = null
  }
}
const doCancel = async (row) => {
  try {
    await ElMessageBox.confirm('取消后该待确认不再执行，用户需重新发起。', '取消确认', { type: 'warning' })
  } catch { return }
  busyId.value = row.id
  try {
    await cancelOpenTool({ confirmId: row.id, reason: '坐席取消' })
    ElMessage.success('已取消')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busyId.value = null
  }
}

watch(autoRefresh, (on) => {
  if (timer) { clearInterval(timer); timer = null }
  if (on) timer = setInterval(load, REFRESH_MS)
})
onMounted(() => {
  load()
  clockTimer = setInterval(() => { now.value = Date.now() }, 1000)
  if (autoRefresh.value) timer = setInterval(load, REFRESH_MS)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.refresh-label { font-size: 12px; color: #667085; margin-right: 4px; }
.last-time { margin-left: auto; font-size: 12px; color: #98a2b3; }
.tool-name { font-size: 13px; font-weight: 600; color: #1c2b4a; }
.tool-sub { font-size: 11px; color: #98a2b3; }
.text-danger { color: #f04438; font-weight: 600; }
.detail-title { margin: 14px 0 8px; font-size: 13px; font-weight: 600; color: #1c2b4a; }
.detail-pairs { max-height: 200px; overflow: auto; border: 1px solid #eef2f7; border-radius: 8px; padding: 8px 10px; }
.detail-pair { display: flex; gap: 10px; font-size: 12px; line-height: 1.9; }
.detail-k { flex: 0 0 74px; color: #98a2b3; }
.detail-v { color: #344054; word-break: break-all; }
.detail-empty { font-size: 12px; color: #f79009; }
</style>
