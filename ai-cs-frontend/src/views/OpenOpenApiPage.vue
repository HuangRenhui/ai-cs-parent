<template>
  <div class="page-card">
    <p class="hint">从接口文档导入工具定义，绑定到已有连接器后执行导入。执行成功会生成开放工具。</p>
    <!-- 顶栏切片已标明本页，单列表不再重复「接口导入」标题 -->
    <div class="page-toolbar is-actions-only">
      <TbBtn act="add" label="新建导入" @click="open()" />
      <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deleteOpenApiImport, '导入记录', load)" />
    </div>
    <el-table :data="records" stripe empty-text="暂无导入记录" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="importName" label="名称" min-width="130" show-overflow-tooltip />
      <el-table-column label="文档地址" min-width="180">
        <template #default="{ row }">
          <CellText title="文档地址" :text="row.sourceUrl" />
        </template>
      </el-table-column>
      <el-table-column label="连接器" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ connectorName(row.connectorId) }}</template>
      </el-table-column>
      <el-table-column label="行业包" min-width="100">
        <template #default="{ row }">{{ displayText(PACK_CODE_TEXT, row.packCode) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="88">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ displayText(IMPORT_STATUS_TEXT, row.status, '待处理') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="toolCount" label="工具数" min-width="72" />
      <el-table-column label="错误" min-width="120">
        <template #default="{ row }">
          <CellText title="错误" :text="row.errorMsg" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="run" @click="run(row)" />
            <TblAct act="delete" @click="remove(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog title="新建接口导入" v-model="visible" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required><el-input v-model="form.importName" /></el-form-item>
        <el-form-item label="文档地址">
          <el-input v-model="form.sourceUrl" placeholder="接口文档地址" />
        </el-form-item>
        <el-form-item label="连接器">
          <el-select v-model="form.connectorId" placeholder="绑定接口连接器" clearable style="width: 100%">
            <el-option v-for="c in connectors" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业包"><PackSelect v-model="form.packCode" /></el-form-item>
        <el-form-item label="租户"><TenantSelect v-model="form.tenantCode" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createOpenApiImport, deleteOpenApiImport, executeOpenApiImport, listConnectors, listOpenApiImports
} from '../api'
import PackSelect from '../components/PackSelect.vue'
import TenantSelect from '../components/TenantSelect.vue'
import { IMPORT_STATUS_TEXT, PACK_CODE_TEXT, displayText } from '../utils/selectOptions'
import { useClientPager } from '../composables/useClientPager'
import { useBatchSelect } from '../composables/useBatchSelect'
import TablePager from '../components/TablePager.vue'

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
const connectors = ref([])
const visible = ref(false)
const empty = () => ({ importName: '', sourceUrl: '', connectorId: null, packCode: '', tenantCode: 'default', remark: '' })
const form = reactive(empty())

const statusType = (s) => ({ success: 'success', failed: 'danger', pending: 'info' }[s] || 'info')

const load = async () => {
  try {
    const res = await listOpenApiImports()
    list.value = res.data || []
  } catch {
    list.value = []
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

/** 导入记录把连接器 ID 显示成名称 */
const connectorName = (id) => {
  const c = connectors.value.find((x) => String(x.id) === String(id))
  return c ? c.name : (id ? `#${id}` : '—')
}
const open = () => {
  Object.assign(form, empty())
  visible.value = true
}
const submit = async () => {
  if (!form.importName) {
    ElMessage.warning('请填写导入名称')
    return
  }
  await createOpenApiImport({ ...form })
  ElMessage.success('已创建')
  visible.value = false
  load()
}
const run = async (row) => {
  await ElMessageBox.confirm(`执行导入「${row.importName}」？`, '提示', { type: 'warning' })
  await executeOpenApiImport(row.id)
  ElMessage.success('已触发执行')
  load()
}
const remove = async (id) => {
  await ElMessageBox.confirm('删除该导入记录？', '提示', { type: 'warning' })
  await deleteOpenApiImport(id)
  load()
}

onMounted(() => {
  load()
  loadConnectors()
})
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 16px; }
</style>
