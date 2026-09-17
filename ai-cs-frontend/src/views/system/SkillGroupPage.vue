<template>
  <div class="page-card">
    <StubBanner />
    <p class="hint">按业务线划分技能组，并配置坐席可见的数据范围。</p>
    <!-- 顶栏切片已标明本页，不再重复「技能组与数据权限」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增技能组" @click="open()" />
        <TbBtn act="delete" label="批量删除" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>
    <el-table :data="records" stripe v-loading="loading" empty-text="暂无技能组" table-layout="fixed" max-height="640" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="groupCode" label="组编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="groupName" label="技能组" min-width="130" show-overflow-tooltip />
      <el-table-column prop="skillDesc" label="技能说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="坐席数" min-width="80">
        <template #default="{ row }">{{ row.agentCount }} 人</template>
      </el-table-column>
      <el-table-column label="数据范围" min-width="120">
        <template #default="{ row }">
          <TypeTag :text="SCOPE_TEXT[row.dataScope] || row.dataScope" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="140" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog :title="form.id ? '编辑技能组' : '新增技能组'" v-model="visible" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="组编码" required>
          <el-input v-model="form.groupCode" :disabled="!!form.id" placeholder="英文，如 GROUP_AFTER_SALE" />
        </el-form-item>
        <el-form-item label="技能组" required><el-input v-model="form.groupName" /></el-form-item>
        <el-form-item label="技能说明"><el-input v-model="form.skillDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="s in scopes" :key="s.code" :label="s.name" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSkillGroups, listDataScopes, saveSkillGroup, updateSkillGroup, deleteSkillGroup } from '../../api'
import TablePager from '../../components/TablePager.vue'
import TbBtn from '../../components/TbBtn.vue'
import TblAct from '../../components/TblAct.vue'
import TypeTag from '../../components/TypeTag.vue'
import { useClientPager } from '../../composables/useClientPager'

const SCOPE_TEXT = { ALL: '全部数据', TENANT: '本租户数据', GROUP: '本技能组数据', SELF: '仅本人数据' }

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const scopes = ref([])
const selectedRows = ref([])
const batchRemoving = ref(false)
const empty = () => ({ id: null, groupCode: '', groupName: '', skillDesc: '', dataScope: 'GROUP', status: 1 })
const form = reactive(empty())

const onSelect = (rows) => {
  selectedRows.value = rows
}

const load = async () => {
  loading.value = true
  try {
    const res = await listSkillGroups()
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}
const loadScopes = async () => {
  try {
    const res = await listDataScopes()
    scopes.value = res.data || []
  } catch {
    scopes.value = []
  }
}
const open = (row) => {
  Object.assign(form, empty(), row || {})
  visible.value = true
}
const submit = async () => {
  if (!form.groupCode || !form.groupName) {
    ElMessage.warning('请填写组编码与技能组名称')
    return
  }
  saving.value = true
  try {
    if (form.id) await updateSkillGroup({ ...form })
    else await saveSkillGroup({ ...form })
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除技能组「${row.groupName}」？`, '提示', { type: 'warning' })
  await deleteSkillGroup(row.id)
  ElMessage.success('删除成功')
  load()
}

/** 批量删除技能组 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 个技能组吗？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await deleteSkillGroup(row.id)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已删除 ${ok} 个${fail ? `，失败 ${fail} 个` : ''}`)
    else ElMessage.error('删除失败')
    selectedRows.value = []
    load()
  } finally {
    batchRemoving.value = false
  }
}

onMounted(() => {
  load()
  loadScopes()
})
</script>
