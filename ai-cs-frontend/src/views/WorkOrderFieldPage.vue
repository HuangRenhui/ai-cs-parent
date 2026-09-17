<template>
  <div class="page-card">
    <StubBanner />
    <div class="page-toolbar">
      <div>
        <h3>工单自定义字段</h3>
        <p class="page-desc">为工单扩展业务字段，字段类型与是否必填可配置。</p>
      </div>
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增字段" @click="open()" />
        <TbBtn act="complete" label="批量启用" :disabled="!selectedRows.length" :loading="batchEnabling" @click="batchEnable" />
        <TbBtn act="close" label="批量关闭" :disabled="!selectedRows.length" :loading="batchDisabling" @click="batchDisable" />
        <TbBtn act="delete" label="批量删除" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>
    <el-table :data="records" stripe v-loading="loading" empty-text="暂无字段" table-layout="fixed" max-height="640" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="sortNum" label="排序" width="70" />
      <el-table-column prop="fieldKey" label="字段键" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fieldName" label="字段名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" min-width="110">
        <template #default="{ row }">
          <TypeTag :text="typeText(row.fieldType)" />
        </template>
      </el-table-column>
      <el-table-column label="必填" min-width="70">
        <template #default="{ row }">
          <el-tag :type="row.required === 1 ? 'warning' : 'info'" size="small">{{ row.required === 1 ? '必填' : '选填' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            @change="(v) => toggleStatus(row, v)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog :title="form.id ? '编辑字段' : '新增字段'" v-model="visible" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="字段键" required>
          <el-input v-model="form.fieldKey" :disabled="!!form.id" placeholder="英文，如 orderNo" />
        </el-form-item>
        <el-form-item label="字段名称" required><el-input v-model="form.fieldName" /></el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="form.fieldType" style="width: 100%">
            <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="form.required" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNum" :min="0" /></el-form-item>
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
import { listWorkOrderFields, listWorkOrderFieldTypes, saveWorkOrderField, updateWorkOrderField, deleteWorkOrderField } from '../api'
import TablePager from '../components/TablePager.vue'
import TbBtn from '../components/TbBtn.vue'
import TblAct from '../components/TblAct.vue'
import TypeTag from '../components/TypeTag.vue'
import { useClientPager } from '../composables/useClientPager'

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const types = ref([])
const selectedRows = ref([])
const batchRemoving = ref(false)
const batchEnabling = ref(false)
const batchDisabling = ref(false)

const onSelect = (rows) => {
  selectedRows.value = rows
}
const empty = () => ({ id: null, fieldKey: '', fieldName: '', fieldType: 'text', required: 0, sortNum: 10, status: 1 })
const form = reactive(empty())

const typeText = (code) => {
  const hit = types.value.find((t) => t.code === code)
  return hit ? hit.name : code
}
const load = async () => {
  loading.value = true
  try {
    const res = await listWorkOrderFields()
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}
const loadTypes = async () => {
  try {
    const res = await listWorkOrderFieldTypes()
    types.value = res.data || []
  } catch {
    types.value = []
  }
}
const open = (row) => {
  Object.assign(form, empty(), row || {})
  visible.value = true
}
const submit = async () => {
  if (!form.fieldKey || !form.fieldName) {
    ElMessage.warning('请填写字段键与名称')
    return
  }
  saving.value = true
  try {
    if (form.id) await updateWorkOrderField({ ...form })
    else await saveWorkOrderField({ ...form })
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
  await ElMessageBox.confirm(`确认删除字段「${row.fieldName}」？`, '提示', { type: 'warning' })
  await deleteWorkOrderField(row.id)
  ElMessage.success('删除成功')
  load()
}

/** 单条切换启用 / 停用 */
const toggleStatus = async (row, status) => {
  try {
    await updateWorkOrderField({ ...row, status })
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已停用')
  } catch {
    load()
  }
}

/** 批量启用（跳过已启用的） */
const batchEnable = async () => {
  const rows = selectedRows.value.filter((r) => r.status !== 1)
  if (!rows.length) {
    ElMessage.warning('选中的字段都已是启用状态')
    return
  }
  try {
    await ElMessageBox.confirm(`确定启用选中的 ${rows.length} 个字段吗？`, '批量启用', { type: 'warning' })
  } catch {
    return
  }
  batchEnabling.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await updateWorkOrderField({ ...row, status: 1 })
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已启用 ${ok} 个${fail ? `，失败 ${fail} 个` : ''}`)
    else ElMessage.error('操作失败')
    selectedRows.value = []
    load()
  } finally {
    batchEnabling.value = false
  }
}

/** 批量关闭（停用，跳过已关闭的） */
const batchDisable = async () => {
  const rows = selectedRows.value.filter((r) => r.status !== 0)
  if (!rows.length) {
    ElMessage.warning('选中的字段都已是关闭状态')
    return
  }
  try {
    await ElMessageBox.confirm(`确定关闭选中的 ${rows.length} 个字段吗？`, '批量关闭', { type: 'warning' })
  } catch {
    return
  }
  batchDisabling.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await updateWorkOrderField({ ...row, status: 0 })
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已关闭 ${ok} 个${fail ? `，失败 ${fail} 个` : ''}`)
    else ElMessage.error('操作失败')
    selectedRows.value = []
    load()
  } finally {
    batchDisabling.value = false
  }
}

/** 批量删除 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 个字段吗？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await deleteWorkOrderField(row.id)
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
  loadTypes()
})
</script>
