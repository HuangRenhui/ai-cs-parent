<template>
  <div class="page-card">
    <p class="hint">出站域名必须命中白名单，避免服务端被诱导向非法地址发请求。字段映射把对方系统字段转成内核字段，并支持测试正向/反向转换。</p>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <template v-if="section === 'whitelist'">
      <div class="page-toolbar is-actions-only">
        <TbBtn act="add" label="新增白名单" @click="openWl()" />
        <TbBtn act="delete" :disabled="!wlSelected.length" :loading="wlRemoving" @click="batchRemoveWl(deleteUrlWhitelist, '白名单', loadWl)" />
      </div>
      <el-table :data="wlRecords" stripe empty-text="暂无白名单" table-layout="fixed" max-height="680" @selection-change="onWlSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column label="租户" min-width="100">
        <template #default="{ row }">{{ displayText(TENANT_TEXT, row.tenantCode) }}</template>
      </el-table-column>
      <el-table-column label="域名或地址模式" min-width="180">
        <template #default="{ row }">
          <CellText title="域名或地址模式" :text="row.domainPattern" />
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="120">
        <template #default="{ row }">
          <CellText title="备注" :text="row.remark" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="openWl(row)" />
            <TblAct act="delete" @click="removeWl(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
      <TablePager v-model:page="wlPage" v-model:size="wlSize" :total="wlTotal" />
    </template>

    <template v-else>
      <div class="page-toolbar is-actions-only">
        <!-- 映射接口按连接器 ID 查询，必须先选中一个连接器 -->
        <el-select v-model="mappingConnectorId" placeholder="选择连接器" clearable style="width: 220px" @change="loadMappings">
          <el-option v-for="c in connectors" :key="c.id" :label="`${c.name || c.id} (#${c.id})`" :value="c.id" />
        </el-select>
        <TbBtn act="add" label="新增映射" :disabled="!mappingConnectorId" @click="openMp()" />
        <TbBtn act="delete" :disabled="!mpSelected.length" :loading="mpRemoving" @click="batchRemoveMp(deleteFieldMapping, '映射', loadMappings)" />
      </div>
      <el-table :data="mpRecords" stripe empty-text="请先选择连接器" table-layout="fixed" max-height="680" @selection-change="onMpSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="sourceField" label="对方字段" min-width="140" show-overflow-tooltip />
      <el-table-column prop="targetField" label="内核字段" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" min-width="88">
        <template #default="{ row }">
          <TypeTag :text="displayText(FIELD_TYPE_TEXT, row.fieldType)" />
        </template>
      </el-table-column>
      <el-table-column label="转换规则" min-width="140">
        <template #default="{ row }">
          <CellText title="转换规则" :text="row.transformRule" />
        </template>
      </el-table-column>
      <el-table-column label="必填" min-width="64">
        <template #default="{ row }">{{ row.required === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="openMp(row)" />
            <TblAct act="delete" @click="removeMp(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
      <TablePager v-model:page="mpPage" v-model:size="mpSize" :total="mpTotal" />

      <p class="hint" style="margin-top: 16px">映射测试：用一段对方字段的数据，看正向/反向转换结果。</p>
      <el-input v-model="testPayload" type="textarea" :rows="4" placeholder='{"orderNo":"SO-1"}' />
      <div class="page-toolbar is-actions-only">
        <el-button :disabled="!mappingConnectorId" @click="runApply">正向（对方→内核）</el-button>
        <el-button :disabled="!mappingConnectorId" @click="runReverse">反向（内核→对方）</el-button>
      </div>
      <pre class="preview-out">{{ testResult }}</pre>
    </template>

    <el-dialog :title="wlForm.id ? '编辑白名单' : '新增白名单'" v-model="wlVisible" width="520px">
      <el-form :model="wlForm" label-width="110px">
        <el-form-item label="租户" required><TenantSelect v-model="wlForm.tenantCode" /></el-form-item>
        <el-form-item label="域名模式" required>
          <el-input v-model="wlForm.domainPattern" placeholder="例如 *.合作方.com 或 api.合作方.com" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="wlForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wlVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWl">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="mpForm.id ? '编辑映射' : '新增映射'" v-model="mpVisible" width="520px">
      <el-form :model="mpForm" label-width="110px">
        <el-form-item label="对方字段" required><el-input v-model="mpForm.sourceField" placeholder="例如订单号字段" /></el-form-item>
        <el-form-item label="内核字段" required><el-input v-model="mpForm.targetField" placeholder="例如实体编号" /></el-form-item>
        <el-form-item label="字段类型">
          <el-radio-group v-model="mpForm.fieldType">
            <el-radio-button label="string">文本</el-radio-button>
            <el-radio-button label="number">数字</el-radio-button>
            <el-radio-button label="date">日期</el-radio-button>
            <el-radio-button label="json">对象</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="转换规则"><el-input v-model="mpForm.transformRule" placeholder="可选，按约定填写转换规则" /></el-form-item>
        <el-form-item label="默认值"><el-input v-model="mpForm.defaultValue" /></el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="mpForm.required" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="mpForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mpVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMp">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteFieldMapping, deleteUrlWhitelist, listConnectors, listFieldMappings, listUrlWhitelist,
  saveFieldMapping, saveUrlWhitelist, testApplyMapping, testReverseMapping
} from '../api'
import TenantSelect from '../components/TenantSelect.vue'
import TablePager from '../components/TablePager.vue'
import SectionSwitch from '../components/SectionSwitch.vue'
import { useClientPager } from '../composables/useClientPager'
import { useBatchSelect } from '../composables/useBatchSelect'
import { FIELD_TYPE_TEXT, TENANT_TEXT, displayText } from '../utils/selectOptions'

/** 白名单 / 字段映射（含测试）横排切片 */
const section = ref('whitelist')
const sectionOptions = [
  { value: 'whitelist', label: '地址白名单' },
  { value: 'mapping', label: '字段映射' }
]

const whitelists = ref([])
const mappings = ref([])
const { page: wlPage, size: wlSize, total: wlTotal, records: wlRecords } = useClientPager(whitelists)
const { page: mpPage, size: mpSize, total: mpTotal, records: mpRecords } = useClientPager(mappings)
const { selectedRows: wlSelected, batchRemoving: wlRemoving, onSelect: onWlSelect, batchRemove: batchRemoveWl } = useBatchSelect()
const { selectedRows: mpSelected, batchRemoving: mpRemoving, onSelect: onMpSelect, batchRemove: batchRemoveMp } = useBatchSelect()
const connectors = ref([])
const mappingConnectorId = ref(null)
const wlVisible = ref(false)
const mpVisible = ref(false)
const emptyWl = () => ({ id: null, tenantCode: 'default', domainPattern: '', remark: '' })
const emptyMp = () => ({
  id: null, connectorId: null, sourceField: '', targetField: '', fieldType: 'string',
  transformRule: '', required: 0, defaultValue: '', remark: ''
})
const wlForm = reactive(emptyWl())
const mpForm = reactive(emptyMp())
const testPayload = ref('{"orderNo":"SO-1"}')
const testResult = ref('')

/** 加载租户出站白名单 */
const loadWl = async () => {
  try {
    const res = await listUrlWhitelist()
    whitelists.value = res.data || []
  } catch {
    whitelists.value = []
  }
}
/** 按连接器 ID 拉取字段映射；未选中时清空表格 */
const loadMappings = async () => {
  if (!mappingConnectorId.value) {
    mappings.value = []
    return
  }
  try {
    const res = await listFieldMappings(mappingConnectorId.value)
    mappings.value = res.data || []
  } catch {
    mappings.value = []
  }
}
const loadConnectors = async () => {
  try {
    const res = await listConnectors()
    connectors.value = res.data || []
  } catch {
    connectors.value = []
  }
}

const openWl = (row) => {
  Object.assign(wlForm, emptyWl(), row || {})
  wlVisible.value = true
}
const submitWl = async () => {
  if (!wlForm.tenantCode || !wlForm.domainPattern) {
    ElMessage.warning('请填写租户与域名模式')
    return
  }
  await saveUrlWhitelist({ ...wlForm })
  ElMessage.success('已保存')
  wlVisible.value = false
  loadWl()
}
const removeWl = async (id) => {
  await ElMessageBox.confirm('删除该白名单？', '提示', { type: 'warning' })
  await deleteUrlWhitelist(id)
  loadWl()
}

const openMp = (row) => {
  Object.assign(mpForm, emptyMp(), { connectorId: mappingConnectorId.value }, row || {})
  mpVisible.value = true
}
const submitMp = async () => {
  if (!mappingConnectorId.value || !mpForm.sourceField || !mpForm.targetField) {
    ElMessage.warning('请填写对方字段与内核字段')
    return
  }
  await saveFieldMapping({ ...mpForm, connectorId: mappingConnectorId.value })
  ElMessage.success('已保存')
  mpVisible.value = false
  loadMappings()
}
const removeMp = async (id) => {
  await ElMessageBox.confirm('删除该映射？', '提示', { type: 'warning' })
  await deleteFieldMapping(id)
  loadMappings()
}

const parsePayload = () => {
  try {
    return JSON.parse(testPayload.value || '{}')
  } catch {
    ElMessage.warning('测试数据需为对象格式')
    return null
  }
}
const runApply = async () => {
  const payload = parsePayload()
  if (!payload || !mappingConnectorId.value) return
  const res = await testApplyMapping(mappingConnectorId.value, payload)
  testResult.value = JSON.stringify(res.data, null, 2)
}
const runReverse = async () => {
  const payload = parsePayload()
  if (!payload || !mappingConnectorId.value) return
  const res = await testReverseMapping(mappingConnectorId.value, payload)
  testResult.value = JSON.stringify(res.data, null, 2)
}

onMounted(() => {
  loadWl()
  loadConnectors()
})
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 16px; }
.preview-out { background: #f7f9fc; padding: 12px; border-radius: 8px; white-space: pre-wrap; min-height: 80px; }
</style>
