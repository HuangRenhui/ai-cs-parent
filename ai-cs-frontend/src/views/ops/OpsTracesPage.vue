<template>
  <div class="page-card">
    <p class="hint">{{ page.hint }}</p>
    <div class="page-toolbar">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="链路号">
          <el-input v-model="query.traceId" clearable placeholder="链路号" />
        </el-form-item>
        <el-form-item label="会话号">
          <el-input v-model="query.sessionId" clearable placeholder="会话号" />
        </el-form-item>
        <el-form-item>
          <FilterActions @search="search" @reset="resetSearch" />
        </el-form-item>
      </el-form>
    </div>
    <el-table :data="page.list" stripe empty-text="暂无链路数据。日志带链路号后会在此聚合。" table-layout="fixed" max-height="680">
      <el-table-column prop="traceId" label="链路号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="sessionId" label="会话号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="spanCount" label="方法数" min-width="80" />
      <el-table-column label="总耗时" min-width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="durationTone(row.durationMs)" effect="plain">{{ durationLabel(row.durationMs) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始" min-width="168" />
      <el-table-column label="操作" min-width="160">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="tree" @click="open(row)" />
            <TblAct v-if="rowRequestId(row)" act="log" @click="$router.push({ path: '/ops/logs', query: { requestId: rowRequestId(row) } })" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="query.page" v-model:size="query.size" :total="page.total || 0" @change="searchKeepPage" />
    <OpsCallTreeDialog
      v-model="spanVisible"
      :title="spanTitle"
      :roots="treeRoots"
      :duration-ms="current.durationMs"
      :share-text="shareText"
      empty-text="暂无调用步骤"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getOpsTraces } from '../../api'
import OpsCallTreeDialog from '../../components/OpsCallTreeDialog.vue'
import TablePager from '../../components/TablePager.vue'
import { durationLabel, durationTone, formatShareText, spansToRoots } from '../../utils/opsCallTree'
import { displayText, SERVICE_NAME_TEXT } from '../../utils/selectOptions'

const query = reactive({ traceId: '', sessionId: '', page: 1, size: 10 })
const page = ref({ list: [], total: 0, hint: '' })
const spanVisible = ref(false)
const current = ref({ spans: [] })

const spanTitle = computed(() => {
  const id = current.value?.traceId
  const n = current.value?.spanCount || treeRoots.value.length
  return id ? `调用树 · ${id}（${n} 步）` : '调用树（按日志聚合）'
})

const serviceText = (v) => displayText(SERVICE_NAME_TEXT, v, v)

/** 后端树或扁平 Span 都收成服务根节点 */
const treeRoots = computed(() => spansToRoots(current.value?.spans || []))

const shareText = computed(() => {
  if (current.value?.shareText) return current.value.shareText
  return formatShareText({
    title: current.value?.traceId ? `调用链  链路号 ${current.value.traceId}` : '调用链',
    extra: [
      current.value?.sessionId ? `会话号 ${current.value.sessionId}` : ''
    ],
    durationMs: current.value?.durationMs,
    roots: treeRoots.value,
    serviceText
  })
})

/** 从树根或扁平 Span 上取请求号，跳转日志页 */
const rowRequestId = (row) => {
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if (n.requestId) return n.requestId
      const hit = walk(n.children)
      if (hit) return hit
    }
    return ''
  }
  return walk(row?.spans) || ''
}

const search = async () => {
  query.page = 1
  const res = await getOpsTraces(query)
  page.value = res.data || { list: [], total: 0 }
}

/** 清空链路号、会话号 */
const resetSearch = async () => {
  query.traceId = ''
  query.sessionId = ''
  await search()
}

/** 翻页/改条数时保留当前页码再查 */
const searchKeepPage = async () => {
  const res = await getOpsTraces(query)
  page.value = res.data || { list: [], total: 0 }
}

/** 调用树用弹窗展示；层级再深也不撑出视口 */
const open = (row) => {
  current.value = row
  spanVisible.value = true
}

onMounted(search)
</script>

<style scoped>
.hint { color: #6b7280; line-height: 1.6; font-size: 13px; margin-bottom: 12px; }
</style>
