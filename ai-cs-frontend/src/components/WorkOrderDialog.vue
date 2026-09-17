<template>
  <!-- 统一工单弹窗：聊天页与工单页共用，避免各页各写一套简陋表单 -->
  <el-dialog
    class="cs-dialog"
    :title="isEdit ? '编辑工单' : '创建工单'"
    :model-value="modelValue"
    width="580px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="dialog-lead">{{ leadText }}</p>
    <el-form :model="form" label-width="96px" class="wo-form">
      <el-form-item label="工单类型" required>
        <el-select v-model="form.orderType" placeholder="请选择问题类型">
          <el-option label="咨询" value="咨询" />
          <el-option label="投诉" value="投诉" />
          <el-option label="建议" value="建议" />
          <el-option label="售后" value="售后" />
          <el-option label="退款" value="退款" />
        </el-select>
      </el-form-item>
      <el-form-item label="紧急程度">
        <!-- 全局主题会把分段条拉满并让文字居中，与上下输入同宽 -->
        <el-radio-group v-model="form.priority">
          <el-radio-button label="普通">普通</el-radio-button>
          <el-radio-button label="紧急">紧急</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="工单内容" required>
        <el-input
          v-model="form.orderContent"
          type="textarea"
          :rows="5"
          maxlength="500"
          show-word-limit
          placeholder="请描述客户问题、已沟通情况和期望处理结果"
        />
      </el-form-item>
      <el-form-item v-if="showSession" label="关联会话">
        <div class="session-row">
          <el-select
            v-model="form.sessionId"
            filterable
            clearable
            :disabled="sessionLocked"
            placeholder="选择已有会话，可不关联"
            style="flex: 1"
            @change="onSessionChange"
          >
            <el-option
              v-for="s in sessionOptions"
              :key="s.sessionId"
              :label="sessionLabel(s)"
              :value="s.sessionId"
            />
          </el-select>
          <el-button v-if="!sessionLocked" :loading="creatingSession" @click="createRandomSession">
            生成新会话
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="关联客户">
        <el-select
          v-model="form.customerId"
          filterable
          clearable
          placeholder="选择客户档案"
        >
          <el-option
            v-for="c in customers"
            :key="c.id"
            :label="customerLabel(c)"
            :value="String(c.id)"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('submit')">
        {{ isEdit ? '保存修改' : '提交工单' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 创建/编辑工单弹窗。会话从列表选择或一键生成，客户从档案选择。
 * 聊天页会锁定当前会话；工单页可改选或新建会话。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ensureSession } from '../api'

const props = defineProps({
  /** 是否显示弹窗 */
  modelValue: { type: Boolean, default: false },
  /** 双向绑定的表单对象 */
  form: { type: Object, required: true },
  /** 是否编辑模式 */
  isEdit: { type: Boolean, default: false },
  /** 提交中 */
  loading: { type: Boolean, default: false },
  /** 是否展示会话字段 */
  showSession: { type: Boolean, default: true },
  /** 会话是否只读（聊天页锁定当前会话） */
  sessionLocked: { type: Boolean, default: false },
  /** 客户下拉选项 */
  customers: { type: Array, default: () => [] },
  /** 已有会话列表 */
  sessions: { type: Array, default: () => [] }
})

defineEmits(['update:modelValue', 'submit'])

/** 本弹窗内新生成、尚未出现在 props.sessions 里的会话 */
const extraSessions = ref([])
const creatingSession = ref(false)

const leadText = computed(() =>
  props.isEdit
    ? '修改工单信息后将同步到工单列表。'
    : '请填写问题类型与详细描述，便于坐席快速定位并跟进。'
)

/** 合并列表会话、刚生成的会话，以及编辑态已有但不在列表中的 ID */
const sessionOptions = computed(() => {
  const seen = new Set()
  const out = []
  const push = (s) => {
    if (!s?.sessionId || seen.has(s.sessionId)) return
    seen.add(s.sessionId)
    out.push(s)
  }
  extraSessions.value.forEach(push)
  ;(props.sessions || []).forEach(push)
  if (props.form.sessionId && !seen.has(props.form.sessionId)) {
    push({ sessionId: props.form.sessionId })
  }
  return out
})

/** 下拉展示：昵称 + 手机号，方便客服辨认 */
const customerLabel = (c) => {
  const name = c.nickname || c.phone || '客户'
  const extra = c.phone && c.nickname ? ` · ${c.phone}` : ''
  return `${name}${extra}`
}

/** 会话下拉：编号 + 客户 + 状态 */
const sessionLabel = (s) => {
  const cust = props.customers.find((c) => String(c.id) === String(s.customerId))
  const name = cust ? (cust.nickname || cust.phone) : ''
  const st = s.sessionStatus === 2 ? '已结束' : (s.sessionStatus === 1 ? '进行中' : '')
  return [name || '未关联客户', st].filter(Boolean).join(' · ') || '会话'
}

/** 选中会话后带出对应客户，减少再选一次 */
const onSessionChange = (sid) => {
  if (!sid) return
  const s = sessionOptions.value.find((x) => x.sessionId === sid)
  if (s?.customerId) props.form.customerId = String(s.customerId)
}

/** 前端生成 sess_时间戳 并确保会话落库，避免手填 ID */
const createRandomSession = async () => {
  creatingSession.value = true
  try {
    const sid = 'sess_' + Date.now()
    const customerId = props.form.customerId ? Number(props.form.customerId) : undefined
    await ensureSession({ sessionId: sid, sessionType: 1, customerId })
    extraSessions.value = [{ sessionId: sid, customerId: customerId || 0, sessionStatus: 1 }, ...extraSessions.value]
    props.form.sessionId = sid
    ElMessage.success('已生成会话并关联')
  } catch {
    /* 拦截器已提示 */
  } finally {
    creatingSession.value = false
  }
}
</script>

<style scoped>
.session-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
</style>
