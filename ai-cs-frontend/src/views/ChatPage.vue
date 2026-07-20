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
          <div class="time">{{ msg.time }}</div>
        </div>
      </div>
      <div v-if="isLoading" class="loading">
        <el-spinner size="small" />
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
import { ref, nextTick } from 'vue'
import { Bot, User } from '@element-plus/icons-vue'
import request from '../utils/request'

const messages = ref([
  { content: '您好！我是AI智能客服，请问有什么可以帮助您的？', isAI: true, time: new Date().toLocaleTimeString() }
])
const inputMessage = ref('')
const messagesRef = ref(null)
const isLoading = ref(false)
const showCreateOrderDialog = ref(false)
const sessionId = ref('session_' + Date.now())

const orderForm = ref({
  orderType: '',
  content: '',
  customerId: ''
})

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return

  messages.value.push({
    content: inputMessage.value,
    isAI: false,
    time: new Date().toLocaleTimeString()
  })
  inputMessage.value = ''

  nextTick(() => {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })

  isLoading.value = true

  try {
    const response = await request.post('/ai/chat/send', {
      sessionId: sessionId.value,
      msg: messages.value[messages.value.length - 1].content
    })

    messages.value.push({
      content: response.data,
      isAI: true,
      time: new Date().toLocaleTimeString()
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
  sessionId.value = 'session_' + Date.now()
  messages.value = [
    { content: '您好！我是AI智能客服，请问有什么可以帮助您的？', isAI: true, time: new Date().toLocaleTimeString() }
  ]
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
    await request.post('/workorder/create', {
      ...orderForm.value,
      sessionId: sessionId.value
    })
    showCreateOrderDialog.value = false
    orderForm.value = { orderType: '', content: '', customerId: '' }
    alert('工单创建成功')
  } catch (error) {
    console.error('创建工单失败:', error)
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
@media (max-width: 768px) {
  .chat-header {
    padding: 12px;
    flex-direction: column;
    gap: 8px;
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
    max-width: 85%;
    font-size: 14px;
  }
  .chat-input {
    padding: 10px;
    gap: 8px;
  }
  .avatar {
    width: 32px;
    height: 32px;
  }
}
</style>