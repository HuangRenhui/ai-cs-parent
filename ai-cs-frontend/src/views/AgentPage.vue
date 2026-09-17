<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div>
        <h3>坐席管理</h3>
        <p class="page-desc">维护坐席账号、工号与在线状态，转人工时按在线坐席分配。</p>
      </div>
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增坐席" @click="openDialog()" />
        <TbBtn act="export" @click="exportVisible = true" />
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>
    <el-table :data="records" stripe v-loading="loading" empty-text="暂无坐席" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column label="工号" min-width="88">
        <template #default="{ row }">{{ row.agentNo || ('A' + String(row.id).padStart(3, '0')) }}</template>
      </el-table-column>
      <el-table-column prop="agentAccount" label="账号" min-width="120" show-overflow-tooltip />
      <el-table-column prop="agentName" label="姓名" min-width="110" show-overflow-tooltip />
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="statusType(row.agentStatus)">{{ statusLabel(row.agentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="168" />
      <el-table-column label="操作" min-width="340">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="online" @click="setStatus(row, 1)" />
            <TblAct act="busy" @click="setStatus(row, 2)" />
            <TblAct act="offline" @click="setStatus(row, 0)" />
            <TblAct act="edit" @click="openDialog(row)" />
            <TblAct act="delete" @click="remove(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <ExportPickDialog
      v-model="exportVisible"
      title="导出坐席"
      :fields="exportFields"
      :selected-count="selectedRows.length"
      :page-count="records.length"
      @confirm="doExport"
    />

    <el-dialog :title="form.id ? '编辑坐席' : '新增坐席'" v-model="visible" width="480px">
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="80px">
        <el-form-item label="账号" prop="agentAccount">
          <el-input v-model="form.agentAccount" maxlength="32" placeholder="字母开头，4-32 位" />
        </el-form-item>
        <el-form-item label="姓名" prop="agentName">
          <el-input v-model="form.agentName" maxlength="32" />
        </el-form-item>
        <el-form-item label="密码" prop="agentPwd">
          <el-input v-model="form.agentPwd" type="password" maxlength="32" placeholder="6-32 位，需含字母和数字；编辑可留空" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <!-- 三态等分居中，避免下拉贴左、与全宽输入不成套 -->
          <el-radio-group v-model="form.agentStatus">
            <el-radio-button :label="1">在线</el-radio-button>
            <el-radio-button :label="2">忙碌</el-radio-button>
            <el-radio-button :label="0">离线</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteAgent, listAgents, saveAgent, updateAgent, updateAgentStatus } from '../api'
import { rules } from '../utils/validate'
import { useClientPager } from '../composables/useClientPager'
import TablePager from '../components/TablePager.vue'
import ExportPickDialog from '../components/ExportPickDialog.vue'
import { rowsToCsv, downloadTextFile } from '../utils/listCsv'

const agentList = ref([])
const { page, size, total, records } = useClientPager(agentList)
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({ id: null, agentAccount: '', agentName: '', agentPwd: '', agentStatus: 1 })
const selectedRows = ref([])
const exportVisible = ref(false)
const batchRemoving = ref(false)
const formRules = computed(() => ({
  agentAccount: rules.requiredAccount,
  agentName: rules.requiredNickname,
  agentPwd: rules.optionalPassword(!form.id)
}))

const statusLabel = (status) => ({ 0: '离线', 1: '在线', 2: '忙碌' }[status] || '未知')
const statusType = (status) => ({ 0: 'info', 1: 'success', 2: 'warning' }[status] || 'info')

/** 导出可选字段，工号缺省时按编号补 A00x，状态转中文 */
const exportFields = [
  { key: 'id', label: '编号' },
  { key: 'agentNo', label: '工号', value: (r) => r.agentNo || ('A' + String(r.id).padStart(3, '0')) },
  { key: 'agentAccount', label: '账号' },
  { key: 'agentName', label: '姓名' },
  { key: 'agentStatus', label: '状态', value: (r) => statusLabel(r.agentStatus) },
  { key: 'createTime', label: '创建时间' }
]

const onSelect = (rows) => {
  selectedRows.value = rows
}

const loadAgents = async () => {
  loading.value = true
  try {
    const res = await listAgents()
    agentList.value = res.data || []
  } catch {
    agentList.value = []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  Object.assign(form, row || { id: null, agentAccount: '', agentName: '', agentPwd: '', agentStatus: 1 })
  if (!row) form.agentPwd = 'Agent123'
  else form.agentPwd = ''
  visible.value = true
}

const submit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateAgent({ ...form })
      ElMessage.success('修改成功')
    } else {
      await saveAgent({ ...form, id: undefined })
      ElMessage.success('新增成功')
    }
    visible.value = false
    loadAgents()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const setStatus = async (row, agentStatus) => {
  await updateAgentStatus(row.id, agentStatus)
  ElMessage.success('状态已更新')
  loadAgents()
}

const remove = async (id) => {
  await ElMessageBox.confirm('确定删除该坐席吗？', '提示', { type: 'warning' })
  await deleteAgent(id)
  ElMessage.success('删除成功')
  loadAgents()
}

/** 按勾选行逐条删除坐席 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 名坐席吗？`, '批量删除', { type: 'warning' })
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await deleteAgent(row.id)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已删除 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('删除失败')
    selectedRows.value = []
    loadAgents()
  } finally {
    batchRemoving.value = false
  }
}

/** 坐席列表不分页接口，全部筛选即当前内存列表 */
const doExport = async ({ scope, keys }) => {
  const cols = exportFields.filter((f) => keys.includes(f.key))
  if (!cols.length) {
    ElMessage.warning('请至少勾选一个字段')
    return
  }
  const list = scope === 'selected' ? selectedRows.value : (scope === 'page' ? records.value : agentList.value)
  if (!list.length) {
    ElMessage.warning('没有可导出的坐席')
    return
  }
  const stamp = new Date().toISOString().slice(0, 10)
  downloadTextFile(`坐席列表_${stamp}.csv`, rowsToCsv(list, cols))
  ElMessage.success(`已导出 ${list.length} 条`)
}

onMounted(loadAgents)
</script>

<style scoped>
/* 沿用全局 page-card / page-toolbar 模板，此处补充小屏细节 */
@media (max-width: 640px) {
  .page-toolbar {
    align-items: flex-start;
  }
  .page-toolbar h3 {
    font-size: 18px;
  }
}
</style>
