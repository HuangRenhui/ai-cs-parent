<template>
  <div class="page-card">
    <p class="hint">维护后台账号。角色请在角色页单独维护。</p>
    <!-- 顶栏切片已标明本页，不再重复「用户管理」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增用户" @click="open()" />
        <el-upload :show-file-list="false" accept=".csv,.txt" :http-request="onImportFile">
          <TbBtn act="import" label="批量导入" />
        </el-upload>
        <TbBtn act="export" :loading="exporting" @click="exportVisible = true" />
        <el-button @click="downloadTemplate">
          <el-icon><Document /></el-icon>
          下载模板
        </el-button>
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove(deleteUser, '用户', load)" />
      </div>
    </div>
    <el-table :data="list" stripe v-loading="loading" empty-text="暂无用户" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="编号" min-width="56" />
      <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
      <el-table-column prop="realName" label="姓名" min-width="110" show-overflow-tooltip />
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机" min-width="128" />
      <el-table-column label="状态" min-width="72">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录" min-width="168" />
      <el-table-column label="操作" min-width="148">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
            <TblAct act="delete" @click="remove(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="pageNum" v-model:size="pageSize" :total="total" @change="load" />

    <el-dialog :title="form.id ? '编辑用户' : '新增用户'" v-model="visible" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!form.id" maxlength="32" />
        </el-form-item>
        <el-form-item :label="form.id ? '新密码' : '密码'" :required="!form.id">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空表示不修改' : '必填'" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio-button :label="1">男</el-radio-button>
            <el-radio-button :label="2">女</el-radio-button>
          </el-radio-group>
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

    <!-- 导入预览：批量接口未就绪前，确认后逐条调用现有保存接口 -->
    <el-dialog title="导入用户预览" v-model="importVisible" width="780px">
      <p class="dialog-lead">
        按用户名逐条写入后台账号，用户名重复的行不会覆盖已有账号。
      </p>
      <el-alert v-if="importErrors.length" type="warning" :closable="false" class="import-alert"
        :title="importErrors.slice(0, 5).join('；')" />
      <el-table :data="importRows" stripe max-height="360" empty-text="没有可导入的行">
        <el-table-column prop="username" label="用户名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="110" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机" min-width="130" />
        <el-table-column label="性别" min-width="72">
          <template #default="{ row }">{{ row.gender === 1 ? '男' : row.gender === 2 ? '女' : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="72">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importRows.length" @click="confirmImport">
          导入 {{ importRows.length }} 条
        </el-button>
      </template>
    </el-dialog>

    <ExportPickDialog
      v-model="exportVisible"
      title="导出用户"
      :fields="exportFields"
      :selected-count="selectedRows.length"
      :page-count="list.length"
      @confirm="doExport"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { deleteUser, listUsers, pageUsers, saveUser, updateUser } from '../../api'
import TablePager from '../../components/TablePager.vue'
import ExportPickDialog from '../../components/ExportPickDialog.vue'
import { useBatchSelect } from '../../composables/useBatchSelect'
import { downloadTextFile, rowsToCsv } from '../../utils/listCsv'
import { parseUserCsv, userTemplateCsv } from '../../utils/userCsv'

const list = ref([])
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const exporting = ref(false)
const exportVisible = ref(false)
const importVisible = ref(false)
const importing = ref(false)
const importRows = ref([])
const importErrors = ref([])

/** 导出可勾选的列，与导入模板表头保持同一套中文名 */
const exportFields = [
  { key: 'id', label: '编号' },
  { key: 'username', label: '用户名' },
  { key: 'realName', label: '姓名' },
  { key: 'email', label: '邮箱' },
  { key: 'phone', label: '手机' },
  { key: 'gender', label: '性别', value: (r) => (r.gender === 1 ? '男' : r.gender === 2 ? '女' : '') },
  { key: 'status', label: '状态', value: (r) => (r.status === 1 ? '启用' : '停用') },
  { key: 'lastLoginTime', label: '最后登录' }
]

const empty = () => ({
  id: null, username: '', password: '', realName: '', email: '', phone: '', gender: 1, status: 1
})
const form = reactive(empty())

const load = async () => {
  loading.value = true
  try {
    const res = await pageUsers({ pageNum: pageNum.value, pageSize: pageSize.value })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const open = (row) => {
  Object.assign(form, empty(), row ? { ...row, password: '' } : {})
  visible.value = true
}

const submit = async () => {
  if (!form.username || (!form.id && !form.password)) {
    ElMessage.warning(form.id ? '请填写用户名' : '请填写用户名和密码')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (form.id && !payload.password) delete payload.password
    if (form.id) await updateUser(payload)
    else await saveUser(payload)
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
  await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  load()
}

/** 下载含表头和示例行的模板，避免业务猜列名 */
const downloadTemplate = () => {
  downloadTextFile('用户导入模板.csv', userTemplateCsv())
}

/** 按弹窗勾选的范围和字段写出 CSV */
const doExport = async ({ scope, keys }) => {
  const cols = exportFields.filter((f) => keys.includes(f.key))
  if (!cols.length) {
    ElMessage.warning('请至少勾选一个字段')
    return
  }
  exporting.value = true
  try {
    let rows = []
    if (scope === 'selected') {
      rows = selectedRows.value
    } else if (scope === 'page') {
      rows = list.value
    } else {
      const res = await listUsers()
      rows = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    }
    if (!rows.length) {
      ElMessage.warning('没有可导出的用户')
      return
    }
    const stamp = new Date().toISOString().slice(0, 10)
    downloadTextFile(`用户列表_${stamp}.csv`, rowsToCsv(rows, cols))
    ElMessage.success(`已导出 ${rows.length} 条`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    exporting.value = false
  }
}

/** 解析上传的 CSV，弹出预览后再写入 */
const onImportFile = async (option) => {
  const file = option.file
  if (!file) return
  try {
    const text = await file.text()
    const parsed = parseUserCsv(text)
    importErrors.value = parsed.errors
    importRows.value = parsed.rows
    if (!parsed.rows.length) {
      ElMessage.warning(parsed.errors[0] || '没有可导入的数据')
      option.onError?.(new Error('empty'))
      return
    }
    importVisible.value = true
    option.onSuccess?.()
  } catch {
    ElMessage.error('读取文件失败')
    option.onError?.(new Error('read'))
  }
}

/** 逐条调用已有保存接口；用户名重复由后端/演示数据自行处理 */
const confirmImport = async () => {
  importing.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of importRows.value) {
      try {
        await saveUser({ ...row })
        ok++
      } catch {
        fail++
      }
    }
    importVisible.value = false
    if (ok) ElMessage.success(`导入完成：成功 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('导入失败，请检查数据后重试')
    load()
  } finally {
    importing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.dialog-lead { margin: 0 0 12px; color: #667085; font-size: 13px; line-height: 1.5; }
.import-alert { margin-bottom: 12px; }
</style>
