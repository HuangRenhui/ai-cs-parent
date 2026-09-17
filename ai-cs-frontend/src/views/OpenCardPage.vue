<template>
  <div class="page-card">
    <p class="hint" v-pre>通用卡片协议：实体卡、按钮、表单。内容模板支持占位符，渲染接口用于预览。</p>
    <!-- 顶栏切片已标明本页，单列表不再重复「卡片模板」标题 -->
    <div class="page-toolbar is-actions-only">
      <TbBtn act="add" label="新增模板" @click="open()" />
      <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deleteCardTemplate, '卡片模板', load)" />
    </div>
    <el-table :data="records" stripe empty-text="暂无卡片模板" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="templateCode" label="编码" min-width="130" show-overflow-tooltip />
      <el-table-column prop="templateName" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="类型" min-width="88">
        <template #default="{ row }">
          <TypeTag :text="displayText(CARD_TYPE_TEXT, row.cardType)" />
        </template>
      </el-table-column>
      <el-table-column label="行业包" min-width="100">
        <template #default="{ row }">{{ displayText(PACK_CODE_TEXT, row.packCode) }}</template>
      </el-table-column>
      <el-table-column label="内容模板" min-width="160">
        <template #default="{ row }">
          <CellText title="内容模板" :text="row.contentJson" />
        </template>
      </el-table-column>
      <el-table-column label="启用" min-width="64">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => toggle(row, on)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
            <TblAct act="preview" @click="preview(row)" />
            <TblAct act="delete" @click="remove(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog :title="form.id ? '编辑模板' : '新增模板'" v-model="visible" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.templateCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.templateName" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.cardType">
            <el-radio-button label="entity">实体卡</el-radio-button>
            <el-radio-button label="button">按钮</el-radio-button>
            <el-radio-button label="form">表单</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="行业包"><PackSelect v-model="form.packCode" /></el-form-item>
        <el-form-item label="租户"><TenantSelect v-model="form.tenantCode" /></el-form-item>
        <el-form-item label="内容模板">
          <el-input v-model="form.contentJson" type="textarea" :rows="8" placeholder='{"title":"订单 ${orderId}"}' />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="渲染预览" v-model="previewVisible" width="520px">
      <el-input v-model="previewData" type="textarea" :rows="4" placeholder='{"orderId":"SO-1001"}' />
      <pre class="preview-out">{{ previewResult || '点击渲染查看结果' }}</pre>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="doRender">渲染</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteCardTemplate, enableCardTemplate, listCardTemplates, renderCardTemplate, saveCardTemplate } from '../api'
import PackSelect from '../components/PackSelect.vue'
import TenantSelect from '../components/TenantSelect.vue'
import { CARD_TYPE_TEXT, PACK_CODE_TEXT, displayText } from '../utils/selectOptions'
import { useClientPager } from '../composables/useClientPager'
import { useBatchSelect } from '../composables/useBatchSelect'
import TablePager from '../components/TablePager.vue'

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
const visible = ref(false)
const previewVisible = ref(false)
const previewResult = ref('')
const previewData = ref('{"orderId":"SO-1001"}')
const previewRow = ref(null)
const empty = () => ({ id: null, templateCode: '', templateName: '', cardType: 'entity', contentJson: '{}', packCode: '', tenantCode: 'default', enabled: 1, remark: '' })
const form = reactive(empty())

const load = async () => {
  try {
    const res = await listCardTemplates()
    list.value = res.data || []
  } catch {
    list.value = []
  }
}
const open = (row) => {
  Object.assign(form, empty(), row || {})
  visible.value = true
}
const submit = async () => {
  if (!form.templateCode || !form.templateName) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  if (form.contentJson) {
    try { JSON.parse(form.contentJson) } catch {
      ElMessage.warning('内容模板格式不正确')
      return
    }
  }
  await saveCardTemplate({ ...form })
  ElMessage.success('已保存')
  visible.value = false
  load()
}
const toggle = async (row, on) => {
  await enableCardTemplate(row.id, on ? 1 : 0)
  load()
}
const remove = async (id) => {
  await ElMessageBox.confirm('删除该卡片模板？', '提示', { type: 'warning' })
  await deleteCardTemplate(id)
  load()
}
const preview = (row) => {
  previewRow.value = row
  previewResult.value = ''
  previewVisible.value = true
}
const doRender = async () => {
  let data = {}
  try { data = JSON.parse(previewData.value || '{}') } catch {
    ElMessage.warning('预览数据需为对象格式')
    return
  }
  const res = await renderCardTemplate(previewRow.value.templateCode, previewRow.value.tenantCode || 'default', data)
  previewResult.value = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2)
}

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 16px; }
.preview-out { background: #f7f9fc; padding: 12px; border-radius: 8px; white-space: pre-wrap; min-height: 80px; margin-top: 12px; }
</style>
