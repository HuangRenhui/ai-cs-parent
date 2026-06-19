<template>
  <div class="chat-container">
    <div class="chat-header">
      <h2>AI智能客服</h2>
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
        <div class="content">{{ msg.content }}</div>
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
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { Bot, User } from '@element-plus/icons-vue'
import request from '../utils/request'

const messages = ref([
  { content: '您好！我是AI智能客服，请问有什么可以帮助您的？', isAI: true }
])
const inputMessage = ref('')
const messagesRef = ref(null)

const sendMessage = async () => {
  if (!inputMessage.value.trim()) return

  messages.value.push({ content: inputMessage.value, isAI: false })
  inputMessage.value = ''

  nextTick(() => {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })

  try {
    const response = await request.post('/ai/chat/send', {
      sessionId: 'session_' + Date.now(),
      msg: messages.value[messages.value.length - 1].content
    })

    messages.value.push({ content: response.data, isAI: true })
  } catch (error) {
    messages.value.push({ content: '抱歉，服务器暂时无法响应', isAI: true })
  }

  nextTick(() => {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
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
}
.chat-header h2 {
  margin: 0;
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
}
.content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
</style>