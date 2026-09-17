<template>
  <div class="page-card">
    <p class="hint">列表接口只返回启用项。租户日配额走智能服务，改上限后立即生效。</p>
    <!-- 顶栏切片已标明本页，不再重复「系统配置」标题 -->
    <div class="page-toolbar is-actions-only">
      <TbBtn v-if="section === 'config'" act="add" label="新增配置" @click="open()" />
    </div>
    <SectionSwitch v-model="section" :options="sectionOptions" />

    <template v-if="section === 'config'">
    <el-table :data="records" stripe v-loading="loading" empty-text="暂无启用配置" table-layout="fixed" max-height="680">
      <el-table-column prop="configKey" label="配置项" min-width="180" show-overflow-tooltip />
      <el-table-column label="配置值" min-width="160">
        <template #default="{ row }">
          <CellText title="配置值" :text="row.configValue" />
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="88">
        <template #default="{ row }">
          <TypeTag :text="displayText(CONFIG_TYPE_TEXT, row.configType)" />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="140">
        <template #default="{ row }">
          <CellText title="说明" :text="row.description" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="72">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="88">
        <template #default="{ row }">
          <div class="table-actions">
            <TblAct act="edit" @click="open(row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />
    </template>

    <template v-else>
    <el-form inline>
      <el-form-item label="租户">
        <div class="quota-tenant">
          <TenantSelect v-model="quotaTenant" />
        </div>
      </el-form-item>
      <el-form-item>
        <el-button @click="loadQuota">查询用量</el-button>
      </el-form-item>
    </el-form>
    <p v-if="quota" class="quota-line">
      今日已用 {{ quota.usage }} / 上限 {{ quota.limit }}
      <el-tag :type="quota.exceeded ? 'danger' : 'success'" size="small">{{ quota.exceeded ? '已超限' : '正常' }}</el-tag>
    </p>
    <el-form inline>
      <el-form-item label="新上限">
        <el-input-number v-model="quotaLimit" :min="0" :step="100" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveQuota">保存配额</el-button>
      </el-form-item>
    </el-form>
    </template>

    <el-dialog :title="form.id ? '编辑配置' : '新增配置'" v-model="visible" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="配置项" required>
          <el-input v-model="form.configKey" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="form.configValue" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.configType" style="width: 100%">
            <el-option v-for="t in CONFIG_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" /></el-form-item>
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
import { ElMessage } from 'element-plus'
import { getTenantQuota, listSysConfigs, saveSysConfig, setTenantQuota, updateSysConfig } from '../../api'
import TenantSelect from '../../components/TenantSelect.vue'
import TablePager from '../../components/TablePager.vue'
import SectionSwitch from '../../components/SectionSwitch.vue'
import { useClientPager } from '../../composables/useClientPager'
import { CONFIG_TYPE_OPTIONS, CONFIG_TYPE_TEXT, displayText } from '../../utils/selectOptions'

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const quotaTenant = ref('default')
const quota = ref(null)
const quotaLimit = ref(1000)
const empty = () => ({ id: null, configKey: '', configValue: '', configType: 'system', description: '', status: 1 })
const form = reactive(empty())

/** 键值配置 / 租户日配额横排切片 */
const section = ref('config')
const sectionOptions = [
  { value: 'config', label: '键值配置' },
  { value: 'quota', label: '租户日配额' }
]

const load = async () => {
  loading.value = true
  try {
    const res = await listSysConfigs()
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
  if (!form.configKey || form.configValue == null || form.configValue === '') {
    ElMessage.warning('请填写配置键与值')
    return
  }
  saving.value = true
  try {
    if (form.id) await updateSysConfig({ ...form })
    else await saveSysConfig({ ...form })
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
const loadQuota = async () => {
  try {
    const res = await getTenantQuota(quotaTenant.value || 'default')
    quota.value = res.data
    if (res.data?.limit != null) quotaLimit.value = Number(res.data.limit)
  } catch {
    quota.value = null
  }
}
const saveQuota = async () => {
  await setTenantQuota(quotaTenant.value || 'default', quotaLimit.value)
  ElMessage.success('配额已更新')
  loadQuota()
}

onMounted(() => {
  load()
  loadQuota()
})
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 12px; }
.quota-line { color: #374151; margin: 0 0 12px; display: flex; gap: 8px; align-items: center; }
.quota-tenant { width: 220px; }
</style>
