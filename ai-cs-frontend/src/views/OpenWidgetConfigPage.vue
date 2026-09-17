<template>
  <div class="page-card">
    <StubBanner description="白标与隐私配置存在内存里，服务重启即丢，尚未建表落库。" />
    <p class="hint">
      一个站点一套白标。不同租户、不同渠道（官网 / 活动页 / 小程序）可以各配一套，
      品牌名、主题色与隐私声明互不影响；同一时刻只有「启用」的那套对外生效。
    </p>
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <TbBtn act="add" label="新增配置" @click="open()" />
        <TbBtn act="delete" :disabled="!selectedRows.length" :loading="batchRemoving"
          @click="batchRemove(deleteWidgetConfig, '白标配置', load)" />
      </div>
    </div>

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无白标配置，可点「新增配置」添加"
      table-layout="fixed" max-height="680" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="configName" label="配置名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="tenantCode" label="适用租户" min-width="120" show-overflow-tooltip />
      <el-table-column label="品牌名称" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.brandName || '—' }}</template>
      </el-table-column>
      <el-table-column label="主题色" min-width="120">
        <template #default="{ row }">
          <span class="swatch" :style="{ background: row.brandColor || '#ccc' }"></span>
          <span class="swatch-code">{{ row.brandColor || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="窗口位置" min-width="88">
        <template #default="{ row }">{{ row.position === 'left' ? '左下' : '右下' }}</template>
      </el-table-column>
      <el-table-column label="允许附件" min-width="88">
        <template #default="{ row }">{{ row.allowAttachment === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="启用" min-width="72">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => toggleEnabled(row, on)" />
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

    <el-dialog :title="form.id ? '编辑白标配置' : '新增白标配置'" v-model="visible" width="640px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.configName" maxlength="40" placeholder="便于识别的名字，例如 官网默认 / 电商旗舰店" />
        </el-form-item>
        <el-form-item label="适用租户">
          <el-input v-model="form.tenantCode" maxlength="32" placeholder="留空表示通用；填租户编码则只对该租户生效" />
        </el-form-item>

        <el-divider content-position="left">品牌白标</el-divider>
        <el-form-item label="品牌名称"><el-input v-model="form.brandName" /></el-form-item>
        <el-form-item label="主题色">
          <el-color-picker v-model="form.brandColor" />
          <span class="color-tip">{{ form.brandColor }}</span>
        </el-form-item>
        <el-form-item label="Logo 地址"><el-input v-model="form.logoUrl" placeholder="/logo.svg 或完整图片地址" /></el-form-item>
        <el-form-item label="欢迎语">
          <el-input v-model="form.welcomeText" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="窗口位置">
          <el-radio-group v-model="form.position">
            <el-radio value="right">右下</el-radio>
            <el-radio value="left">左下</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">隐私同意</el-divider>
        <el-form-item label="启用隐私同意">
          <el-switch v-model="form.privacyEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.privacyTitle" /></el-form-item>
        <el-form-item label="声明内容">
          <el-input v-model="form.privacyText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="同意按钮文案"><el-input v-model="form.privacyAgreeText" /></el-form-item>
        <el-form-item label="允许附件">
          <el-switch v-model="form.allowAttachment" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-divider content-position="left">其他</el-divider>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
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
/**
 * Widget 隐私与白标：支持多套配置。
 * 一个站点/租户一套，避免不同业务线抢同一份品牌设置。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteWidgetConfig, listWidgetConfigs, saveWidgetConfig, setWidgetConfigEnabled, updateWidgetConfig
} from '../api'
import { useBatchSelect } from '../composables/useBatchSelect'

const list = ref([])
const { selectedRows, batchRemoving, onSelect, batchRemove } = useBatchSelect()
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)

const empty = () => ({
  id: null,
  configName: '',
  tenantCode: '',
  brandName: '',
  brandColor: '#2f6bff',
  logoUrl: '',
  welcomeText: '',
  position: 'right',
  privacyEnabled: 1,
  privacyTitle: '',
  privacyText: '',
  privacyAgreeText: '',
  allowAttachment: 1,
  sortNum: 0,
  enabled: 1
})
const form = reactive(empty())

const load = async () => {
  loading.value = true
  try {
    const res = await listWidgetConfigs()
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
  if (!form.configName) {
    ElMessage.warning('请填写配置名称')
    return
  }
  saving.value = true
  try {
    if (form.id) await updateWidgetConfig({ ...form })
    else await saveWidgetConfig({ ...form, id: undefined })
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const toggleEnabled = async (row, on) => {
  await setWidgetConfigEnabled(row.id, on ? 1 : 0)
  ElMessage.success(on ? `已启用「${row.configName}」` : `已停用「${row.configName}」`)
  load()
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(
      row.enabled === 1
        ? `「${row.configName}」正在生效，删除后客户端会回退到其它启用配置，确定删除？`
        : `确定删除「${row.configName}」吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteWidgetConfig(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; line-height: 1.7; margin: 0 0 16px; }
.color-tip { margin-left: 10px; color: #6b7280; font-size: 13px; }
.swatch {
  display: inline-block;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  vertical-align: -3px;
  margin-right: 6px;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.08);
}
.swatch-code { color: #667085; font-size: 13px; }
</style>
