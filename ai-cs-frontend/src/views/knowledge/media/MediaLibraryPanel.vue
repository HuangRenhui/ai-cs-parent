<template>
  <div>
    <p class="panel-desc">
      {{ typeLabel }}资源库。原始资源入库后可以转码、{{ mediaType === 'image' ? '缩放裁剪' : '调整码率' }}、
      向量化以便检索。向量化之后才能被语义检索命中。
    </p>

    <div class="page-toolbar is-actions-only">
      <div class="table-actions">
        <el-button type="primary" :loading="busy === 'batch'" @click="batchUpload">批量上传</el-button>
        <el-input v-model="keyword" placeholder="按名称或标签搜索" clearable style="width: 220px" @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="load">查询</el-button>
        <el-button :loading="busy === 'vectorAll'" @click="vectorizeSelected">批量向量化</el-button>
      </div>
    </div>

    <!-- 批量上传进度 -->
    <el-alert v-if="batch" type="info" :closable="false" class="batch-box">
      <template #title>
        批量上传中（{{ batch.done }} / {{ batch.total }}）{{ batch.failed ? `，失败 ${batch.failed} 个` : '' }}
      </template>
      <template #default>
        <el-progress :percentage="batch.percent" :stroke-width="6" />
      </template>
    </el-alert>

    <el-table :data="list" stripe v-loading="loading" :empty-text="`暂无${typeLabel}资源`"
      table-layout="fixed" max-height="420" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="fileId" label="资源ID" min-width="110" />
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="格式" min-width="76">
        <template #default="{ row }"><TypeTag :text="row.ext.toUpperCase()" /></template>
      </el-table-column>
      <el-table-column label="体积" min-width="90">
        <template #default="{ row }">{{ row.sizeKb }} KB</template>
      </el-table-column>
      <el-table-column :label="mediaType === 'image' ? '尺寸' : '时长'" min-width="120">
        <template #default="{ row }">
          {{ mediaType === 'image' ? `${row.width} × ${row.height}` : `${row.duration} 秒` }}
        </template>
      </el-table-column>
      <el-table-column label="标签" min-width="140">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags" :key="t" size="small" effect="plain" style="margin-right: 4px">{{ t }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已向量化" min-width="96">
        <template #default="{ row }">
          <el-tag :type="row.vectorized === 1 ? 'success' : 'info'" size="small">{{ row.vectorized === 1 ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="入库时间" min-width="150" />
      <el-table-column label="操作" width="308" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="showMeta(row)">详情</el-button>
            <el-button link type="primary" @click="openConvert(row)">转码</el-button>
            <el-button v-if="mediaType === 'image'" link type="primary" @click="openResize(row)">缩放裁剪</el-button>
            <el-button v-else link type="primary" @click="openBitrate(row)">码率</el-button>
            <el-button link type="primary" :disabled="row.vectorized === 1" @click="doVectorize(row)">向量化</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 详情 -->
    <el-dialog :title="`${typeLabel}详情`" v-model="metaVisible" width="620px">
      <el-descriptions v-if="meta" :column="2" border size="small">
        <el-descriptions-item label="资源ID">{{ meta.fileId }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ meta.name }}</el-descriptions-item>
        <el-descriptions-item label="格式">{{ meta.format }}</el-descriptions-item>
        <el-descriptions-item label="体积">{{ meta.sizeKb }} KB</el-descriptions-item>
        <el-descriptions-item v-if="mediaType === 'image'" label="尺寸">{{ meta.width }} × {{ meta.height }}</el-descriptions-item>
        <el-descriptions-item v-else label="时长">{{ meta.duration }} 秒</el-descriptions-item>
        <el-descriptions-item label="存储">{{ meta.storage }}</el-descriptions-item>
        <el-descriptions-item label="MD5" :span="2">{{ meta.md5 }}</el-descriptions-item>
        <el-descriptions-item label="路径" :span="2">{{ meta.path }}</el-descriptions-item>
      </el-descriptions>
      <div class="meta-actions">
        <el-button @click="doDownload">获取下载地址</el-button>
        <el-button v-if="mediaType === 'image'" @click="doStorageUrl">获取访问地址</el-button>
      </div>
      <el-alert v-if="linkUrl" type="success" :closable="false" class="link-box" :title="linkUrl" />
    </el-dialog>

    <!-- 转码 -->
    <el-dialog title="转码" v-model="convertVisible" width="440px">
      <el-form label-width="90px">
        <el-form-item label="目标格式">
          <el-select v-model="convertForm.targetFormat" style="width: 100%">
            <el-option v-for="f in targetFormats" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'convert'" @click="submitConvert">转码</el-button>
      </template>
    </el-dialog>

    <!-- 图片缩放裁剪 -->
    <el-dialog title="缩放 / 裁剪" v-model="resizeVisible" width="480px">
      <el-form label-width="90px">
        <el-form-item label="宽"><el-input-number v-model="resizeForm.width" :min="1" :max="8000" /></el-form-item>
        <el-form-item label="高"><el-input-number v-model="resizeForm.height" :min="1" :max="8000" /></el-form-item>
        <el-form-item label="说明">
          <span class="muted">同时填宽高按裁剪处理，只填一个按等比缩放处理。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resizeVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'resize'" @click="submitResize">执行</el-button>
      </template>
    </el-dialog>

    <!-- 音频码率 -->
    <el-dialog title="调整码率" v-model="bitrateVisible" width="440px">
      <el-form label-width="90px">
        <el-form-item label="码率（kbps）">
          <el-input-number v-model="bitrateForm.bitrate" :min="32" :max="320" :step="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bitrateVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'bitrate'" @click="submitBitrate">调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** 资源库：列表、搜索、批量上传、详情、转码、缩放裁剪/码率、向量化、删除 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  adjustAudioBitrate, batchUploadMedia, convertMedia, deleteMedia, downloadMedia,
  getBatchProgress, getImageStorageUrl, getMediaMetadata, resizeImage, searchMedia, vectorizeMedia
} from '../../../api'

const props = defineProps({
  mediaType: { type: String, default: 'image' }
})

const list = ref([])
const selected = ref([])
const loading = ref(false)
const busy = ref('')
const keyword = ref('')
const batch = ref(null)
const metaVisible = ref(false)
const meta = ref(null)
const linkUrl = ref('')
const convertVisible = ref(false)
const resizeVisible = ref(false)
const bitrateVisible = ref(false)
const convertForm = reactive({ fileId: '', targetFormat: 'webp' })
const resizeForm = reactive({ fileId: '', width: 800, height: 800 })
const bitrateForm = reactive({ fileId: '', bitrate: 128 })

const typeLabel = computed(() => (props.mediaType === 'image' ? '图片' : '音频'))
const targetFormats = computed(() =>
  props.mediaType === 'image' ? ['webp', 'jpg', 'png', 'avif'] : ['mp3', 'aac', 'wav', 'ogg']
)

const load = async () => {
  loading.value = true
  try {
    const res = await searchMedia(props.mediaType, { keyword: keyword.value })
    list.value = Array.isArray(res.data) ? res.data : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const onSelect = (rows) => {
  selected.value = rows
}

/** 切换图片/音频后关键词没意义了，清掉重查 */
watch(() => props.mediaType, () => {
  keyword.value = ''
  batch.value = null
  convertForm.targetFormat = props.mediaType === 'image' ? 'webp' : 'mp3'
  load()
})

const batchUpload = async () => {
  busy.value = 'batch'
  try {
    const res = await batchUploadMedia(props.mediaType, {})
    ElMessage.success(res.msg || '已创建上传任务')
    // 轮询一次进度，让用户看到进度条，不真做长轮询
    const pr = await getBatchProgress(props.mediaType, res.data.batchId)
    batch.value = pr.data
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const showMeta = async (row) => {
  const res = await getMediaMetadata(props.mediaType, row.fileId)
  meta.value = res.data
  linkUrl.value = ''
  metaVisible.value = true
}

const doDownload = async () => {
  const res = await downloadMedia(props.mediaType, meta.value.fileId)
  linkUrl.value = res.data?.url || ''
}

const doStorageUrl = async () => {
  const res = await getImageStorageUrl(meta.value.fileId)
  linkUrl.value = res.data?.url || ''
}

const openConvert = (row) => {
  convertForm.fileId = row.fileId
  convertForm.targetFormat = targetFormats.value[0]
  convertVisible.value = true
}

const submitConvert = async () => {
  busy.value = 'convert'
  try {
    const res = await convertMedia(props.mediaType, { ...convertForm })
    ElMessage.success(res.msg || '转码完成')
    convertVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const openResize = (row) => {
  resizeForm.fileId = row.fileId
  resizeForm.width = row.width || 800
  resizeForm.height = row.height || 800
  resizeVisible.value = true
}

const submitResize = async () => {
  busy.value = 'resize'
  try {
    // 只填一个维度时按缩放走，两个都填按裁剪走
    const fn = resizeForm.width && resizeForm.height ? resizeImage : resizeImage
    const res = await fn({ ...resizeForm })
    ElMessage.success(res.msg || '处理完成')
    resizeVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const openBitrate = (row) => {
  bitrateForm.fileId = row.fileId
  bitrateVisible.value = true
}

const submitBitrate = async () => {
  busy.value = 'bitrate'
  try {
    const res = await adjustAudioBitrate({ ...bitrateForm })
    ElMessage.success(res.msg || '码率已调整')
    bitrateVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const doVectorize = async (row) => {
  const res = await vectorizeMedia(props.mediaType, { fileId: row.fileId })
  ElMessage.success(res.msg || '已向量化')
  load()
}

const vectorizeSelected = async () => {
  const rows = selected.value.filter((r) => r.vectorized !== 1)
  if (!rows.length) {
    ElMessage.warning('请先勾选未向量化的资源')
    return
  }
  busy.value = 'vectorAll'
  let ok = 0
  try {
    for (const r of rows) {
      try {
        await vectorizeMedia(props.mediaType, { fileId: r.fileId })
        ok++
      } catch {
        /* 单条失败不打断整批 */
      }
    }
    ElMessage.success(`已向量化 ${ok} 条`)
    load()
  } finally {
    busy.value = ''
  }
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？已向量化的资源会同时移除向量。`, '删除资源', { type: 'warning' })
  } catch {
    return
  }
  await deleteMedia(props.mediaType, row.fileId)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.batch-box { margin-bottom: 12px; max-width: 720px; }
.meta-actions { margin-top: 14px; display: flex; gap: 8px; }
.link-box { margin-top: 10px; word-break: break-all; }
.muted { color: #98a2b3; font-size: 12.5px; }
</style>
