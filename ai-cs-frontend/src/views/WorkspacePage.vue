<template>
  <div class="workspace">
    <!-- 左：多会话列表 -->
    <aside class="ws-col ws-sessions">
      <div class="ws-col-head">
        <span>我的会话</span>
        <el-tag size="small" type="success">{{ sessions.length }} 个</el-tag>
      </div>
      <div class="ws-session-list">
        <div
          v-for="s in sessions"
          :key="s.sessionId"
          class="ws-session"
          :class="{ active: s.sessionId === activeId }"
          @click="selectSession(s)"
        >
          <div class="ws-session-top">
            <span class="ws-session-name">客户 #{{ s.customerId }}</span>
            <el-tag size="small" :type="s.sessionStatus === 1 ? 'success' : 'info'">
              {{ s.sessionStatus === 1 ? '进行中' : '已结束' }}
            </el-tag>
          </div>
          <div class="ws-session-sub">{{ s.sessionId }}</div>
        </div>
        <el-empty v-if="!sessions.length" description="暂无会话" :image-size="60" />
      </div>
    </aside>

    <!-- 中：会话消息 -->
    <section class="ws-col ws-chat">
      <div class="ws-col-head">
        <span>{{ activeId || '请选择会话' }}</span>
        <el-button size="small" text @click="endCurrent" :disabled="!activeId">结束会话</el-button>
      </div>
      <div class="ws-msgs" v-loading="loadingMsgs">
        <div v-for="m in messages" :key="m.id" class="ws-msg" :class="{ mine: m.msgType === 2 }">
          <div class="ws-bubble">{{ m.msgContent }}</div>
          <div class="ws-time">{{ m.createTime }}</div>
        </div>
        <el-empty v-if="!messages.length" description="选择左侧会话查看消息" :image-size="60" />
      </div>
      <div class="ws-input">
        <el-input v-model="draft" placeholder="输入回复（演示，不落库）" @keyup.enter="send" />
        <el-button type="primary" @click="send">发送</el-button>
      </div>
    </section>

    <!-- 右：客户业务快照 -->
    <aside class="ws-col ws-side">
      <div class="ws-col-head"><span>客户快照</span></div>
      <div class="ws-side-body">
        <div class="ws-field"><label>客户编号</label><span>{{ active?.customerId ?? '-' }}</span></div>
        <div class="ws-field"><label>会话号</label><span>{{ activeId || '-' }}</span></div>
        <div class="ws-field"><label>会话类型</label><span>{{ active?.sessionType === 2 ? '人工' : '机器人' }}</span></div>
        <div class="ws-field"><label>开始时间</label><span>{{ active?.startTime || '-' }}</span></div>
        <el-divider content-position="left">业务实体（演示）</el-divider>
        <div class="ws-card">
          <div class="ws-card-title">最近订单</div>
          <div class="ws-card-line">SO-10086 · 已发货</div>
          <div class="ws-card-line">金额 ￥198.00</div>
        </div>
        <div class="ws-card">
          <div class="ws-card-title">最近工单</div>
          <div class="ws-card-line">WO_9001 · 处理中</div>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listSessions, listSessionMessages, endSession } from '../api'

const sessions = ref([])
const messages = ref([])
const activeId = ref('')
const loadingMsgs = ref(false)
const draft = ref('')

const active = computed(() => sessions.value.find((s) => s.sessionId === activeId.value) || null)

const load = async () => {
  try {
    const res = await listSessions()
    sessions.value = res.data || []
    if (sessions.value.length && !activeId.value) selectSession(sessions.value[0])
  } catch {
    sessions.value = []
  }
}
const selectSession = async (s) => {
  activeId.value = s.sessionId
  loadingMsgs.value = true
  try {
    const res = await listSessionMessages(s.sessionId)
    messages.value = res.data || []
  } catch {
    messages.value = []
  } finally {
    loadingMsgs.value = false
  }
}
const send = () => {
  if (!draft.value.trim()) return
  messages.value.push({ id: Date.now(), msgType: 2, msgContent: draft.value, createTime: '刚刚' })
  draft.value = ''
}
const endCurrent = async () => {
  if (!activeId.value) return
  await endSession(activeId.value)
  ElMessage.success('会话已结束')
  load()
}

onMounted(load)
</script>

<style scoped>
.workspace { display: flex; gap: 12px; height: calc(100vh - 130px); }
.ws-col { background: #fff; border-radius: 10px; display: flex; flex-direction: column; overflow: hidden; box-shadow: 0 1px 3px rgba(16, 24, 40, 0.06); }
.ws-sessions { width: 260px; flex-shrink: 0; }
.ws-chat { flex: 1; min-width: 0; }
.ws-side { width: 260px; flex-shrink: 0; }
.ws-col-head { height: 46px; padding: 0 14px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #eef2f7; font-weight: 650; color: #1c2b4a; }
.ws-session-list { flex: 1; overflow-y: auto; padding: 8px; }
.ws-session { padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 6px; background: #f8fafc; }
.ws-session.active { background: #eaf1ff; }
.ws-session-top { display: flex; align-items: center; justify-content: space-between; font-size: 13px; font-weight: 600; color: #1c2b4a; }
.ws-session-sub { margin-top: 4px; font-size: 12px; color: #94a3b8; }
.ws-msgs { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.ws-msg { display: flex; flex-direction: column; align-items: flex-start; }
.ws-msg.mine { align-items: flex-end; }
.ws-bubble { max-width: 70%; padding: 9px 12px; border-radius: 10px; background: #f1f5f9; color: #1f2937; font-size: 14px; }
.ws-msg.mine .ws-bubble { background: #2f6bff; color: #fff; }
.ws-time { margin-top: 3px; font-size: 11px; color: #94a3b8; }
.ws-input { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #eef2f7; }
.ws-side-body { flex: 1; overflow-y: auto; padding: 14px; }
.ws-field { display: flex; justify-content: space-between; font-size: 13px; color: #475569; margin-bottom: 10px; }
.ws-field label { color: #94a3b8; }
.ws-card { padding: 10px 12px; border-radius: 8px; background: #f8fafc; margin-bottom: 10px; }
.ws-card-title { font-size: 13px; font-weight: 650; color: #1c2b4a; margin-bottom: 6px; }
.ws-card-line { font-size: 12px; color: #64748b; line-height: 1.7; }
</style>
