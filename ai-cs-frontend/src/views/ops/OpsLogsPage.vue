<template>
  <div class="page-card">
    <p class="hint">{{ page.hint }}</p>
    <div class="page-toolbar">
      <el-form :inline="true" :model="query" @submit.prevent="search">
        <el-form-item label="请求号">
          <el-input v-model="query.requestId" clearable placeholder="请求号" />
        </el-form-item>
        <el-form-item label="会话号">
          <el-input v-model="query.sessionId" clearable placeholder="会话号" />
        </el-form-item>
        <el-form-item label="链路号">
          <el-input v-model="query.traceId" clearable placeholder="链路号" />
        </el-form-item>
        <el-form-item label="服务">
          <el-input v-model="query.service" clearable placeholder="如网关、坐席服务" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" clearable placeholder="全部" style="width: 110px">
            <el-option v-for="lv in LOG_LEVEL_OPTIONS" :key="lv.value" :label="lv.label" :value="lv.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="类名 / 方法 / 已脱敏文本" />
        </el-form-item>
        <el-form-item>
          <FilterActions @search="search" @reset="resetSearch" />
        </el-form-item>
      </el-form>
    </div>
    <el-table :data="page.list" stripe empty-text="暂无日志。可按请求号精确查询。" table-layout="fixed" max-height="680">
      <el-table-column prop="timestamp" label="时间" min-width="168" />
      <el-table-column label="级别" min-width="72">
        <template #default="{ row }">{{ displayText(LOG_LEVEL_TEXT, row.level) }}</template>
      </el-table-column>
      <el-table-column label="服务" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ displayText(SERVICE_NAME_TEXT, row.service) }}</template>
      </el-table-column>
      <el-table-column label="类 / 方法" min-width="180">
        <template #default="{ row }">
          <CellText
            title="类 / 方法"
            :text="(simpleName(row.className || row.logger) || '') + (row.methodName ? '.' + row.methodName : '')"
          />
        </template>
      </el-table-column>
      <el-table-column label="耗时" min-width="96">
        <template #default="{ row }">
          <el-tag v-if="row.durationMs != null" size="small" :type="durationTone(row.durationMs)" effect="plain">
            {{ durationLabel(row.durationMs) }}
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="requestId" label="请求号" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button v-if="row.requestId" link type="primary" @click="openRequest(row.requestId)">{{ row.requestId }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="160">
        <template #default="{ row }">
          <CellText title="日志内容" :text="row.message" />
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="query.page" v-model:size="query.size" :total="page.total || 0" @change="load" />
    <OpsCallTreeDialog
      v-model="treeVisible"
      :title="treeTitle"
      :roots="tree.roots"
      :duration-ms="tree.durationMs"
      :share-text="tree.shareText"
      empty-text="该请求号暂无调用步骤"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getOpsLogs, getOpsLogsByRequestId } from '../../api'
import OpsCallTreeDialog from '../../components/OpsCallTreeDialog.vue'
import TablePager from '../../components/TablePager.vue'
import { durationLabel, durationTone, logsToTree, simpleName } from '../../utils/opsCallTree'
import { displayText, LOG_LEVEL_OPTIONS, LOG_LEVEL_TEXT, SERVICE_NAME_TEXT } from '../../utils/selectOptions'

const route = useRoute()

const query = reactive({
  requestId: '',
  sessionId: '',
  traceId: '',
  service: '',
  level: '',
  keyword: '',
  page: 1,
  size: 10
})
const page = ref({ list: [], total: 0, hint: '' })
const treeVisible = ref(false)
const timeline = ref([])
const timelineRequestId = ref('')

const treeTitle = computed(() => {
  const id = timelineRequestId.value
  return id ? `请求调用树 · ${id}` : '请求调用树'
})

/** 同一请求号的日志聚成服务/类/方法树，供弹窗展示与复制分享 */
const tree = computed(() => logsToTree(timeline.value, {
  requestId: timelineRequestId.value,
  traceId: timeline.value.find((x) => x.traceId)?.traceId,
  sessionId: timeline.value.find((x) => x.sessionId)?.sessionId
}, (v) => displayText(SERVICE_NAME_TEXT, v, v)))

const search = async () => {
  query.page = 1
  await load()
}

/** 清空请求号、会话号等筛选条件 */
const resetSearch = async () => {
  query.requestId = ''
  query.sessionId = ''
  query.traceId = ''
  query.service = ''
  query.level = ''
  query.keyword = ''
  await search()
}

const load = async () => {
  const res = await getOpsLogs(query)
  page.value = res.data || { list: [], total: 0 }
}

/** 点请求号：弹窗展示层级调用树，可复制纯文本分享 */
const openRequest = async (requestId) => {
  timelineRequestId.value = requestId
  const res = await getOpsLogsByRequestId(requestId)
  timeline.value = res.data || []
  treeVisible.value = true
}

onMounted(() => {
  if (route.query.requestId) {
    query.requestId = String(route.query.requestId)
  }
  load()
})
</script>

<style scoped>
.hint { color: #6b7280; line-height: 1.6; font-size: 13px; margin-bottom: 12px; }
</style>
