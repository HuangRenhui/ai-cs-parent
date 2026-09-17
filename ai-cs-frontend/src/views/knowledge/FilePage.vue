<template>
  <div class="page-card">
    <p class="hint">
      知识库底层文件服务：文档、图片、压缩包的统一入口，支持预览、断点续传下载、批量打包与在线解压。
      解压后可以直接浏览包内文件并入库。
    </p>

    <SectionSwitch v-model="tab" :options="tabOptions" />

    <!-- 文件列表 -->
    <template v-if="tab === 'files'">
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <el-input v-model="keyword" placeholder="按文件名搜索" clearable style="width: 220px" @keyup.enter="load">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="load">查询</el-button>
          <el-button type="primary" :disabled="!selected.length" :loading="busy === 'batch'" @click="batchDownload">
            批量打包下载（{{ selected.length }}）
          </el-button>
        </div>
      </div>

      <el-table :data="list" stripe v-loading="loading" empty-text="暂无文件" table-layout="fixed" max-height="420" @selection-change="onSelect">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="fileId" label="文件ID" min-width="100" />
        <el-table-column prop="name" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="格式" min-width="80">
          <template #default="{ row }"><TypeTag :text="row.ext.toUpperCase()" /></template>
        </el-table-column>
        <el-table-column label="体积" min-width="100">
          <template #default="{ row }">{{ row.sizeKb >= 1024 ? (row.sizeKb / 1024).toFixed(1) + ' MB' : row.sizeKb + ' KB' }}</template>
        </el-table-column>
        <el-table-column label="存储" min-width="88">
          <template #default="{ row }"><TypeTag :text="row.storageType === 'oss' ? '对象存储' : '本地'" /></template>
        </el-table-column>
        <el-table-column prop="uploader" label="上传人" min-width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" min-width="150" />
        <el-table-column label="操作" width="296" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="showPreview(row)">预览</el-button>
              <el-button link type="primary" @click="showInfo(row)">详情</el-button>
              <el-button link type="primary" @click="doDownload(row)">下载</el-button>
              <el-button link type="primary" @click="showRange(row)">断点续传</el-button>
              <el-button v-if="row.ext === 'zip'" link type="warning" @click="openDecompress(row)">解压</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 解压 -->
    <template v-else>
      <div class="decompress-box">
        <h5>在线解压</h5>
        <p class="muted">上传压缩包或选择已入库的压缩文件，解压后可在下方浏览包内文件。</p>
        <el-form label-width="100px" style="max-width: 620px">
          <el-form-item label="压缩包">
            <el-upload drag :auto-upload="false" :limit="1" accept=".zip,.tar,.gz,.rar" style="width: 100%">
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽压缩包到此处，或<em>点击选择</em></div>
              <template #tip><div class="el-upload__tip">支持 zip / tar / gz / rar</div></template>
            </el-upload>
          </el-form-item>
          <el-form-item label="解压方式">
            <el-radio-group v-model="decompressMode">
              <el-radio value="upload">上传后解压</el-radio>
              <el-radio value="local">解压已入库文件</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="decompressMode === 'local'" label="选择文件">
            <el-select v-model="decompressFileId" style="width: 100%" placeholder="选择已入库的压缩包">
              <el-option v-for="f in zipFiles" :key="f.fileId" :label="f.name" :value="f.fileId" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy === 'decompress'" @click="doDecompress">开始解压</el-button>
          </el-form-item>
        </el-form>
      </div>

      <template v-if="entries.length">
        <h5 class="sub-title">包内文件（任务 {{ taskId }}）</h5>
        <el-table :data="entries" stripe class="mini-table" table-layout="fixed" max-height="320">
          <el-table-column prop="name" label="文件名" min-width="240" show-overflow-tooltip />
          <el-table-column label="格式" min-width="80">
            <template #default="{ row }"><TypeTag :text="row.ext.toUpperCase()" /></template>
          </el-table-column>
          <el-table-column label="体积" min-width="100">
            <template #default="{ row }">{{ row.sizeKb }} KB</template>
          </el-table-column>
          <el-table-column label="操作" min-width="100" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="previewEntry(row)">预览</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </template>

    <!-- 预览 -->
    <el-dialog title="文件预览" v-model="previewVisible" width="720px">
      <el-descriptions v-if="preview" :column="2" border size="small">
        <el-descriptions-item label="文件名">{{ preview.name }}</el-descriptions-item>
        <el-descriptions-item label="格式">{{ preview.ext }}</el-descriptions-item>
        <el-descriptions-item label="预览方式">{{ preview.previewType }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ preview.previewUrl }}</el-descriptions-item>
      </el-descriptions>
      <pre v-if="preview && preview.content" class="preview-content">{{ preview.content }}</pre>
      <p v-else class="muted">该格式不支持在线渲染，请下载后查看。</p>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog title="文件详情" v-model="infoVisible" width="620px">
      <el-descriptions v-if="info" :column="2" border size="small">
        <el-descriptions-item label="文件ID">{{ info.fileId }}</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ info.name }}</el-descriptions-item>
        <el-descriptions-item label="格式">{{ info.ext }}</el-descriptions-item>
        <el-descriptions-item label="体积">{{ info.sizeKb }} KB</el-descriptions-item>
        <el-descriptions-item label="存储类型">{{ info.storageType }}</el-descriptions-item>
        <el-descriptions-item label="上传人">{{ info.uploader }}</el-descriptions-item>
        <el-descriptions-item label="存储路径" :span="2">{{ info.path }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 断点续传 -->
    <el-dialog title="断点续传下载" v-model="rangeVisible" width="560px">
      <p class="dialog-lead">
        大文件下载中断后，可以用 Range 从断点继续，不用重新下整个文件。
      </p>
      <el-descriptions v-if="rangeInfo" :column="2" border size="small">
        <el-descriptions-item label="起始字节">{{ rangeInfo.rangeStart }}</el-descriptions-item>
        <el-descriptions-item label="结束字节">{{ rangeInfo.rangeEnd }}</el-descriptions-item>
        <el-descriptions-item label="本次大小">{{ (rangeInfo.contentLength / 1024).toFixed(0) }} KB</el-descriptions-item>
        <el-descriptions-item label="还有后续分片">{{ rangeInfo.hasMore ? '是' : '否' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="rangeVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** 文件管理：列表、预览、详情、断点续传、批量打包、在线解压 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, UploadFilled } from '@element-plus/icons-vue'
import SectionSwitch from '../../components/SectionSwitch.vue'
import {
  batchDownloadFiles, decompressFile, decompressLocal, downloadFile, downloadFileRange,
  getFileInfo, listDecompressContents, listFiles, previewDecompressed, previewFile
} from '../../api'

const tab = ref('files')
const tabOptions = [
  { value: 'files', label: '文件列表' },
  { value: 'decompress', label: '在线解压' }
]

const list = ref([])
const selected = ref([])
const loading = ref(false)
const busy = ref('')
const keyword = ref('')
const previewVisible = ref(false)
const infoVisible = ref(false)
const rangeVisible = ref(false)
const preview = ref(null)
const info = ref(null)
const rangeInfo = ref(null)
const decompressMode = ref('local')
const decompressFileId = ref('')
const entries = ref([])
const taskId = ref('')

const zipFiles = computed(() => list.value.filter((f) => f.ext === 'zip'))
const onSelect = (rows) => {
  selected.value = rows
}

const load = async () => {
  loading.value = true
  try {
    const res = await listFiles({ keyword: keyword.value })
    list.value = Array.isArray(res.data) ? res.data : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const showPreview = async (row) => {
  const res = await previewFile({ fileId: row.fileId })
  preview.value = res.data
  previewVisible.value = true
}

const showInfo = async (row) => {
  const res = await getFileInfo({ fileId: row.fileId })
  info.value = res.data
  infoVisible.value = true
}

/** 普通下载：拿地址后在新窗口打开，不在页面里渲染文件 */
const doDownload = async (row) => {
  const res = await downloadFile({ fileId: row.fileId })
  const url = res.data?.url
  if (url) window.open(url, '_blank')
  else ElMessage.warning('未取到下载地址')
}

const showRange = async (row) => {
  // Range 传第一段，模拟从 0 开始的分片下载
  const res = await downloadFileRange({ fileId: row.fileId }, 'bytes=0-1048575')
  rangeInfo.value = res.data
  rangeVisible.value = true
}

const batchDownload = async () => {
  busy.value = 'batch'
  try {
    const res = await batchDownloadFiles({ fileIds: selected.value.map((r) => r.fileId) })
    ElMessage.success(`${res.msg || '已打包'}，共 ${res.data?.fileCount} 个文件`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const doDecompress = async () => {
  busy.value = 'decompress'
  try {
    const res = decompressMode.value === 'local'
      ? await decompressLocal({ fileId: decompressFileId.value || (zipFiles.value[0] && zipFiles.value[0].fileId) })
      : await decompressFile({})
    taskId.value = res.data?.taskId || ''
    ElMessage.success(res.msg || '解压完成')
    const contents = await listDecompressContents({ taskId: taskId.value })
    entries.value = Array.isArray(contents.data) ? contents.data : []
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const previewEntry = async (row) => {
  const res = await previewDecompressed(taskId.value, { name: row.name })
  preview.value = { ...res.data, ext: row.ext }
  previewVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
.decompress-box {
  padding: 16px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  margin-bottom: 16px;
}
.decompress-box h5 { margin: 0 0 6px; font-size: 13.5px; color: #1c2b4a; }
.sub-title { margin: 0 0 10px; font-size: 13.5px; color: #1c2b4a; }
.mini-table { margin-bottom: 12px; }
.dialog-lead { margin: 0 0 14px; color: #667085; font-size: 13px; line-height: 1.6; }
.preview-content {
  margin: 14px 0 0;
  padding: 12px;
  background: #f7f9fc;
  border-radius: 8px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #1c2b4a;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}
.muted { color: #98a2b3; font-size: 12.5px; margin: 10px 0 0; }
</style>
