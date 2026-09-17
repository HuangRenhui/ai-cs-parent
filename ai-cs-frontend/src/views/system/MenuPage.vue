<template>
  <div class="page-card">
    <p class="hint">
      侧栏由系统按角色自动生成。本页维护的是权限菜单树，用于角色分配与按钮级权限标识。
    </p>
    <!-- 顶栏切片已标明本页，不再重复「菜单管理」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增菜单" @click="open()" />
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deleteMenu, '菜单', load)" />
      </div>
    </div>
    <el-table :data="tree" row-key="id" default-expand-all stripe empty-text="暂无菜单" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="menuName" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="路由" min-width="150">
        <template #default="{ row }">
          <CellText title="路由" :text="row.path" />
        </template>
      </el-table-column>
      <el-table-column label="组件" min-width="140">
        <template #default="{ row }">
          <CellText title="组件" :text="row.component" />
        </template>
      </el-table-column>
      <el-table-column label="权限标识" min-width="140">
        <template #default="{ row }">
          <CellText title="权限标识" :text="row.perms" />
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="80">
        <template #default="{ row }">
          <TypeTag :text="typeLabel(row.menuType)" />
        </template>
      </el-table-column>
      <el-table-column prop="sortNum" label="排序" min-width="64" />
      <el-table-column label="可见" min-width="64">
        <template #default="{ row }">{{ row.visible === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="child" @click="open(null, row.id)" />
            <TblAct act="edit" @click="open(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="form.id ? '编辑菜单' : '新增菜单'" v-model="visible" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="父菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            check-strictly
            :render-after-expand="false"
            node-key="id"
            :props="{ label: 'menuName', children: 'children' }"
            placeholder="顶级菜单选 0"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.menuName" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.menuType">
            <el-radio-button :label="0">目录</el-radio-button>
            <el-radio-button :label="1">菜单</el-radio-button>
            <el-radio-button :label="2">按钮</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由"><el-input v-model="form.path" /></el-form-item>
        <el-form-item label="组件"><el-input v-model="form.component" /></el-form-item>
        <el-form-item label="权限标识"><el-input v-model="form.perms" placeholder="例如系统用户列表" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNum" :min="0" /></el-form-item>
        <el-form-item label="可见">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteMenu, getMenuTree, saveMenu, updateMenu } from '../../api'
import { useBatchSelect } from '../../composables/useBatchSelect'

const tree = ref([])
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
const visible = ref(false)
const saving = ref(false)
const empty = () => ({
  id: null, parentId: 0, menuName: '', menuType: 1, path: '', component: '', perms: '',
  icon: '', sortNum: 0, visible: 1, status: 1
})
const form = reactive(empty())

const typeLabel = (v) => ({ 0: '目录', 1: '菜单', 2: '按钮' }[v] || v)
/** 树选择需要顶级 0 节点，避免 parentId 无法选根 */
const parentOptions = computed(() => [{ id: 0, menuName: '顶级菜单', children: tree.value }])

const load = async () => {
  try {
    const res = await getMenuTree()
    tree.value = res.data || []
  } catch {
    tree.value = []
  }
}
const open = (row, parentId) => {
  Object.assign(form, empty(), row || {}, row ? {} : { parentId: parentId ?? 0 })
  if (form.parentId == null) form.parentId = 0
  visible.value = true
}
const submit = async () => {
  if (!form.menuName) {
    ElMessage.warning('请填写菜单名称')
    return
  }
  saving.value = true
  try {
    if (form.id) await updateMenu({ ...form })
    else await saveMenu({ ...form })
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
  await ElMessageBox.confirm(`确定删除菜单「${row.menuName}」吗？`, '提示', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 12px; }
</style>
