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
          <span>{{ statusText }}</span>
        </div>
        <!-- 已转人工（含排队态）后不再提供转人工入口，避免重复提交 -->
        <el-button
          v-if="!transferredAgent && !transferQueued"
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

    <!-- 排队态：已转人工但暂无在线坐席，不展示任何坐席信息，避免谎称已接入 -->
    <div v-else-if="transferQueued" class="agent-banner queued">
      <div class="avatar-dot queued"><el-icon><Clock /></el-icon></div>
      <div>
        <strong>正在等待人工坐席接入</strong>
        <span>已为您转接人工客服，当前坐席繁忙，请稍候</span>
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
            <!-- 附件区：图片直接展示，其他类型显示文件卡片 -->
            <div v-if="msg.attachments && msg.attachments.length" class="msg-attachments">
              <template v-for="(att, ai) in msg.attachments" :key="att.fileId || ai">
                <el-image
                  v-if="att.category === 'image'"
                  class="att-image"
                  :src="att.url"
                  :preview-src-list="imagePreviewList(msg.attachments)"
                  :initial-index="imageIndex(msg.attachments, att)"
                  fit="cover"
                  preview-teleported
                />
                <a
                  v-else
                  class="att-file"
                  :href="att.url"
                  target="_blank"
                  rel="noopener"
                >
                  <el-icon class="att-file-icon"><Document /></el-icon>
                  <span class="att-file-name">{{ att.fileName }}</span>
                  <span class="att-file-size">{{ formatSize(att.fileSize) }}</span>
                </a>
              </template>
            </div>
            <div v-if="msg.content" class="content">{{ msg.content }}</div>
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
    <!-- 待发送附件预览条 -->
    <div v-if="pendingAttachments.length" class="pending-bar">
      <div v-for="(att, i) in pendingAttachments" :key="att.fileId || i" class="pending-item">
        <img v-if="att.category === 'image'" class="pending-thumb" :src="att.url" :alt="att.fileName" />
        <div v-else class="pending-file">
          <el-icon><Document /></el-icon>
          <span class="pending-name">{{ att.fileName }}</span>
        </div>
        <button class="pending-remove" title="移除" @click="removePending(i)">×</button>
      </div>
      <div v-if="uploading" class="pending-uploading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>上传中...</span>
      </div>
    </div>

    <div
      class="chat-input"
      @paste="onPaste"
      @dragover.prevent="onDragOver"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
      :class="{ dragging }"
    >
      <!-- 隐藏的文件选择器：支持多选与全类型 -->
      <input
        ref="fileInputRef"
        class="file-input-hidden"
        type="file"
        multiple
        @change="onFilePicked"
      />
      <el-tooltip content="上传附件（支持图片、文档等，也可直接粘贴或拖入）" placement="top">
        <el-button class="attach-btn" :disabled="uploading" @click="pickFile">
          <el-icon><Paperclip /></el-icon>
        </el-button>
      </el-tooltip>
      <el-input
        v-model="inputMessage"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        resize="none"
        placeholder="请输入消息...（可粘贴图片或文本，也可拖拽文件到此处）"
        @keydown.enter.exact.prevent="sendMessage"
      />
      <el-button type="primary" :disabled="uploading" @click="sendMessage">发送</el-button>
      <div v-if="dragging" class="drop-mask">松开即可上传附件</div>
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
import { ChatDotRound, Headset, Reading, User, Loading, Document, Paperclip, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { createWorkOrder, ensureSession, interruptChat, listCustomers, listOpenPacks, transferSession } from '../api'
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
/**
 * 是否处于「已转人工但无在线坐席」的排队态。
 * <p>后端在无在线坐席时返回 transferred=true 且 agentId 为空，此时会话已转为人工接待，
 * 只是尚未指派具体坐席。<b>不能像以前那样借一位在线坐席展示</b>——那会让用户以为
 * 已有人接待，与「排队中」的真实状态相反。</p>
 */
const transferQueued = ref(false)
const customers = ref([])
/** 聊天页工单锁定当前会话，下拉只展示这一条 */
const orderSessions = computed(() => [{ sessionId: sessionId.value, sessionStatus: 1 }])
let socket = null
let closedByUser = false
let reconnectTimer = null
let replyTimer = null
let streamAbort = null
const REPLY_TIMEOUT_MS = 35000

/** 顶栏接待状态文案：已接入坐席 / 排队中 / 智能接待 */
const statusText = computed(() => {
  if (transferredAgent.value) {
    return `工号 ${transferredAgent.value.agentNo} ${transferredAgent.value.agentName}`
  }
  if (transferQueued.value) return '等待人工接入'
  return '智能接待'
})

const orderForm = ref({
  orderType: '咨询',
  orderContent: '',
  customerId: '',
  sessionId: '',
  priority: '普通'
})

/* ===== 对话附件：上传 / 粘贴 / 拖拽 ===== */
/** 上传接口路径（网关会 RewritePath 到 base-service 的 /file/chat-attachment） */
const ATTACHMENT_UPLOAD_URL = '/file/chat-attachment'
/** 待发送的附件列表（发送后清空） */
const pendingAttachments = ref([])
/** 附件上传中标记，上传期间禁止发送 */
const uploading = ref(false)
/** 拖拽悬停标记，用于展示落区遮罩 */
const dragging = ref(false)
const fileInputRef = ref(null)

/** 触发隐藏的文件选择器 */
const pickFile = () => {
  if (fileInputRef.value) fileInputRef.value.click()
}

/** 读取文件选择器的选择结果 */
const onFilePicked = (e) => {
  const files = Array.from(e.target?.files || [])
  if (files.length) uploadFiles(files)
  // 重置，保证同一文件可重复选择
  if (e.target) e.target.value = ''
}

/**
 * 上传一批文件到对话附件接口。
 * <p>并行上传，逐个追加到待发送列表；失败时提示但不阻断其余文件。</p>
 */
const uploadFiles = async (files) => {
  if (!files.length) return
  uploading.value = true
  try {
    const results = await Promise.all(
      files.map((file) => {
        const form = new FormData()
        form.append('file', file)
        return request.post(ATTACHMENT_UPLOAD_URL, form).then((res) => res.data)
      })
    )
    pendingAttachments.value = pendingAttachments.value.concat(results.filter(Boolean))
  } catch (e) {
    ElMessage.error('附件上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

/** 移除一个待发送附件 */
const removePending = (index) => {
  pendingAttachments.value.splice(index, 1)
}

/**
 * 粘贴处理：从剪贴板取图片，同时保留文本粘贴。
 * <p>ClipboardEvent 的 files 属性在部分浏览器不可用，故回退到 items 遍历。</p>
 */
const onPaste = (e) => {
  const items = e.clipboardData?.items
  if (!items) return
  const images = []
  for (const item of items) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) images.push(file)
    }
  }
  if (images.length) {
    // 仅在有图片时阻止默认，纯文本粘贴保持原生行为
    e.preventDefault()
    uploadFiles(images)
  }
}

const onDragOver = () => {
  dragging.value = true
}

/** 拖拽放入：取文件列表上传 */
const onDrop = (e) => {
  dragging.value = false
  const files = Array.from(e.dataTransfer?.files || [])
  if (files.length) uploadFiles(files)
}

/** 组装图片附件的预览列表，供 el-image 放大浏览 */
const imagePreviewList = (attachments) =>
  (attachments || []).filter((a) => a.category === 'image').map((a) => a.url)

/** 计算某张图片在预览列表中的下标 */
const imageIndex = (attachments, target) => {
  const list = (attachments || []).filter((a) => a.category === 'image')
  return Math.max(0, list.findIndex((a) => a.fileId === target.fileId))
}

/** 字节数转可读大小 */
const formatSize = (bytes) => {
  const n = Number(bytes)
  if (!n || n < 0) return ''
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

/** 附件随消息一起发出的精简结构（去掉本地预览用的冗余字段） */
const payloadAttachments = (list) =>
  (list || []).map((a) => ({
    fileId: a.fileId,
    url: a.url,
    fileName: a.fileName,
    category: a.category,
    contentType: a.contentType,
    fileSize: a.fileSize
  }))

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
  // 已指派到具体坐席，退出排队态
  transferQueued.value = false
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

/**
 * 进入排队态：已转人工但当前没有在线坐席可指派。
 * <p>后端语义是「会话已改人工接待，等班长派单或坐席上线」，因此这里<b>只标记排队</b>，
 * 不展示任何坐席工号姓名——借一位在线坐席展示会构成「谎称已接入」。
 * 用户主动问「转人工」时，这个状态也用于确认按钮已被消费。</p>
 */
const applyTransferQueued = () => {
  transferQueued.value = true
  transferredAgent.value = null
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
        // 服务端在意图为转人工时会改会话状态并带回 transferred / 工号姓名。
        // 无坐席字段时表示排队中，同样不做「借坐席」兜底
        if (data.transferred) {
          const fromWs = normalizeAgent({
            agentNo: data.agentNo,
            agentName: data.agentName,
            agentId: data.agentId
          })
          if (fromWs) applyTransfer(fromWs)
          else applyTransferQueued()
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
  // 允许「纯附件、无文本」发送；但上传中或正在回复时不允许发送
  const hasText = !!inputMessage.value.trim()
  const hasAttachment = pendingAttachments.value.length > 0
  if ((!hasText && !hasAttachment) || isLoading.value || uploading.value) return

  const lastUser = inputMessage.value
  const lastAttachments = payloadAttachments(pendingAttachments.value)

  appendMessage({
    content: lastUser,
    isAI: false,
    attachments: pendingAttachments.value.slice(),
    time: new Date().toLocaleTimeString()
  })
  inputMessage.value = ''
  pendingAttachments.value = []
  scrollToBottom()
  isLoading.value = true

  // 流式走 HTTP SSE，避免与 WebSocket 两路同时推同一条回复
  if (useStream.value) {
    await sendStream(lastUser, lastAttachments)
    return
  }

  if (socket && socket.readyState === WebSocket.OPEN) {
    armReplyTimer()
    socket.send(JSON.stringify({
      msg: lastUser,
      sessionId: sessionId.value,
      packCode: packCode.value || undefined,
      attachments: lastAttachments
    }))
    return
  }

  try {
    const response = await request.post('/ai/chat/send', {
      sessionId: sessionId.value,
      msg: lastUser,
      packCode: packCode.value || undefined,
      attachments: lastAttachments
    })
    const payload = response.data
    const text = typeof payload === 'string' ? payload : (payload?.reply || '')
    appendMessage({
      content: text,
      isAI: true,
      time: new Date().toLocaleTimeString(),
      citations: payload?.citations || []
    })
    // 转人工：按后端语义分「已指派坐席」与「排队中」两种情况渲染。
    // 注意不能再用 fallbackOnlineAgent 兜底——后端明确返回 agentId 为空时，
    // 表示暂无在线坐席，借一位坐席展示会谎称已接入
    if (payload?.transferred) {
      const agent = normalizeAgent(payload)
      if (agent) applyTransfer(agent)
      else applyTransferQueued()
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
const sendStream = async (msg, attachments) => {
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
        packCode: packCode.value || undefined,
        attachments: attachments || []
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
    // 后端在无在线坐席时返回 agentId 为空，此时进入排队态而非借一位坐席展示
    const agent = normalizeAgent(typeof data === 'object' ? data : null)
    if (agent) applyTransfer(agent)
    else applyTransferQueued()
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
  transferQueued.value = false
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
  position: relative;
  padding: 12px 16px 14px;
  background: #fff;
  border-top: 1px solid #e7edf5;
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.chat-input.dragging {
  background: #f4f8ff;
  outline: 2px dashed #2f6bff;
  outline-offset: -6px;
}
.chat-input :deep(.el-textarea) {
  flex: 1;
}
.chat-input :deep(.el-textarea__inner) {
  min-height: 42px !important;
  padding: 10px 14px;
  border-radius: 12px !important;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  font-size: 14px;
  line-height: 1.5;
}
.chat-input .el-button {
  height: 42px;
  padding: 0 22px;
  border-radius: 12px;
  font-size: 15px;
}
/* 附件按钮保持方形，与发送按钮区分 */
.chat-input .attach-btn {
  padding: 0;
  width: 42px;
  flex: none;
  font-size: 18px;
}
.file-input-hidden {
  display: none;
}
/* 拖拽悬停遮罩 */
.drop-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(47, 107, 255, 0.06);
  color: #2f6bff;
  font-size: 14px;
  font-weight: 600;
  border-radius: 12px;
  pointer-events: none;
}

/* 待发送附件预览条 */
.pending-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 16px 0;
  background: #fff;
  align-items: center;
}
.pending-item {
  position: relative;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  overflow: visible;
  background: #fbfcfe;
}
.pending-thumb {
  display: block;
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 9px;
}
.pending-file {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 180px;
  height: 56px;
  padding: 0 12px;
  font-size: 12.5px;
  color: #374151;
}
.pending-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pending-remove {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 18px;
  height: 18px;
  line-height: 15px;
  border: none;
  border-radius: 50%;
  background: #f04438;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}
.pending-uploading {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: #6b7280;
}

/* 消息内附件 */
.msg-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}
.att-image {
  width: 160px;
  max-width: 100%;
  height: 160px;
  border-radius: 10px;
  cursor: zoom-in;
  background: #f4f6fb;
}
.message.user-message .att-image {
  margin-left: auto;
}
.att-file {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 240px;
  padding: 10px 14px;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  background: #fbfcfe;
  color: #374151;
  text-decoration: none;
  font-size: 13px;
}
.att-file:hover {
  border-color: #2f6bff;
  color: #2f6bff;
}
.att-file-icon {
  flex: none;
  font-size: 16px;
}
.att-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-file-size {
  flex: none;
  color: #98a2b3;
  font-size: 12px;
}
.chat-container > .agent-banner {
  margin: 10px 16px 0;
}
/* 排队态接待条：用琥珀色与「已接入」的绿色区分，避免用户误以为已有坐席服务 */
.agent-banner.queued {
  background: #fffaeb;
  border-color: #fedf89;
}
.agent-banner.queued strong {
  color: #b54708;
}
.agent-banner.queued span {
  color: #b54708;
  opacity: .85;
}
.agent-banner .avatar-dot.queued {
  background: #f79009;
  font-size: 18px;
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
