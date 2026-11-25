<template>
  <div class="confirm-card" :class="`risk-${risk || 'read'}`">
    <div class="confirm-head">
      <strong class="confirm-title">高危操作待确认</strong>
      <el-tag size="small" :type="riskTone" effect="light">{{ displayText(RISK_TEXT, risk) }}</el-tag>
      <el-tag size="small" :type="statusTone" effect="plain">{{ displayText(CONFIRM_STATUS_TEXT, status) }}</el-tag>
    </div>
    <div class="confirm-body">
      <div class="confirm-line"><label>操作</label><span>{{ toolName || '—' }}</span></div>
      <div v-if="summary" class="confirm-line">
        <label>参数</label><span class="confirm-summary">{{ summary }}</span>
      </div>
      <div v-if="requestedBy" class="confirm-line"><label>发起方</label><span>{{ requestedBy }}</span></div>
      <div class="confirm-line">
        <label>有效期</label><span :class="{ 'is-expired': expired }">{{ remainText }}</span>
      </div>
    </div>
    <div v-if="!readonly && !terminal" class="confirm-actions">
      <el-button size="small" type="primary" :loading="busy" :disabled="expired || busy" @click="emit('confirm', confirmId)">同意并执行</el-button>
      <el-button size="small" :disabled="expired || busy" @click="emit('reject', confirmId)">拒绝</el-button>
      <el-button size="small" text :disabled="busy" @click="emit('cancel', confirmId)">取消</el-button>
    </div>
    <div v-else class="confirm-foot">{{ footText }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CONFIRM_STATUS_TEXT, RISK_TEXT, displayText } from '../utils/selectOptions'

const props = defineProps({
  confirmId: { type: [Number, String], default: null },
  toolName: { type: String, default: '' },
  risk: { type: String, default: 'read' },
  status: { type: String, default: 'CONFIRMING' },
  expireAt: { type: [String, Number], default: '' },
  summary: { type: String, default: '' },
  requestedBy: { type: String, default: '' },
  /** C 端只看卡片与结果，不显示操作按钮 */
  readonly: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})
const emit = defineEmits(['confirm', 'reject', 'cancel'])

const TERMINAL = ['SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED', 'CANCELLED']
const terminal = computed(() => TERMINAL.includes(props.status))
const riskTone = computed(() => ({ critical: 'danger', write: 'warning' }[props.risk] || 'success'))
const statusTone = computed(() => ({
  SUCCEEDED: 'success',
  FAILED: 'danger',
  REJECTED: 'danger',
  EXPIRED: 'info',
  CANCELLED: 'info',
  CONFIRMED: 'warning'
}[props.status] || 'warning'))
const footText = computed(() => {
  if (terminal.value) return `该操作已${displayText(CONFIRM_STATUS_TEXT, props.status)}`
  if (props.readonly) return '请坐席在「待确认」页处理'
  return '已过期，请重新发起'
})

const now = ref(Date.now())
let clockTimer = null
const expireTime = computed(() => {
  if (!props.expireAt) return Infinity
  const t = new Date(String(props.expireAt).replace(/-/g, '/')).getTime()
  return Number.isNaN(t) ? Infinity : t
})
const expired = computed(() => expireTime.value !== Infinity && expireTime.value <= now.value)
const remainText = computed(() => {
  if (expireTime.value === Infinity) return '长期有效'
  const ms = expireTime.value - now.value
  if (ms <= 0) return '已过期'
  const m = Math.floor(ms / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return m > 0 ? `${m} 分 ${String(s).padStart(2, '0')} 秒` : `${s} 秒`
})

onMounted(() => { clockTimer = setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => { if (clockTimer) clearInterval(clockTimer) })
</script>

<style scoped>
.confirm-card { width: min(420px, 100%); border: 1px solid #fedf89; border-left: 4px solid #f79009; border-radius: 10px; background: #fffaf0; padding: 12px 14px; box-shadow: 0 1px 3px rgba(16,24,40,.06); }
.confirm-card.risk-critical { border-color: #fecdca; border-left-color: #f04438; background: #fef3f2; }
.confirm-card.risk-read { border-color: #abefc6; border-left-color: #12b76a; background: #f0fdf4; }
.confirm-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.confirm-title { font-size: 13px; color: #1c2b4a; }
.confirm-body { display: flex; flex-direction: column; gap: 6px; }
.confirm-line { display: flex; gap: 8px; font-size: 12px; line-height: 1.6; color: #344054; }
.confirm-line label { flex: 0 0 44px; color: #98a2b3; }
.confirm-summary { word-break: break-all; }
.is-expired { color: #f04438; font-weight: 600; }
.confirm-actions { display: flex; gap: 8px; margin-top: 12px; }
.confirm-foot { margin-top: 10px; font-size: 11px; color: #98a2b3; }
</style>
