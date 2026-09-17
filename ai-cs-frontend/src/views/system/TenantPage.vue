<template>
  <div class="page-card">
    <StubBanner />
    <p class="hint">开通租户、配置套餐与到期时间，控制租户启停。</p>
    <!-- 顶栏切片已标明本页，不再重复「多租户管理」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增租户" @click="open()" />
        <TbBtn act="delete" label="批量删除" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>
    <el-form inline>
      <el-form-item label="关键字">
        <el-input v-model="keyword" placeholder="租户名 / 编码" clearable style="width: 200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="records" stripe v-loading="loading" empty-text="暂无租户" table-layout="fixed" max-height="640" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="tenantCode" label="租户编码" min-width="120" show-overflow-tooltip />
      <el-table-column prop="tenantName" label="租户名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="套餐" min-width="100">
        <template #default="{ row }">
          <TypeTag :text="PLAN_TEXT[row.planCode] || row.planCode" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" show-overflow-tooltip />
      <el-table-column prop="expireTime" label="到期时间" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" min-width="180" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
            <TblAct :act="row.status === 1 ? 'offline' : 'online'" :label="row.status === 1 ? '停用' : '启用'" @click="toggleStatus(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog :title="form.id ? '编辑租户' : '新增租户'" v-model="visible" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="租户编码" required>
          <el-input v-model="form.tenantCode" :disabled="!!form.id" placeholder="英文，用于数据隔离" />
        </el-form-item>
        <el-form-item label="租户名称" required><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="套餐">
          <el-select v-model="form.planCode" style="width: 100%">
            <el-option v-for="p in PLAN_OPTIONS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期时间">
          <el-input v-model="form.expireTime" placeholder="yyyy-MM-dd HH:mm:ss" />
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
import { listTenants, saveTenant, updateTenant, updateTenantStatus, deleteTenant } from '../../api'
import TablePager from '../../components/TablePager.vue'
import TbBtn from '../../components/TbBtn.vue'
import TblAct from '../../components/TblAct.vue'
import TypeTag from '../../components/TypeTag.vue'
import { useClientPager } from '../../composables/useClientPager'

const PLAN_TEXT = { BASIC: '基础版', PRO: '专业版', ENTERPRISE: '企业版', TRIAL: '试用版' }
const PLAN_OPTIONS = [
  { value: 'BASIC', label: '基础版' },
  { value: 'PRO', label: '专业版' },
  { value: 'ENTERPRISE', label: '企业版' },
  { value: 'TRIAL', label: '试用版' }
]

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const loading = ref(false)
const keyword = ref('')
const visible = ref(false)
const saving = ref(false)
const selectedRows = ref([])
const batchRemoving = ref(false)
const empty = () => ({ id: null, tenantCode: '', tenantName: '', planCode: 'BASIC', status: 1, expireTime: '' })
const form = reactive(empty())

const onSelect = (rows) => {
  selectedRows.value = rows
}

const load = async () => {
  loading.value = true
  try {
    const res = await listTenants(keyword.value)
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
  if (!form.tenantCode || !form.tenantName) {
    ElMessage.warning('请填写租户编码与名称')
    return
  }
  saving.value = true
  try {
    if (form.id) await updateTenant({ ...form })
    else await saveTenant({ ...form })
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
const toggleStatus = async (row) => {
  await updateTenantStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('状态已更新')
  load()
}
const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除租户「${row.tenantName}」？`, '提示', { type: 'warning' })
  await deleteTenant(row.id)
  ElMessage.success('删除成功')
  load()
}

/** 批量删除租户 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 个租户吗？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await deleteTenant(row.id)
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

onMounted(load)
</script>
