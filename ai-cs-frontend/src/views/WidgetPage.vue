<template>
  <div class="widget">
    <header>
      <strong>在线客服</strong>
      <span>{{ scene || '咨询' }} · {{ visitorRef }}</span>
    </header>
    <div class="msgs" ref="box">
      <div v-for="(m, i) in messages" :key="i" :class="['row', m.mine ? 'mine' : 'bot']">
        <div>
          <div class="bubble">{{ m.text }}</div>
          <div v-if="m.citations?.length" class="cites">
            来源 {{ m.citations.map(c => '#' + c.faqId).join(' ') }}
          </div>
        </div>
      </div>
      <div v-if="quickActions.length" class="quick-actions">
        <button v-for="(a, i) in quickActions" :key="i" class="chip" @click="sendAction(a)">{{ a.label }}</button>
      </div>
    </div>
    <footer>
      <el-input v-model="input" maxlength="2000" placeholder="输入问题，可问物流/退款/咨询" @keyup.enter="send" />
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
    </footer>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { initWidget, sendChat } from '../api'

const route = useRoute()
const visitorRef = ref(route.query.visitorRef || 'guest_' + Date.now())
const scene = ref(route.query.scene || '')
const entityType = route.query.entityType || ''
const entityId = route.query.entityId || ''
const sessionId = ref('sess_widget_' + Date.now())
const accessToken = ref('')
const DEFAULT_GREETING = '您好，我是智能客服。可咨询问题；物流/退款会走已注册的连接器，而不是内核订单表。'
const messages = ref([{ mine: false, text: DEFAULT_GREETING }])
const quickActions = ref([])
const input = ref('')
const sending = ref(false)
const box = ref()

const entities = entityType && entityId ? [{ type: entityType, id: entityId }] : []

onMounted(async () => {
  try {
    const res = await initWidget({
      tenantCode: route.query.tenant || 'default',
      channel: 'web',
      visitorRef: visitorRef.value,
      scene: scene.value,
      entities
    })
    accessToken.value = res.data?.accessToken || ''
    // 场景化入口：用配置的开场白替换默认首条消息，并渲染快捷动作
    if (res.data?.greeting) {
      messages.value[0] = { mine: false, text: res.data.greeting }
    }
    if (Array.isArray(res.data?.quickActions) && res.data.quickActions.length) {
      quickActions.value = res.data.quickActions.filter((a) => a && a.label && a.send)
    }
    if (!accessToken.value) {
      messages.value.push({ mine: false, text: '未能获取访客令牌，请确认开放服务已启动后再发送。' })
    }
  } catch (e) {
    messages.value.push({ mine: false, text: '开放服务未启动时仍可聊天，但无法写入访客映射。' })
  }
})

const sendAction = (action) => {
  if (sending.value) return
  input.value = action.send
  send()
}

const send = async () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (!accessToken.value) {
    messages.value.push({ mine: false, text: '尚未完成访客初始化，无法发送。请刷新页面。' })
    return
  }
  input.value = ''
  messages.value.push({ mine: true, text })
  sending.value = true
  try {
    const res = await sendChat({
      sessionId: sessionId.value,
      msg: text,
      visitorRef: visitorRef.value,
      scene: scene.value,
      channel: 'web',
      tenantCode: route.query.tenant || 'default',
      entities
    }, accessToken.value ? { headers: { Authorization: 'Bearer ' + accessToken.value } } : undefined)
    const reply = res.data?.reply || '暂时无法回答'
    messages.value.push({ mine: false, text: reply, citations: res.data?.citations })
  } catch (e) {
    messages.value.push({ mine: false, text: '发送失败，请确认网关与 AI / 开放服务已启动。' })
  } finally {
    sending.value = false
    await nextTick()
    if (box.value) box.value.scrollTop = box.value.scrollHeight
  }
}
</script>

<style scoped>
.widget { height: 100%; display: flex; flex-direction: column; background: #f4f6fb; }
header { padding: 12px 16px; background: #1e2a44; color: #fff; display: flex; justify-content: space-between; gap: 8px; font-size: 14px; padding-top: calc(12px + var(--safe-top)); }
header span { color: #9aa8c3; font-size: 12px; }
.msgs { flex: 1; overflow: auto; padding: 16px; }
.row { display: flex; margin-bottom: 10px; }
.row.mine { justify-content: flex-end; }
.bubble { max-width: 80%; padding: 8px 12px; border-radius: 10px; background: #fff; line-height: 1.5; }
.mine .bubble { background: #3b82f6; color: #fff; }
.cites { font-size: 11px; color: #9aa8c3; margin-top: 4px; }
.quick-actions { display: flex; flex-wrap: wrap; gap: 8px; padding: 4px 0 8px; }
.chip {
  border: 1px solid #c7d4ee; background: #fff; color: #3b82f6; border-radius: 999px;
  padding: 6px 14px; font-size: 13px; cursor: pointer; transition: background .15s;
}
.chip:hover { background: #eef4ff; }
footer { display: flex; gap: 8px; padding: 12px; background: #fff; border-top: 1px solid #e8edf5; padding-bottom: calc(12px + var(--safe-bottom)); }
@media (max-width: 640px) {
  .msgs { padding: 12px; }
  .bubble { max-width: 86%; font-size: 14px; }
}
</style>
