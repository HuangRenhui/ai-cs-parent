<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div>
        <h3>客户管理</h3>
        <p class="page-desc">维护客户资料与标签，工单和会话可关联到对应客户。</p>
      </div>
      <div class="toolbar-actions">
        <div class="toolbar-search">
          <el-input v-model="keyword" placeholder="请输入手机号 / 邮箱 / 昵称 / 标签" clearable @keyup.enter="loadCustomers">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <FilterActions @search="loadCustomers" @reset="resetSearch" />
        </div>
        <TbBtn act="add" label="新增" @click="openDialog()" />
        <el-upload :show-file-list="false" accept=".csv,.txt" :http-request="onImportFile">
          <TbBtn act="import" />
        </el-upload>
        <TbBtn act="export" :loading="exporting" @click="exportVisible = true" />
        <el-button @click="downloadTemplate">
          <el-icon><Document /></el-icon>
          下载模板
        </el-button>
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving" @click="batchRemove" />
      </div>
    </div>
    <!-- 勾选列定宽；其余列 min-width 均分，避免中间空一大块 -->
    <el-table :data="customerList" stripe v-loading="loading" empty-text="暂无客户" table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column label="客户" min-width="180">
        <template #default="{ row }">
          <div class="cust-cell">
            <UserAvatar class="cust-avatar" :url="row.avatar" :alt="row.nickname || ''" />
            <div class="cust-meta">
              <div class="cust-name">{{ row.nickname || '未命名' }}</div>
              <div class="cust-id">编号 {{ row.id }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" min-width="132" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.email">{{ row.email }}</span>
          <span v-else class="cell-muted">未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="性别" min-width="80" align="center">
        <template #default="{ row }">
          <span v-if="row.gender === 1" class="gender-pill is-m">男</span>
          <span v-else-if="row.gender === 2" class="gender-pill is-f">女</span>
          <span v-else class="cell-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="customerTag" label="标签" min-width="100">
        <template #default="{ row }">
          <TypeTag v-if="row.customerTag" :text="row.customerTag" />
          <span v-else class="cell-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="168" show-overflow-tooltip />
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="detail" @click="showDetail(row)" />
            <TblAct act="edit" @click="openDialog(row)" />
            <TblAct act="delete" @click="remove(row.id)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="pageNum" v-model:size="pageSize" :total="total" @change="loadCustomers" />

    <ExportPickDialog
      v-model="exportVisible"
      title="导出客户"
      :fields="exportFields"
      :selected-count="selectedRows.length"
      :page-count="customerList.length"
      @confirm="doExport"
    />

    <el-dialog :title="form.id ? '编辑客户' : '新增客户'" v-model="visible" width="520px">
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="80px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="11位大陆手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" maxlength="80" placeholder="选填，如 name@example.com" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="32" placeholder="选填，中文/字母/数字" />
        </el-form-item>
        <el-form-item label="性别">
          <!-- 分段条与输入同宽、文字居中，避免圆点单选挤在左侧 -->
          <el-radio-group v-model="form.gender" @change="onGenderChange">
            <el-radio-button :label="1">男</el-radio-button>
            <el-radio-button :label="2">女</el-radio-button>
            <el-radio-button :label="0">不选</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="头像">
          <AvatarUpload v-model="form.avatar" :gender="form.gender" />
        </el-form-item>
        <el-form-item label="标签" prop="customerTag">
          <el-select v-model="form.customerTag" filterable allow-create default-first-option clearable placeholder="选择或输入标签" style="width: 100%">
            <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 客户详情：列表放不下的字段（会话数、工单数）在这里看 -->
    <el-dialog title="客户详情" v-model="detailVisible" width="560px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="编号">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detail.nickname || '未命名' }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ detail.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ detail.customerTag || '—' }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ genderLabel(detail.gender) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="会话数">{{ detail.sessionCount ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="工单数">{{ detail.workOrderCount ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="建档时间" :span="2">{{ detail.createTime || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 导入预览：后端批量接口未就绪前，确认后逐条调用现有保存接口 -->
    <el-dialog title="导入客户预览" v-model="importVisible" width="720px">
      <p class="dialog-lead">将按手机号逐条写入客户档案，手机号重复的行不会覆盖已有档案。</p>
      <el-alert v-if="importErrors.length" type="warning" :closable="false" class="import-alert" :title="importErrors.slice(0, 5).join('；')" />
      <el-table :data="importRows" stripe max-height="360" empty-text="没有可导入的行">
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
        <el-table-column label="性别" min-width="80">
          <template #default="{ row }">{{ genderLabel(row.gender) }}</template>
        </el-table-column>
        <el-table-column prop="customerTag" label="标签" min-width="100" />
      </el-table>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importRows.length" @click="confirmImport">
          导入 {{ importRows.length }} 条
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** 客户档案页：分页增删改查 + 前端表格导入导出（后端批量接口未就绪） */
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Document } from '@element-plus/icons-vue'
import { deleteCustomer, getCustomerDetail, listCustomers, pageCustomers, saveCustomer, updateCustomer } from '../api'
import { rules } from '../utils/validate'
import { genderLabel, isSystemDefaultAvatar, pickRandomDefaultAvatar } from '../utils/avatar'
import { customerTemplateCsv, downloadTextFile, parseCustomerCsv } from '../utils/customerCsv'
import { rowsToCsv } from '../utils/listCsv'
import { CUSTOMER_TAG_OPTIONS } from '../utils/selectOptions'
import AvatarUpload from '../components/AvatarUpload.vue'
import UserAvatar from '../components/UserAvatar.vue'
import TablePager from '../components/TablePager.vue'
import ExportPickDialog from '../components/ExportPickDialog.vue'

const customerList = ref([])
const keyword = ref('')
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const visible = ref(false)
const saving = ref(false)
const exporting = ref(false)
const exportVisible = ref(false)
const importVisible = ref(false)
const importing = ref(false)
const batchRemoving = ref(false)
const selectedRows = ref([])
const importRows = ref([])
const importErrors = ref([])
const formRef = ref()
const form = reactive({ id: null, phone: '', email: '', nickname: '', gender: 0, avatar: '', customerTag: '' })
const formRules = {
  phone: rules.requiredMobile,
  email: rules.optionalEmail,
  nickname: rules.optionalNickname,
  customerTag: rules.optionalTag
}

/** 导出可选字段，value 把码转成中文 */
const exportFields = [
  { key: 'id', label: '编号' },
  { key: 'nickname', label: '昵称' },
  { key: 'phone', label: '手机号' },
  { key: 'email', label: '邮箱' },
  { key: 'gender', label: '性别', value: (r) => genderLabel(r.gender) === '—' ? '' : genderLabel(r.gender) },
  { key: 'customerTag', label: '标签' },
  { key: 'createTime', label: '创建时间' }
]

const onSelect = (rows) => {
  selectedRows.value = rows
}
/** 预设标签 + 列表里已出现的标签 */
const tagOptions = computed(() => {
  const set = new Set(CUSTOMER_TAG_OPTIONS)
  for (const c of customerList.value) {
    if (c.customerTag) set.add(c.customerTag)
  }
  return [...set]
})

const resetForm = () => {
  form.id = null
  form.phone = ''
  form.email = ''
  form.nickname = ''
  form.gender = 0
  form.avatar = ''
  form.customerTag = ''
}

const onGenderChange = (gender) => {
  if (isSystemDefaultAvatar(form.avatar)) {
    form.avatar = pickRandomDefaultAvatar(gender)
  }
}

const loadCustomers = async () => {
  loading.value = true
  try {
    const res = await pageCustomers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined
    })
    customerList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    customerList.value = []
  } finally {
    loading.value = false
  }
}

/** 清空搜索条件后重新拉列表 */
const resetSearch = () => {
  keyword.value = ''
  pageNum.value = 1
  loadCustomers()
}

const openDialog = (row) => {
  resetForm()
  if (row) {
    Object.assign(form, row)
    if (form.gender == null) form.gender = 0
  }
  visible.value = true
}

const submit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateCustomer({ ...form })
      ElMessage.success('修改成功')
    } else {
      await saveCustomer({ ...form, id: undefined })
      ElMessage.success('保存成功')
    }
    visible.value = false
    loadCustomers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const remove = async (id) => {
  await ElMessageBox.confirm('确定删除该客户吗？', '提示', { type: 'warning' })
  await deleteCustomer(id)
  ElMessage.success('删除成功')
  loadCustomers()
}

/** 列表只给关键列，完整档案（会话数、工单数等）走详情接口拿 */
const detailVisible = ref(false)
const detail = ref(null)

const showDetail = async (row) => {
  try {
    const res = await getCustomerDetail(row.id)
    detail.value = res.data
  } catch {
    // 详情拉不到时退回行数据，至少不让弹窗空着
    detail.value = { ...row }
  }
  detailVisible.value = true
}

/** 按勾选行逐条删除，后端没有批量接口时走现有单条删除 */
const batchRemove = async () => {
  const rows = selectedRows.value
  if (!rows.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 位客户吗？`, '批量删除', { type: 'warning' })
  batchRemoving.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of rows) {
      try {
        await deleteCustomer(row.id)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) ElMessage.success(`已删除 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('删除失败')
    selectedRows.value = []
    loadCustomers()
  } finally {
    batchRemoving.value = false
  }
}

/** 下载含表头的空模板，方便业务按列填数 */
const downloadTemplate = () => {
  downloadTextFile('客户导入模板.csv', customerTemplateCsv())
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
    let list = []
    if (scope === 'selected') {
      list = selectedRows.value
    } else if (scope === 'page') {
      list = customerList.value
    } else {
      const res = await listCustomers(keyword.value || undefined)
      list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    }
    if (!list.length) {
      ElMessage.warning('没有可导出的客户')
      return
    }
    const stamp = new Date().toISOString().slice(0, 10)
    downloadTextFile(`客户列表_${stamp}.csv`, rowsToCsv(list, cols))
    ElMessage.success(`已导出 ${list.length} 条`)
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
    const parsed = parseCustomerCsv(text)
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

/** 逐条调用已有保存接口；重复手机号由后端/演示数据自行处理 */
const confirmImport = async () => {
  importing.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of importRows.value) {
      try {
        await saveCustomer({ ...row, id: undefined })
        ok++
      } catch {
        fail++
      }
    }
    importVisible.value = false
    if (ok) ElMessage.success(`导入完成：成功 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
    else ElMessage.error('导入失败，请检查数据后重试')
    loadCustomers()
  } finally {
    importing.value = false
  }
}

onMounted(loadCustomers)
</script>

<style scoped>
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.import-alert { margin-bottom: 12px; }
.dialog-lead { margin: 0 0 12px; color: #667085; font-size: 13px; line-height: 1.5; }
@media (max-width: 640px) {
  .toolbar-actions {
    width: 100%;
  }
  .toolbar-search,
  .toolbar-search .el-input {
    width: 100%;
  }
}
</style>
