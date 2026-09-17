<template>
  <div>
    <p class="panel-desc">
      去重靠内容指纹而不是文件名，同图不同名也能查出重复；版本管理让「换图」可回退，
      运营误传一张图不用重传原始文件。
    </p>

    <el-tabs v-model="tab">
      <el-tab-pane label="重复检测" name="dedup">
        <div class="page-toolbar is-actions-only">
          <div class="table-actions">
            <el-button type="primary" :loading="busy === 'check'" @click="checkDedup">检测重复</el-button>
            <el-button :loading="busy === 'list'" @click="loadSimilar">查看疑似重复</el-button>
          </div>
        </div>
        <el-alert v-if="checkResult" :type="checkResult.duplicated ? 'warning' : 'success'" :closable="false" class="mb">
          <template #title>
            {{ checkResult.duplicated ? `发现 ${checkResult.matches.length} 组疑似重复` : '未发现重复资源' }}
          </template>
        </el-alert>
        <el-table :data="pairs" stripe v-loading="busy === 'list'" empty-text="暂无疑似重复" class="mini-table" table-layout="fixed">
          <el-table-column prop="fileId" label="资源ID" min-width="110" />
          <el-table-column prop="dupName" label="疑似重复对象" min-width="200" show-overflow-tooltip />
          <el-table-column label="相似度" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.similarity >= 0.95 ? 'danger' : 'warning'" size="small">{{ row.similarity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="判断依据" min-width="280" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="版本管理" name="version">
        <div class="form-row">
          <el-input v-model="fileId" placeholder="资源ID，例如 img-2001" style="width: 220px" />
          <el-button type="primary" :loading="busy === 'version'" @click="loadVersions">查看版本</el-button>
          <el-button :disabled="!versions.length" @click="openDiff">版本对比</el-button>
        </div>
        <el-table v-if="versions.length" :data="versions" stripe class="mini-table" table-layout="fixed">
          <el-table-column label="版本" min-width="90">
            <template #default="{ row }">
              v{{ row.version }}
              <el-tag v-if="row.current === 1" size="small" type="success" effect="plain" style="margin-left: 4px">当前</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="变更说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="体积" min-width="90">
            <template #default="{ row }">{{ row.sizeKb }} KB</template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" min-width="110" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" min-width="150" />
          <el-table-column label="操作" width="146" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" :disabled="row.current === 1" @click="doSwitch(row)">切到此版本</el-button>
                <el-button link type="warning" :disabled="row.current === 1" @click="doRollback(row)">回滚</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="输入资源ID查看它的历史版本" :image-size="70" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="版本对比" v-model="diffVisible" width="600px">
      <el-table :data="diffRows" stripe table-layout="fixed">
        <el-table-column prop="field" label="项目" min-width="110" />
        <el-table-column prop="before" label="旧版本" min-width="180" show-overflow-tooltip />
        <el-table-column prop="after" label="新版本" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
/** 去重检测与版本管理 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  checkMediaDedup, diffMediaVersion, listMediaVersions, listSimilarMedia,
  rollbackMediaVersion, switchMediaVersion
} from '../../../api'

const props = defineProps({
  mediaType: { type: String, default: 'image' }
})

const tab = ref('dedup')
const busy = ref('')
const checkResult = ref(null)
const pairs = ref([])
const fileId = ref('img-2001')
const versions = ref([])
const diffVisible = ref(false)
const diffRows = ref([])

const typeLabel = computed(() => (props.mediaType === 'image' ? '图片' : '音频'))

watch(() => props.mediaType, () => {
  checkResult.value = null
  pairs.value = []
  versions.value = []
  fileId.value = props.mediaType === 'image' ? 'img-2001' : 'aud-3001'
})

const checkDedup = async () => {
  busy.value = 'check'
  try {
    const res = await checkMediaDedup(props.mediaType, {})
    checkResult.value = res.data
    pairs.value = res.data?.matches || []
  } catch {
    checkResult.value = null
  } finally {
    busy.value = ''
  }
}

const loadSimilar = async () => {
  busy.value = 'list'
  try {
    const res = await listSimilarMedia(props.mediaType, {})
    pairs.value = Array.isArray(res.data) ? res.data : []
  } catch {
    pairs.value = []
  } finally {
    busy.value = ''
  }
}

const loadVersions = async () => {
  busy.value = 'version'
  try {
    const res = await listMediaVersions(props.mediaType, fileId.value)
    versions.value = Array.isArray(res.data) ? res.data : []
    if (!versions.value.length) ElMessage.warning(`「${fileId.value}」暂无版本记录`)
  } catch {
    versions.value = []
  } finally {
    busy.value = ''
  }
}

const doSwitch = async (row) => {
  try {
    await ElMessageBox.confirm(`确认把「${fileId.value}」切换到 v${row.version}？对外访问会立刻生效。`, '切换版本', { type: 'warning' })
  } catch {
    return
  }
  const res = await switchMediaVersion(props.mediaType, fileId.value, { versionId: row.versionId })
  ElMessage.success(res.msg || '已切换')
  loadVersions()
}

const doRollback = async (row) => {
  try {
    await ElMessageBox.confirm(`确认回滚到 v${row.version}？` + (typeLabel.value === '图片' ? '线上展示的图会换成这一版。' : '播报的音频会换成这一版。'), '回滚版本', { type: 'warning' })
  } catch {
    return
  }
  const res = await rollbackMediaVersion(props.mediaType, fileId.value, { versionId: row.versionId })
  ElMessage.success(res.msg || '已回滚')
  loadVersions()
}

const openDiff = async () => {
  const res = await diffMediaVersion(props.mediaType, fileId.value)
  diffRows.value = res.data?.changes || []
  diffVisible.value = true
}

onMounted(loadSimilar)
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.mini-table { margin: 10px 0; max-width: 880px; }
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
  flex-wrap: wrap;
}
.mb { margin-bottom: 12px; max-width: 720px; }
</style>
