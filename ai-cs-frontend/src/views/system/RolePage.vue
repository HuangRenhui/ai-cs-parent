<template>
  <div class="page-card">
    <p class="hint">维护角色编码与权限范围。</p>
    <!-- 顶栏切片已标明本页，不再重复「角色管理」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增角色" @click="open()" />
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deleteRole, '角色', load)" />
      </div>
    </div>
    <el-table :data="records" stripe v-loading="loading" empty-text="暂无角色" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column prop="roleName" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="roleCode" label="编码" min-width="140" show-overflow-tooltip />
      <el-table-column label="描述" min-width="160">
        <template #default="{ row }">
          <CellText title="描述" :text="row.description" />
        </template>
      </el-table-column>
      <el-table-column prop="sortNum" label="排序" min-width="72" />
      <el-table-column label="状态" min-width="72">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog :title="form.id ? '编辑角色' : '新增角色'" v-model="visible" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="form.roleCode" :disabled="!!form.id" placeholder="例如管理员编码" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
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
import { deleteRole, listRoles, saveRole, updateRole } from '../../api'
import { useClientPager } from '../../composables/useClientPager'
import { useBatchSelect } from '../../composables/useBatchSelect'
import TablePager from '../../components/TablePager.vue'

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const empty = () => ({ id: null, roleName: '', roleCode: '', description: '', sortNum: 0, status: 1 })
const form = reactive(empty())

const load = async () => {
  loading.value = true
  try {
    const res = await listRoles()
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}
const open = (row) => {
  Object.assign(form, empty(), row || {})
  visible.value = true
}
const submit = async () => {
  if (!form.roleName || !form.roleCode) {
    ElMessage.warning('请填写名称与编码')
    return
  }
  saving.value = true
  try {
    if (form.id) await updateRole({ ...form })
    else await saveRole({ ...form })
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
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '提示', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
