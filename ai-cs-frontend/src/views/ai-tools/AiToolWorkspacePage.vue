<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div>
        <el-button text type="primary" @click="$router.push('/ai/tools')">返回工具箱</el-button>
        <h3>{{ tool ? tool.name : '工具' }}</h3>
        <p class="page-desc">{{ tool ? tool.hint : '' }}</p>
      </div>
      <el-tag v-if="tool && !tool.builtin" type="success" effect="plain">自定义工具</el-tag>
      <el-tag v-else type="warning">后端待接通</el-tag>
    </div>

    <!-- 绑定开放接口的自定义工具走通用 HTTP 面板 -->
    <HttpToolPanel v-if="tool && tool.execType === 'http'" :tool="tool" />
    <component :is="panel" v-else-if="panel" />
    <el-empty v-else-if="tool" description="该工具没有可用的执行面板" />
  </div>
</template>

<script setup>
/**
 * AI 工具工作台：按路由编码挂上对应功能面板。
 * 内置工具用 catalog + 固定面板；自定义工具按执行方式来（内置面板复用 / 开放接口面板）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AI_TOOLS } from './catalog'
import { listAiTools } from '../../api'
import EntryPanel from './panels/EntryPanel.vue'
import ImportPanel from './panels/ImportPanel.vue'
import BatchDeletePanel from './panels/BatchDeletePanel.vue'
import FaqDraftPanel from './panels/FaqDraftPanel.vue'
import TicketFillPanel from './panels/TicketFillPanel.vue'
import HttpToolPanel from './panels/HttpToolPanel.vue'

const PANELS = {
  entry: EntryPanel,
  import: ImportPanel,
  'batch-delete': BatchDeletePanel,
  'faq-draft': FaqDraftPanel,
  'ticket-fill': TicketFillPanel
}

const route = useRoute()
const router = useRouter()
const customTools = ref([])
const loaded = ref(false)

/** 内置工具优先，其次查自定义工具 */
const tool = computed(() => {
  const code = route.params.code
  const builtin = AI_TOOLS.find((t) => t.code === code)
  if (builtin) return { ...builtin, builtin: true, execType: 'builtin', panelCode: builtin.code }
  return customTools.value.find((t) => t.code === code) || null
})

/** 内置能力复用的面板；HTTP 类型的走 HttpToolPanel，这里返回 null */
const panel = computed(() => {
  const t = tool.value
  if (!t || t.execType === 'http') return null
  return PANELS[t.panelCode || t.code] || null
})

/** 标题与失效跳转：自定义工具要等列表拉完才能判断 */
const syncTitle = () => {
  const t = tool.value
  if (t) {
    document.title = `${t.name} · 智能客服`
    return
  }
  if (loaded.value) {
    ElMessage.warning('工具不存在或已删除')
    router.replace('/ai/tools')
  }
}

watch(() => route.params.code, syncTitle, { immediate: true })

onMounted(async () => {
  try {
    const res = await listAiTools()
    const data = res.data
    customTools.value = Array.isArray(data) ? data : (data?.records || [])
  } catch {
    customTools.value = []
  } finally {
    loaded.value = true
    syncTitle()
  }
})
</script>
