<template>
  <div class="page-card">
    <p class="hint">入站接收对方系统事件；出站在会话开始、转人工、结束时回调对方客户系统。鉴权支持无校验、令牌或签名。</p>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <template v-if="section === 'in'">
      <div class="page-toolbar is-actions-only">
        <TbBtn act="add" label="新增入站" @click="openIn()" />
        <TbBtn act="delete" :disabled="!inSelected.length" :loading="inRemoving" @click="batchRemoveIn(deleteInboundWebhook, '入站回调', load)" />
      </div>
      <el-table :data="inRecords" stripe empty-text="暂无入站回调" table-layout="fixed" max-height="680" @selection-change="onInSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="路径" min-width="160">
        <template #default="{ row }">
          <CellText title="路径" :text="row.path" />
        </template>
      </el-table-column>
      <el-table-column label="事件" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ displayText(WEBHOOK_EVENT_TEXT, row.eventType) }}</template>
      </el-table-column>
      <el-table-column label="鉴权" min-width="80">
        <template #default="{ row }">
          <TypeTag :text="displayText(AUTH_TYPE_TEXT, row.authType)" />
        </template>
      </el-table-column>
      <el-table-column label="租户" min-width="88">
        <template #default="{ row }">{{ displayText(TENANT_TEXT, row.tenantCode) }}</template>
      </el-table-column>
      <el-table-column label="启用" min-width="64">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => toggleIn(row, on)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="openIn(row)" />
            <TblAct act="delete" @click="removeIn(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
      <TablePager v-model:page="inPage" v-model:size="inSize" :total="inTotal" />
    </template>

    <template v-else>
      <div class="page-toolbar is-actions-only">
        <el-button type="primary" :disabled="!outSelected.length" :loading="outRemoving" @click="batchTestOut">批量测试</el-button>
        <TbBtn act="add" label="新增出站" @click="openOut()" />
        <TbBtn act="delete" :disabled="!outSelected.length" :loading="outRemoving" @click="batchRemoveOut(deleteOutboundWebhook, '出站回调', load)" />
      </div>
      <el-table :data="outRecords" stripe empty-text="暂无出站回调" table-layout="fixed" max-height="680" @selection-change="onOutSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="回调地址" min-width="180">
        <template #default="{ row }">
          <CellText title="回调地址" :text="row.callbackUrl" />
        </template>
      </el-table-column>
      <el-table-column label="事件" min-width="120">
        <template #default="{ row }">{{ displayText(WEBHOOK_EVENT_TEXT, row.eventType) }}</template>
      </el-table-column>
      <el-table-column label="鉴权" min-width="80">
        <template #default="{ row }">
          <TypeTag :text="displayText(AUTH_TYPE_TEXT, row.authType)" />
        </template>
      </el-table-column>
      <el-table-column label="租户" min-width="88">
        <template #default="{ row }">{{ displayText(TENANT_TEXT, row.tenantCode) }}</template>
      </el-table-column>
      <el-table-column label="启用" min-width="64">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => toggleOut(row, on)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="openOut(row)" />
            <TblAct act="test" @click="testOut(row)" />
            <TblAct act="delete" @click="removeOut(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
      <TablePager v-model:page="outPage" v-model:size="outSize" :total="outTotal" />
    </template>

    <el-dialog :title="inForm.id ? '编辑入站' : '新增入站'" v-model="inVisible" width="520px">
      <el-form :model="inForm" label-width="100px">
        <el-form-item label="名称" required><el-input v-model="inForm.name" /></el-form-item>
        <el-form-item label="路径" required>
          <el-input v-model="inForm.path" placeholder="例如 /回调/物流" />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-select v-model="inForm.eventType" style="width: 100%">
            <el-option label="物流更新" value="logistics_update" />
            <el-option label="退款结果" value="refund_result" />
            <el-option label="工单进展" value="workorder_progress" />
          </el-select>
        </el-form-item>
        <el-form-item label="鉴权">
          <el-radio-group v-model="inForm.authType">
            <el-radio-button label="none">无</el-radio-button>
            <el-radio-button label="token">令牌</el-radio-button>
            <el-radio-button label="signature">签名</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="鉴权配置">
          <el-input v-model="inForm.authConfig" type="textarea" :rows="3" placeholder='令牌填 {"token":"..."}，签名填 {"secret":"..."}' />
        </el-form-item>
        <el-form-item label="租户"><TenantSelect v-model="inForm.tenantCode" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="inForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inVisible = false">取消</el-button>
        <el-button type="primary" @click="submitIn">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="outForm.id ? '编辑出站' : '新增出站'" v-model="outVisible" width="520px">
      <el-form :model="outForm" label-width="100px">
        <el-form-item label="名称" required><el-input v-model="outForm.name" /></el-form-item>
        <el-form-item label="回调地址" required><el-input v-model="outForm.callbackUrl" placeholder="对方系统的回调地址" /></el-form-item>
        <el-form-item label="事件类型">
          <el-select v-model="outForm.eventType" style="width: 100%">
            <el-option label="会话开始" value="session_start" />
            <el-option label="转人工" value="transfer_agent" />
            <el-option label="会话结束" value="session_end" />
            <el-option label="评价" value="rating" />
          </el-select>
        </el-form-item>
        <el-form-item label="鉴权">
          <el-radio-group v-model="outForm.authType">
            <el-radio-button label="none">无</el-radio-button>
            <el-radio-button label="token">令牌</el-radio-button>
            <el-radio-button label="signature">签名</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="鉴权配置">
          <el-input v-model="outForm.authConfig" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="租户"><TenantSelect v-model="outForm.tenantCode" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="outForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOut">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteInboundWebhook, deleteOutboundWebhook, enableInboundWebhook, enableOutboundWebhook,
  listInboundWebhooks, listOutboundWebhooks, saveInboundWebhook, saveOutboundWebhook, triggerOutboundWebhook
} from '../api'
import TenantSelect from '../components/TenantSelect.vue'
import { AUTH_TYPE_TEXT, displayText, TENANT_TEXT, WEBHOOK_EVENT_TEXT } from '../utils/selectOptions'
import { useClientPager } from '../composables/useClientPager'
import { useBatchSelect } from '../composables/useBatchSelect'
import TablePager from '../components/TablePager.vue'
import SectionSwitch from '../components/SectionSwitch.vue'

/** 入站 / 出站横排切片 */
const section = ref('in')
const sectionOptions = [
  { value: 'in', label: '入站回调' },
  { value: 'out', label: '出站回调' }
]

const inbounds = ref([])
const outbounds = ref([])
const { page: inPage, size: inSize, total: inTotal, records: inRecords } = useClientPager(inbounds)
const { page: outPage, size: outSize, total: outTotal, records: outRecords } = useClientPager(outbounds)
const { selectedRows: inSelected, batchRemoving: inRemoving, onSelect: onInSelect, batchRemove: batchRemoveIn } = useBatchSelect()
const { selectedRows: outSelected, batchRemoving: outRemoving, onSelect: onOutSelect, batchRemove: batchRemoveOut, batchAct: batchActOut } = useBatchSelect()
const inVisible = ref(false)
const outVisible = ref(false)
const emptyIn = () => ({ id: null, name: '', path: '', eventType: 'logistics_update', authType: 'none', authConfig: '', tenantCode: 'default', enabled: 1, remark: '' })
const emptyOut = () => ({ id: null, name: '', callbackUrl: '', eventType: 'session_start', authType: 'none', authConfig: '', tenantCode: 'default', enabled: 1, remark: '' })
const inForm = reactive(emptyIn())
const outForm = reactive(emptyOut())

const load = async () => {
  try {
    const [a, b] = await Promise.all([listInboundWebhooks(), listOutboundWebhooks()])
    inbounds.value = a.data || []
    outbounds.value = b.data || []
  } catch {
    inbounds.value = []
    outbounds.value = []
  }
}

const openIn = (row) => {
  Object.assign(inForm, emptyIn(), row || {})
  inVisible.value = true
}
const submitIn = async () => {
  if (!inForm.name || !inForm.path) {
    ElMessage.warning('请填写名称与路径')
    return
  }
  await saveInboundWebhook({ ...inForm })
  ElMessage.success('已保存')
  inVisible.value = false
  load()
}
const toggleIn = async (row, on) => {
  await enableInboundWebhook(row.id, on ? 1 : 0)
  load()
}
const removeIn = async (id) => {
  await ElMessageBox.confirm('删除该入站回调？', '提示', { type: 'warning' })
  await deleteInboundWebhook(id)
  load()
}

const openOut = (row) => {
  Object.assign(outForm, emptyOut(), row || {})
  outVisible.value = true
}
const submitOut = async () => {
  if (!outForm.name || !outForm.callbackUrl) {
    ElMessage.warning('请填写名称与回调地址')
    return
  }
  await saveOutboundWebhook({ ...outForm })
  ElMessage.success('已保存')
  outVisible.value = false
  load()
}
const toggleOut = async (row, on) => {
  await enableOutboundWebhook(row.id, on ? 1 : 0)
  load()
}
const removeOut = async (id) => {
  await ElMessageBox.confirm('删除该出站回调？', '提示', { type: 'warning' })
  await deleteOutboundWebhook(id)
  load()
}
/** 手动触发一次出站，便于核对对方接收 */
const pingOut = async (row) => {
  await triggerOutboundWebhook(row.eventType, row.tenantCode || 'default', { test: true, source: 'admin' })
}

const testOut = async (row) => {
  try {
    await ElMessageBox.confirm(`确定测试出站回调「${row.name}」？`, '确认测试', { type: 'warning' })
  } catch {
    return
  }
  await pingOut(row)
  ElMessage.success('已触发测试回调')
}

/** 对勾选出站回调逐条测试 */
const batchTestOut = () => batchActOut(
  pingOut,
  { noun: '出站回调', verb: '测试', title: '确认测试', reload: load, labelOf: (row) => row.name }
)

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 16px; }
</style>
