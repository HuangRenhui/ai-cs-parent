<template>
  <div class="chat-container">
    <div class="chat-header">
      <div class="header-left">
        <h2>AI智能客服</h2>
        <span class="session-info">会话ID: {{ sessionId }}</span>
      </div>
      <div class="header-actions">
        <el-button size="small" @click="showCreateOrderDialog = true">创建工单</el-button>
        <el-button size="small" @click="clearSession">清空会话</el-button>
      </div>
    </div>
    <div class="chat-messages" ref="messagesRef">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['message', msg.isAI ? 'ai-message' : 'user-message']"
      >
        <div class="avatar">
          <el-icon v-if="msg.isAI"><Bot /></el-icon>
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
      <div v-if="isLoading" class="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>AI正在思考...</span>
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

    <!-- 创建工单弹窗 -->
    <el-dialog title="创建工单" v-model="showCreateOrderDialog">
      <el-form :model="orderForm" label-width="80px">
        <el-form-item label="工单类型">
          <el-select v-model="orderForm.orderType" placeholder="请选择">
            <el-option label="咨询" value="咨询" />
            <el-option label="投诉" value="投诉" />
            <el-option label="建议" value="建议" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单内容">
          <el-textarea v-model="orderForm.content" rows="4" :placeholder="`当前对话内容:\n${getRecentMessages()}`" />
        </el-form-item>
        <el-form-item label="客户ID">
          <el-input v-model="orderForm.customerId" type="number" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateOrderDialog = false">取消</el-button>
        <el-button type="primary" @click="createOrder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { Bot, User, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { createWorkOrder, ensureSession } from '../api'

const messages = ref([
  { content: '您好！我是AI智能客服，请问有什么可以帮助您的？', isAI: true, time: new Date().toLocaleTimeString() }
])
const inputMessage = ref('')
const messagesRef = ref(null)
const isLoading = ref(false)
const showCreateOrderDialog = ref(false)
const sessionId = ref('sess_' + Date.now())
let socket = null
let closedByUser = false
let reconnectTimer = null
let replyTimer = null
const REPLY_TIMEOUT_MS = 35000

const orderForm = ref({
  orderType: '',
  content: '',
  customerId: ''
})

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
      messages.value.push({
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
        messages.value.push({ content: data.content || '连接异常', isAI: true, time: new Date().toLocaleTimeString() })
        return
      }
      if (data.type === 'ai') {
        stopReplyTimer()
        isLoading.value = false
        messages.value.push({
          content: data.content,
          isAI: true,
          time: new Date().toLocaleTimeString(),
          citations: data.citations || []
        })
        nextTick(() => {
          if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
        })
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
  connectWs()
})
onUnmounted(() => {
  closedByUser = true
  stopReplyTimer()
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (socket) {
    socket.close()
    socket = null
  }
})

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return

  messages.value.push({
    content: inputMessage.value,
    isAI: false,
    time: new Date().toLocaleTimeString()
  })
  const lastUser = inputMessage.value
  inputMessage.value = ''

  nextTick(() => {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })

  isLoading.value = true

  if (socket && socket.readyState === WebSocket.OPEN) {
    armReplyTimer()
    socket.send(JSON.stringify({ msg: lastUser, sessionId: sessionId.value }))
    return
  }

  try {
    const response = await request.post('/ai/chat/send', {
      sessionId: sessionId.value,
      msg: lastUser
    })
    const payload = response.data
    const text = typeof payload === 'string' ? payload : (payload?.reply || '')
    messages.value.push({
      content: text,
      isAI: true,
      time: new Date().toLocaleTimeString(),
      citations: payload?.citations || []
    })
  } catch (error) {
    messages.value.push({
      content: '抱歉，服务器暂时无法响应',
      isAI: true,
      time: new Date().toLocaleTimeString()
    })
  } finally {
    isLoading.value = false
  }

  nextTick(() => {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

const clearSession = () => {
  if (!confirm('确定要清空当前会话吗？')) return
  sessionId.value = 'sess_' + Date.now()
  messages.value = [
    { content: '您好！我是AI智能客服，请问有什么可以帮助您的？', isAI: true, time: new Date().toLocaleTimeString() }
  ]
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
  const recent = messages.value.slice(-5)
  return recent.map(m => (m.isAI ? 'AI: ' : '用户: ') + m.content).join('\n')
}

const createOrder = async () => {
  if (!orderForm.value.orderType || !orderForm.value.content) {
    alert('请填写工单类型和内容')
    return
  }
  try {
    await createWorkOrder({
      ...orderForm.value,
      sessionId: sessionId.value
    })
    showCreateOrderDialog.value = false
    orderForm.value = { orderType: '', content: '', customerId: '' }
    ElMessage.success('工单创建成功')
  } catch (error) {
    ElMessage.error(error?.msg || '创建工单失败')
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
}
.chat-header {
  padding: 16px;
  background-color: #2a3f5f;
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  flex-direction: column;
}
.chat-header h2 {
  margin: 0;
  font-size: 18px;
}
.session-info {
  font-size: 12px;
  opacity: 0.8;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.chat-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background-color: #f5f5f5;
}
.message {
  display: flex;
  margin-bottom: 16px;
}
.user-message {
  justify-content: flex-end;
}
.user-message .content {
  background-color: #409eff;
  color: white;
}
.ai-message .content {
  background-color: white;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 8px;
  flex-shrink: 0;
}
.message-content {
  display: flex;
  flex-direction: column;
}
.content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.time {
  font-size: 12px;
  color: #999;
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
  color: #666;
}
.chat-input {
  padding: 16px;
  background-color: white;
  border-top: 1px solid #e0e0e0;
  display: flex;
  gap: 12px;
}
.chat-input input {
  flex: 1;
}

/* 移动端适配 */
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
    justify-content: flex-end;
  }
  .header-actions .el-button {
    font-size: 12px;
    padding: 5px 10px;
  }
  .chat-messages {
    padding: 10px;
  }
  .content {
    max-width: 82%;
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