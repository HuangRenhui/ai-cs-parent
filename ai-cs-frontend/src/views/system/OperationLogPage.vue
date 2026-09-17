<template>
  <div class="page-card">
    <p class="hint">{{ pageDesc }}</p>
    <!-- 顶栏切片已标明本页，不再重复「操作审计」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <el-button @click="$router.push('/ops/traffic')">流量分析</el-button>
        <TbBtn v-if="canDelete" act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deleteOperationLog, '审计', load)" />
      </div>
    </div>
    <el-form inline @submit.prevent="search">
      <el-form-item label="操作人">
        <el-select v-model="query.userId" filterable clearable placeholder="全部操作人" style="width: 180px">
          <el-option v-for="u in users" :key="u.id" :label="operatorLabel(u)" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="账号">
        <el-input v-model="query.username" clearable placeholder="账号或姓名" style="width: 160px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="来源 IP">
        <el-input v-model="query.ip" clearable placeholder="精确匹配" style="width: 150px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="模块">
        <el-select v-model="query.module" filterable clearable placeholder="全部模块" style="width: 160px">
          <el-option v-for="m in LOG_MODULE_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间">
        <!-- 非超管禁用一个月以外的日期，避免界面上选出自己看不到的区间 -->
        <el-date-picker
          v-model="query.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          :disabled-date="disabledDate"
        />
      </el-form-item>
      <el-form-item>
        <FilterActions @search="search" @reset="resetSearch" />
      </el-form-item>
    </el-form>
    <el-table :data="list" stripe v-loading="loading" empty-text="暂无操作日志" table-layout="fixed" max-height="680" @selection-change="onSelect" @row-click="openDetail">
      <el-table-column v-if="canDelete" type="selection" width="48" />
      <el-table-column prop="createTime" label="时间" min-width="168" />
      <el-table-column label="操作人" min-width="140">
        <template #default="{ row }">{{ operatorCell(row) }}</template>
      </el-table-column>
      <el-table-column label="模块" min-width="88">
        <template #default="{ row }">{{ displayText(LOG_MODULE_TEXT, row.module) }}</template>
      </el-table-column>
      <el-table-column prop="operation" label="操作" min-width="140" show-overflow-tooltip />
      <el-table-column prop="requestMethod" label="请求方法" min-width="88" />
      <el-table-column label="请求地址" min-width="160">
        <template #default="{ row }">
          <CellText title="请求地址" :text="row.requestUrl" />
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="来源地址" min-width="120" />
      <el-table-column prop="duration" label="耗时（毫秒）" min-width="110" />
      <el-table-column label="结果" min-width="72">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="detail" @click.stop="openDetail(row)" />
            <TblAct v-if="canDelete" act="delete" @click.stop="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="query.pageNum" v-model:size="query.pageSize" :total="total" @change="load" />

    <el-dialog title="操作详情" v-model="detailVisible" width="640px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="时间">{{ current.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ operatorCell(current) }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ displayText(LOG_MODULE_TEXT, current.module) }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ current.operation || '—' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ current.requestMethod || '—' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ current.duration != null ? current.duration + ' 毫秒' : '—' }}</el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">{{ current.requestUrl || '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源地址">{{ current.ip || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="current.status === 1 ? 'success' : 'danger'" size="small">{{ current.status === 1 ? '成功' : '失败' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="json-block">{{ pretty(current.requestParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="响应" :span="2">
          <pre class="json-block">{{ pretty(current.responseResult) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.errorMsg" label="错误" :span="2">{{ current.errorMsg }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 操作审计页：超管可删全部记录；其他角色只能看近一个月，没有删除入口。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteOperationLog, pageOperationLogs, listUsers } from '../../api'
import TablePager from '../../components/TablePager.vue'
import { useBatchSelect } from '../../composables/useBatchSelect'
import { isSuperAdmin } from '../../utils/auth'
import { displayText, LOG_MODULE_OPTIONS, LOG_MODULE_TEXT } from '../../utils/selectOptions'

const route = useRoute()
const router = useRouter()
const list = ref([])
const users = ref([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, userId: '', module: '', username: '', ip: '', timeRange: null })
const detailVisible = ref(false)
const current = ref(null)
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
/** 是否最高超级管理员：决定删除按钮与时间范围是否放开 */
const canDelete = computed(() => isSuperAdmin())
const pageDesc = computed(() => canDelete.value
  ? '超级管理员可查看全部写操作并删除记录。'
  : '当前角色仅可查看近一个月的操作记录，不能删除，更早的日志不可见。')

const userMap = () => Object.fromEntries((users.value || []).map((u) => [String(u.id), u]))

/** 一个月前的零点，给非超管做日期禁用 */
const monthAgo = () => {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  d.setHours(0, 0, 0, 0)
  return d
}

/** 非超管不能点选一个月以外的日期 */
const disabledDate = (date) => {
  if (canDelete.value) return false
  const from = monthAgo()
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  return date.getTime() < from.getTime() || date.getTime() > end.getTime()
}

/** 下拉：姓名（账号），方便对上人 */
const operatorLabel = (u) => {
  if (!u) return '—'
  const name = u.realName || u.username || '—'
  return u.username && u.username !== name ? `${name}（${u.username}）` : name
}

/** 列表/详情：优先日志上的姓名，没有则用用户表补 */
const operatorCell = (row) => {
  if (!row) return '—'
  const u = row.userId != null ? userMap()[String(row.userId)] : null
  const name = row.realName || u?.realName || ''
  const account = row.username || u?.username || ''
  if (name && account && name !== account) return `${name}（${account}）`
  return name || account || '—'
}

/** 点行或详情：弹窗展示参数/响应，表格里放不下的长字段 */
const openDetail = (row) => {
  current.value = row
  detailVisible.value = true
}

/** 超管单条删除，服务端仍会再校验角色 */
const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除这条操作审计吗？`, '提示', { type: 'warning' })
  await deleteOperationLog(row.id)
  ElMessage.success('删除成功')
  load()
}

/** 请求/响应尽量格式化成可读 JSON */
const pretty = (raw) => {
  if (raw == null || raw === '') return '—'
  if (typeof raw === 'object') {
    try {
      return JSON.stringify(raw, null, 2)
    } catch {
      return String(raw)
    }
  }
  const text = String(raw)
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

const load = async () => {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.userId) params.userId = query.userId
    if (query.module) params.module = query.module
    if (query.username) params.username = query.username
    if (query.ip) params.ip = query.ip
    if (query.timeRange && query.timeRange.length === 2) {
      params.beginTime = query.timeRange[0]
      params.endTime = query.timeRange[1]
    }
    const res = await pageOperationLogs(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNum = 1
  load()
}

/** 清空操作人、模块、IP 和时间筛选 */
const resetSearch = () => {
  query.userId = ''
  query.module = ''
  query.username = ''
  query.ip = ''
  query.timeRange = null
  query.pageNum = 1
  // 从流量分析带过来的 ?ip= 一并清掉，避免地址栏和筛选框不一致
  if (route.query.ip) {
    router.replace({ path: '/ops/audit' })
    return
  }
  load()
}

/** 从流量分析点进来时，地址栏带 ip=，填进筛选框 */
const applyRouteIp = () => {
  const ip = route.query.ip
  query.ip = ip ? String(ip) : query.ip
}

watch(() => route.query.ip, (ip) => {
  query.ip = ip ? String(ip) : ''
  query.pageNum = 1
  load()
})

onMounted(async () => {
  applyRouteIp()
  try {
    const res = await listUsers()
    users.value = res.data || []
  } catch {
    users.value = []
  }
  load()
})
</script>

<style scoped>
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.json-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.5;
  color: #344054;
  max-height: 180px;
  overflow: auto;
}
</style>
