<template>
  <div class="page-card">
    <p class="hint">行业提示词按行业包与类型叠加到检索人设、拒答、槽位，避免把电商话术写死在内核。聊天页可选择同一行业包。</p>
    <!-- 顶栏切片已标明本页，单列表不再重复「提示词包」标题 -->
    <div class="page-toolbar">
      <el-select v-model="filterPack" placeholder="按行业包筛选" clearable style="width: 180px">
        <el-option v-for="p in packs" :key="p.code" :label="p.name || p.code" :value="p.code" />
      </el-select>
      <FilterActions @search="load" @reset="resetPackFilter" />
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增提示词" @click="open()" />
        <el-button @click="previewLoad">预览合并文本</el-button>
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deletePromptPack, '提示词', load)" />
      </div>
    </div>
    <el-table :data="records" stripe empty-text="暂无提示词" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column label="行业包" min-width="110">
        <template #default="{ row }">{{ displayText(PACK_CODE_TEXT, row.packCode) }}</template>
      </el-table-column>
      <el-table-column label="类型" min-width="88">
        <template #default="{ row }">
          <TypeTag :text="displayText(PROMPT_TYPE_TEXT, row.promptType)" />
        </template>
      </el-table-column>
      <el-table-column label="场景" min-width="88">
        <template #default="{ row }">{{ displayText(PROMPT_SCENE_TEXT, row.scene, row.scene || '—') }}</template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" min-width="72" />
      <el-table-column label="内容" min-width="180">
        <template #default="{ row }">
          <CellText title="内容" :text="row.promptContent" />
        </template>
      </el-table-column>
      <el-table-column label="启用" min-width="64">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => toggle(row, on)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
            <TblAct act="delete" @click="remove(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog :title="form.id ? '编辑提示词' : '新增提示词'" v-model="visible" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="行业包" required>
          <PackSelect v-model="form.packCode" :clearable="false" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.promptType">
            <el-radio-button label="persona">人设</el-radio-button>
            <el-radio-button label="rejection">拒答</el-radio-button>
            <el-radio-button label="slot">槽位</el-radio-button>
            <el-radio-button label="system">系统</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="form.scene" filterable allow-create default-first-option clearable placeholder="可选场景" style="width: 100%">
            <el-option v-for="s in PROMPT_SCENE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" :max="100" /></el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.promptContent" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="合并后的提示词" v-model="previewVisible" width="640px">
      <pre class="preview-out">{{ previewText || '暂无内容' }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deletePromptPack, enablePromptPack, listOpenPacks, listPromptPacks, loadPromptPack, savePromptPack
} from '../api'
import PackSelect from '../components/PackSelect.vue'
import { displayText, PACK_CODE_TEXT, PROMPT_SCENE_OPTIONS, PROMPT_SCENE_TEXT, PROMPT_TYPE_TEXT } from '../utils/selectOptions'
import { useClientPager } from '../composables/useClientPager'
import { useBatchSelect } from '../composables/useBatchSelect'
import TablePager from '../components/TablePager.vue'

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
const packs = ref([])
const filterPack = ref('')
const visible = ref(false)
const previewVisible = ref(false)
const previewText = ref('')
const empty = () => ({
  id: null, packCode: 'ecommerce', promptType: 'persona', promptContent: '', scene: '',
  priority: 0, enabled: 1, remark: ''
})
const form = reactive(empty())

const load = async () => {
  try {
    const res = await listPromptPacks()
    const rows = res.data || []
    // 列表接口返回全量，按行业包在前端过滤，避免再打一次 listByPack
    list.value = filterPack.value ? rows.filter((r) => r.packCode === filterPack.value) : rows
  } catch {
    list.value = []
  }
}

/** 清空行业包筛选后重新拉全量 */
const resetPackFilter = () => {
  filterPack.value = ''
  load()
}

const loadPacks = async () => {
  try {
    const res = await listOpenPacks()
    packs.value = res.data || []
  } catch {
    packs.value = []
  }
}

const open = (row) => {
  Object.assign(form, empty(), { packCode: filterPack.value || 'ecommerce' }, row || {})
  visible.value = true
}
const submit = async () => {
  if (!form.packCode || !form.promptType || !form.promptContent) {
    ElMessage.warning('请填写行业包、类型与内容')
    return
  }
  await savePromptPack({ ...form })
  ElMessage.success('已保存')
  visible.value = false
  load()
}
const toggle = async (row, on) => {
  await enablePromptPack(row.id, on ? 1 : 0)
  load()
}
const remove = async (id) => {
  await ElMessageBox.confirm('删除该提示词？', '提示', { type: 'warning' })
  await deletePromptPack(id)
  load()
}
/** 调用 load 接口查看该 pack 合并后的人设文本 */
const previewLoad = async () => {
  const code = filterPack.value || form.packCode || 'ecommerce'
  const res = await loadPromptPack(code)
  previewText.value = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2)
  previewVisible.value = true
}

onMounted(() => {
  loadPacks()
  load()
})
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 16px; }
.preview-out { background: #f7f9fc; padding: 12px; border-radius: 8px; white-space: pre-wrap; min-height: 120px; }
</style>
