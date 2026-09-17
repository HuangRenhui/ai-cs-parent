<template>
  <div class="chat-container">
    <div class="chat-header">
      <div class="header-left">
        <h2>智能客服</h2>
        <p class="header-sub">{{ transferredAgent ? '人工接待中' : '智能接待中' }}</p>
      </div>
      <!-- 顶栏：帮助中心 + 接待状态 + 转人工 + 新对话；历史靠上滑加载 -->
      <div class="header-actions">
        <el-button class="hdr-btn" size="small" @click="openHelpCenter">
          <el-icon><Reading /></el-icon>
          帮助中心
        </el-button>
        <div class="status-chip" :class="transferredAgent ? 'human' : 'ai'">
          <i class="pulse"></i>
          <span>{{ transferredAgent ? `工号 ${transferredAgent.agentNo} ${transferredAgent.agentName}` : '智能接待' }}</span>
        </div>
        <el-button
          v-if="!transferredAgent"
          class="hdr-btn"
          size="small"
          :loading="transferring"
          @click="handleTransfer"
        >
          <el-icon><Headset /></el-icon>
          转人工
        </el-button>
        <el-button class="hdr-btn" size="small" @click="clearSession">新对话</el-button>
      </div>
    </div>

    <!-- 接入后固定在消息区顶部，类似真实客服系统的接待条 -->
    <div v-if="transferredAgent" class="agent-banner">
      <div class="avatar-dot">{{ agentInitial(transferredAgent.agentName) }}</div>
      <div>
        <strong>工号 {{ transferredAgent.agentNo }}　{{ transferredAgent.agentName }}</strong>
        <span>{{ transferredAgent.skill || '综合客服' }} · 正在为您服务</span>
      </div>
    </div>

    <div class="chat-messages" ref="messagesRef" @scroll="onHistoryScroll">
      <div class="history-tip">
        <span v-if="historyLoading">数据加载中，请等待</span>
        <span v-else-if="hasMore">上滑查看更早的聊天记录</span>
        <span v-else>已显示全部记录</span>
      </div>
      <template v-for="(msg, index) in messages" :key="msg.id || index">
        <!-- 坐席接入系统卡：不走气泡，避免被当成 AI 回复 -->
        <div v-if="msg.kind === 'agent-join'" class="agent-join-wrap">
          <div class="agent-join-card">
            <div class="avatar-dot">{{ agentInitial(msg.agent.agentName) }}</div>
            <div class="join-meta">
              <strong>工号 {{ msg.agent.agentNo }}　{{ msg.agent.agentName }}</strong>
              <span>{{ msg.agent.skill || '综合客服' }} · 已接入，正在为您服务</span>
            </div>
          </div>
          <div class="time">{{ msg.time }}</div>
        </div>
        <div v-else :class="['message', msg.isAI ? 'ai-message' : 'user-message']">
          <div class="avatar">
            <el-icon v-if="msg.isAI"><ChatDotRound /></el-icon>
            <el-icon v-else><User /></el-icon>
          </div>
          <div class="message-content">
            <div class="content">{{ msg.content }}</div>
            <div v-if="msg.citations && msg.citations.length" class="citations">
              引用：
              <span v-for="c in msg.citations" :key="c.faqId">#{{ c.faqId }} {{ c.question }}</span>
            </div>
            <div class="time">{{ msg.time }}</div>
          </div>
        </div>
      </template>
      <div v-if="isLoading" class="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在思考...</span>
      </div>
    </div>
    <div class="chat-input">
      <el-input
        v-model="inputMessage"
        placeholder="请输入消息..."
        @keyup.enter="sendMessage"
      />
      <el-button type="primary" @click="sendMessage">发送</el-button>
    </div>

    <WorkOrderDialog
      v-model="showCreateOrderDialog"
      :form="orderForm"
      :loading="savingOrder"
      session-locked
      :customers="customers"
      :sessions="orderSessions"
      @submit="createOrder"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ChatDotRound, Headset, Reading, User, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { createWorkOrder, ensureSession, interruptChat, listCustomers, listOpenPacks, listAgents, transferSession } from '../api'
import { isMockEnabled } from '../mock'
import { openHelpCenter } from '../composables/useHelpCenter'
import WorkOrderDialog from '../components/WorkOrderDialog.vue'

const HISTORY_FIRST = 10
const HISTORY_MORE = 20
let msgSeq = 0
const nextMsgId = () => ++msgSeq

/** 演示历史：先铺一批旧对话，方便上滑加载；真实环境只有开场白 */
const seedDemoHistory = () => {
  const turns = [
    ['我的订单怎么还没发货？', '请提供订单号，我帮您查物流进度。'],
    ['订单号是 SO-10086', '该订单已出库，预计明日送达。'],
    ['能改收货地址吗？', '发货前可在订单详情修改一次地址。'],
    ['怎么申请退款？', '提交售后申请后 1-3 个工作日原路退回。'],
    ['退款会退到哪里？', '原支付渠道，微信或银行卡原路返回。'],
    ['有优惠券吗？', '登录后在「我的优惠券」查看可用券。'],
    ['支持货到付款吗？', '部分地区支持，下单页会标明支付方式。'],
    ['包装破损怎么办？', '请拍照联系客服，我们为您补发或退款。'],
    ['会员有什么权益？', '包邮、积分加倍，以及专属客服通道。'],
    ['发票怎么开？', '在订单详情申请电子发票，一般当日开具。'],
    ['能预约送货时间吗？', '可在物流页面备注期望时段，我们会尽量协调。'],
    ['商品和图片不一样', '请提供订单号和照片，我们按售后流程处理。'],
    ['积分怎么用？', '结算时可勾选积分抵扣，100 积分抵 1 元。'],
    ['忘记密码了', '用注册手机号找回，验证码 5 分钟内有效。'],
    ['客服电话是多少？', '您可以直接在本页继续咨询，我们会尽快答复。'],
    ['周末有人值班吗？', '在线客服 7×12 小时，夜间可留言次日回复。'],
    ['能开发票到公司吗？', '可以，请提供公司抬头与税号。'],
    ['物流一直不更新', '已帮您催促承运商，请稍后再看轨迹。'],
    ['想转人工', '当前为智能接待，您可以继续描述问题，我们会尽力解答。'],
    ['谢谢', '不客气，还有问题随时问我。']
  ]
  const list = [{
    id: nextMsgId(),
    content: '您好！我是智能客服，请问有什么可以帮助您的？',
    isAI: true,
    time: '09:00:00'
  }]
  turns.forEach(([userText, aiText], i) => {
    const mm = String(i + 1).padStart(2, '0')
    list.push({ id: nextMsgId(), content: userText, isAI: false, time: `09:${mm}:00` })
    list.push({ id: nextMsgId(), content: aiText, isAI: true, time: `09:${mm}:08` })
  })
  return list
}

const demoMode = isMockEnabled()
const archive = ref(demoMode
  ? seedDemoHistory()
  : [{
      id: nextMsgId(),
      content: '您好！我是智能客服，请问有什么可以帮助您的？',
      isAI: true,
      time: new Date().toLocaleTimeString()
    }])
/** 从最新往回展示的条数，默认 10，上滑再加 20 */
const shown = ref(Math.min(HISTORY_FIRST, archive.value.length))
const historyLoading = ref(false)
const messages = computed(() => archive.value.slice(-shown.value))
const hasMore = computed(() => shown.value < archive.value.length)
const inputMessage = ref('')
const messagesRef = ref(null)
const isLoading = ref(false)
const showCreateOrderDialog = ref(false)
const savingOrder = ref(false)
const sessionId = ref('sess_' + Date.now())
const packCode = ref('')
const packs = ref([])
const useStream = ref(false)
const transferring = ref(false)
/** 当前接入坐席；有值表示已转人工 */
const transferredAgent = ref(null)
const customers = ref([])
/** 聊天页工单锁定当前会话，下拉只展示这一条 */
const orderSessions = computed(() => [{ sessionId: sessionId.value, sessionStatus: 1 }])
let socket = null
let closedByUser = false
let reconnectTimer = null
let replyTimer = null
let streamAbort = null
const REPLY_TIMEOUT_MS = 35000

const orderForm = ref({
  orderType: '咨询',
  orderContent: '',
  customerId: '',
  sessionId: '',
  priority: '普通'
})

/** 取姓名最后一个字做头像底字，贴近真实客服头像占位 */
const agentInitial = (name) => (name && String(name).trim().slice(-1)) || '席'

/** 把接口/列表里的坐席字段归一成接待卡结构 */
const normalizeAgent = (raw) => {
  if (!raw || typeof raw !== 'object') return null
  const id = raw.agentId || raw.id
  const name = raw.agentName
  if (!name && !raw.agentNo && !id) return null
  return {
    agentId: id,
    agentNo: raw.agentNo || (id ? 'A' + String(id).padStart(3, '0') : ''),
    agentName: name || '客服',
    skill: raw.skill || '综合客服',
    agentAccount: raw.agentAccount || ''
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

/** 追加一条到完整记录，并保证新消息落在当前可见窗口里 */
const appendMessage = (msg) => {
  const row = { ...msg, id: msg.id || nextMsgId() }
  archive.value.push(row)
  shown.value += 1
  return row
}

/** 滚到顶部时再取出 20 条更早的记录，并稳住当前视口 */
const loadOlderHistory = () => {
  if (!hasMore.value || historyLoading.value) return
  const el = messagesRef.value
  const prevHeight = el ? el.scrollHeight : 0
  historyLoading.value = true
  window.setTimeout(() => {
    shown.value = Math.min(shown.value + HISTORY_MORE, archive.value.length)
    nextTick(() => {
      if (el) el.scrollTop = el.scrollHeight - prevHeight
      historyLoading.value = false
    })
  }, 280)
}

const onHistoryScroll = () => {
  const el = messagesRef.value
  if (el && el.scrollTop <= 24) loadOlderHistory()
}

/** 写入接待状态并插入会话内系统卡，避免重复插入同一坐席 */
const applyTransfer = (agent) => {
  const normalized = normalizeAgent(agent)
  if (!normalized) return
  transferredAgent.value = normalized
  const exists = archive.value.some(
    (m) => m.kind === 'agent-join' && m.agent?.agentNo === normalized.agentNo
  )
  if (exists) return
  appendMessage({
    kind: 'agent-join',
    isAI: true,
    content: `工号 ${normalized.agentNo} ${normalized.agentName} 已接入`,
    agent: normalized,
    time: new Date().toLocaleTimeString()
  })
  scrollToBottom()
}

/** 接口没带回坐席时，从在线坐席列表兜底一位，保证演示/降级仍能展示工号姓名 */
const fallbackOnlineAgent = async () => {
  try {
    const res = await listAgents()
    const list = res.data || []
    return normalizeAgent(
      list.find((a) => a.agentStatus === 1 && a.id !== 1)
      || list.find((a) => a.agentStatus === 1)
      || list[0]
    )
  } catch {
    return null
  }
}

const wsUrl = () => {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = localStorage.getItem('token') || ''
  const requestId = sessionStorage.getItem('lastRequestId') || ''
  return `${proto}//${location.host}/ws/${sessionId.value}?token=${encodeURIComponent(token)}&requestId=${encodeURIComponent(requestId)}`
}

const stopReplyTimer = () => {
  if (replyTimer) {
    clearTimeout(replyTimer)
    replyTimer = null
  }
}

const armReplyTimer = () => {
  stopReplyTimer()
  replyTimer = setTimeout(() => {
    if (isLoading.value) {
      isLoading.value = false
      appendMessage({
        content: '回复超时，请稍后重试或转人工客服。',
        isAI: true,
        time: new Date().toLocaleTimeString()
      })
    }
  }, REPLY_TIMEOUT_MS)
}

const connectWs = () => {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }
  try {
    socket = new WebSocket(wsUrl())
    socket.onopen = () => {}
    socket.onclose = () => {
      stopReplyTimer()
      if (isLoading.value) {
        isLoading.value = false
      }
      socket = null
      if (!closedByUser) {
        reconnectTimer = setTimeout(connectWs, 2000)
      }
    }
    socket.onerror = () => {
      stopReplyTimer()
      isLoading.value = false
    }
    socket.onmessage = (ev) => {
      let data = ev.data
      try {
        data = JSON.parse(ev.data)
      } catch {
        data = { type: 'ai', content: ev.data }
      }
      if (data.type === 'system') {
        return
      }
      if (data.type === 'error') {
        stopReplyTimer()
        isLoading.value = false
        appendMessage({ content: data.content || '连接异常', isAI: true, time: new Date().toLocaleTimeString() })
        return
      }
      if (data.type === 'ai') {
        stopReplyTimer()
        isLoading.value = false
        appendMessage({
          content: data.content,
          isAI: true,
          time: new Date().toLocaleTimeString(),
          citations: data.citations || []
        })
        // 服务端在意图为转人工时会改会话状态并带回 transferred / 工号姓名
        if (data.transferred) {
          const fromWs = normalizeAgent({
            agentNo: data.agentNo,
            agentName: data.agentName,
            agentId: data.agentId
          })
          if (fromWs) applyTransfer(fromWs)
          else fallbackOnlineAgent().then((agent) => applyTransfer(agent))
        }
        scrollToBottom()
      }
    }
  } catch {
    socket = null
  }
}

onMounted(async () => {
  try {
    await ensureSession({ sessionId: sessionId.value, sessionType: 1 })
  } catch {
    /* 会话服务不可用时仍可聊天 */
  }
  try {
    const res = await listOpenPacks()
    packs.value = (res.data || []).filter((p) => p.enabled !== 0)
  } catch {
    packs.value = []
  }
  if (!demoMode) {
    connectWs()
  }
  scrollToBottom()
})
onUnmounted(() => {
  closedByUser = true
  stopReplyTimer()
  if (streamAbort) streamAbort.abort()
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (socket) {
    socket.close()
    socket = null
  }
})

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return

  appendMessage({
    content: inputMessage.value,
    isAI: false,
    time: new Date().toLocaleTimeString()
  })
  const lastUser = inputMessage.value
  inputMessage.value = ''
  scrollToBottom()
  isLoading.value = true

  // 流式走 HTTP SSE，避免与 WebSocket 两路同时推同一条回复
  if (useStream.value) {
    await sendStream(lastUser)
    return
  }

  if (socket && socket.readyState === WebSocket.OPEN) {
    armReplyTimer()
    socket.send(JSON.stringify({
      msg: lastUser,
      sessionId: sessionId.value,
      packCode: packCode.value || undefined
    }))
    return
  }

  try {
    const response = await request.post('/ai/chat/send', {
      sessionId: sessionId.value,
      msg: lastUser,
      packCode: packCode.value || undefined
    })
    const payload = response.data
    const text = typeof payload === 'string' ? payload : (payload?.reply || '')
    appendMessage({
      content: text,
      isAI: true,
      time: new Date().toLocaleTimeString(),
      citations: payload?.citations || []
    })
    // HTTP 演示路径：用户说「转人工」或接口标记 transferred 时同样展示接待卡
    if (payload?.transferred) {
      const agent = normalizeAgent(payload)
      if (agent) applyTransfer(agent)
      else fallbackOnlineAgent().then((a) => applyTransfer(a))
    }
  } catch (error) {
    appendMessage({
      content: '抱歉，服务器暂时无法响应',
      isAI: true,
      time: new Date().toLocaleTimeString()
    })
  } finally {
    isLoading.value = false
  }
  scrollToBottom()
}

/** POST SSE：Spring 事件名为 token / done / interrupted / error */
const sendStream = async (msg) => {
  const aiMsg = { content: '', isAI: true, time: new Date().toLocaleTimeString(), citations: [] }
  appendMessage(aiMsg)
  streamAbort = new AbortController()
  try {
    const res = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + (localStorage.getItem('token') || '')
      },
      body: JSON.stringify({
        sessionId: sessionId.value,
        msg,
        packCode: packCode.value || undefined
      }),
      signal: streamAbort.signal
    })
    if (!res.ok || !res.body) {
      throw new Error('流式接口不可用')
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    let eventName = 'message'
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const blocks = buf.split('\n\n')
      buf = blocks.pop() || ''
      for (const block of blocks) {
        eventName = 'message'
        let data = ''
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5).trimStart()
        }
        if (eventName === 'token') aiMsg.content += data
        else if (eventName === 'done') aiMsg.content = data || aiMsg.content
        else if (eventName === 'interrupted') aiMsg.content = (data || aiMsg.content) + '\n（已打断）'
        else if (eventName === 'error') aiMsg.content = data || '流式输出失败'
        scrollToBottom()
      }
    }
  } catch (e) {
    if (e?.name !== 'AbortError') {
      aiMsg.content = aiMsg.content || '流式输出失败，请改用普通发送'
    }
  } finally {
    isLoading.value = false
    streamAbort = null
  }
}

const handleInterrupt = async () => {
  try {
    await interruptChat(sessionId.value)
    if (streamAbort) streamAbort.abort()
  } catch {
    /* 拦截器已提示 */
  }
}

const handleTransfer = async () => {
  transferring.value = true
  try {
    const res = await transferSession(sessionId.value)
    const data = res?.data
    const agent = normalizeAgent(typeof data === 'object' ? data : null) || await fallbackOnlineAgent()
    applyTransfer(agent)
  } catch {
    /* 拦截器已提示 */
  } finally {
    transferring.value = false
  }
}

const clearSession = () => {
  if (!confirm('确定要开始新对话吗？当前窗口会回到开场白。')) return
  sessionId.value = 'sess_' + Date.now()
  transferredAgent.value = null
  archive.value = [{
    id: nextMsgId(),
    content: '您好！我是智能客服，请问有什么可以帮助您的？',
    isAI: true,
    time: new Date().toLocaleTimeString()
  }]
  shown.value = 1
  closedByUser = true
  if (socket) {
    socket.close()
    socket = null
  }
  closedByUser = false
  ensureSession({ sessionId: sessionId.value, sessionType: 1 }).catch(() => {})
  connectWs()
}

const getRecentMessages = () => {
  const recent = archive.value.filter((m) => m.kind !== 'agent-join').slice(-5)
  return recent.map(m => (m.isAI ? '客服：' : '用户：') + m.content).join('\n')
}

/** 打开工单弹窗：预填最近对话，并拉取客户列表供选择 */
const openCreateOrder = async () => {
  orderForm.value = {
    orderType: '咨询',
    orderContent: getRecentMessages(),
    customerId: '',
    sessionId: sessionId.value,
    priority: '普通'
  }
  if (!customers.value.length) {
    try {
      const res = await listCustomers()
      customers.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    } catch {
      customers.value = []
    }
  }
  showCreateOrderDialog.value = true
}

const createOrder = async () => {
  if (!orderForm.value.orderType || !orderForm.value.orderContent?.trim()) {
    ElMessage.warning('请填写工单类型和内容')
    return
  }
  savingOrder.value = true
  try {
    const content = orderForm.value.priority === '紧急'
      ? `【紧急】${orderForm.value.orderContent}`
      : orderForm.value.orderContent
    await createWorkOrder({
      orderType: orderForm.value.orderType,
      content,
      sessionId: sessionId.value,
      customerId: orderForm.value.customerId ? Number(orderForm.value.customerId) : undefined
    })
    showCreateOrderDialog.value = false
    ElMessage.success('工单创建成功')
  } catch (error) {
    ElMessage.error(error?.msg || '创建工单失败')
  } finally {
    savingOrder.value = false
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid var(--cs-line, #e7edf5);
  border-radius: 14px;
  overflow: hidden;
  background: #fff;
  box-shadow: var(--cs-shadow, 0 8px 28px rgba(28, 43, 74, 0.06));
}
.chat-header {
  padding: 12px 18px;
  background: linear-gradient(90deg, #152238 0%, #1c2b4a 55%, #243656 100%);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.header-left {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.chat-header h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0.2px;
}
.header-sub {
  margin: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.62);
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
}
.hdr-sep {
  width: 1px;
  height: 18px;
  background: rgba(255, 255, 255, 0.18);
  margin: 0 2px;
  flex-shrink: 0;
}
.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #fff;
  white-space: nowrap;
}
.status-chip .pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #84adff;
  box-shadow: 0 0 0 3px rgba(132, 173, 255, 0.28);
}
.status-chip.human {
  background: rgba(18, 183, 106, 0.18);
  border-color: rgba(171, 239, 198, 0.45);
  color: #d1fadf;
}
.status-chip.human .pulse {
  background: #32d583;
  box-shadow: 0 0 0 3px rgba(50, 213, 131, 0.28);
}
.header-pack {
  width: 128px;
}
.header-actions :deep(.el-select .el-input__wrapper) {
  min-height: 32px;
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.18) inset !important;
}
.header-actions :deep(.el-select .el-input__inner) {
  color: #fff;
}
.header-actions :deep(.el-select .el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.5);
}
.header-actions :deep(.el-select .el-input__suffix) {
  color: rgba(255, 255, 255, 0.7);
}
.header-switch :deep(.el-switch__label) {
  color: rgba(255, 255, 255, 0.78);
}
.header-actions :deep(.hdr-btn.el-button) {
  height: 32px;
  padding: 0 13px;
  margin-left: 0;
  border-radius: 999px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #fff;
  gap: 4px;
}
.header-actions :deep(.hdr-btn.el-button:hover),
.header-actions :deep(.hdr-btn.el-button:focus) {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.32);
  color: #fff;
}
.header-actions :deep(.hdr-btn.primary) {
  background: #2f6bff;
  border-color: #2f6bff;
}
.header-actions :deep(.hdr-btn.primary:hover) {
  background: #4c82ff;
  border-color: #4c82ff;
}
.header-actions :deep(.hdr-btn.danger) {
  background: #f04438;
  border-color: #f04438;
}
.chat-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: #f4f7fb;
}
.history-tip {
  text-align: center;
  font-size: 12px;
  color: #98a2b3;
  padding: 4px 0 14px;
}
.message {
  display: flex;
  margin-bottom: 16px;
}
.user-message {
  justify-content: flex-end;
}
.content {
  padding: 12px 16px;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgba(28, 43, 74, 0.04);
  line-height: 1.55;
}
.user-message .content {
  background: #2f6bff;
  color: white;
  border-bottom-right-radius: 4px;
}
.ai-message .content {
  background: white;
  border: 1px solid #e7edf5;
  border-bottom-left-radius: 4px;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #e8eef6;
  color: #2a3f5f;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 8px;
  flex-shrink: 0;
}
.message-content {
  display: flex;
  flex-direction: column;
  max-width: 70%;
}
.time {
  font-size: 12px;
  color: #98a2b3;
  margin-top: 4px;
  padding: 0 8px;
}
.citations {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  padding: 0 8px;
}
.user-message .time {
  text-align: right;
}
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  color: #667085;
}
.chat-input {
  padding: 12px 16px 14px;
  background: #fff;
  border-top: 1px solid #e7edf5;
  display: flex;
  gap: 10px;
  align-items: center;
}
.chat-input :deep(.el-input) {
  flex: 1;
}
.chat-input :deep(.el-input__wrapper) {
  min-height: 42px;
  padding: 4px 14px;
  border-radius: 12px !important;
}
.chat-input .el-button {
  height: 42px;
  padding: 0 22px;
  border-radius: 12px;
  font-size: 15px;
}
.chat-container > .agent-banner {
  margin: 10px 16px 0;
}
.agent-join-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 8px 0 18px;
}
.agent-join-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #abefc6;
  border-radius: 14px;
  box-shadow: 0 8px 20px rgba(18, 183, 106, 0.08);
  min-width: 280px;
  max-width: 92%;
}
.join-meta strong {
  display: block;
  color: #054f31;
  font-size: 14px;
}
.join-meta span {
  font-size: 12px;
  color: #087443;
}
.agent-join-card .avatar-dot,
.agent-banner .avatar-dot {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #12b76a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .chat-header {
    padding: 12px;
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }
  .chat-header h2 {
    font-size: 16px;
  }
  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }
  .hdr-sep {
    display: none;
  }
  .header-actions :deep(.hdr-btn) {
    font-size: 12px;
    padding: 0 10px;
  }
  .chat-messages {
    padding: 10px;
  }
  .message-content {
    max-width: 82%;
  }
  .content {
    font-size: 14px;
  }
  .chat-input {
    padding: 10px;
    gap: 8px;
    padding-bottom: calc(10px + var(--safe-bottom));
  }
  .avatar {
    width: 32px;
    height: 32px;
  }
}
</style>
