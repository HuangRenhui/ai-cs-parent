<template>
  <div class="page-card">
    <p class="hint">图片 / 音频 / 视频知识入库与检索，支持跨模态问答。</p>
    <!-- 顶栏切片已标明本页，不再重复「多模态知识」标题 -->
    <div class="page-toolbar is-actions-only">
      <div class="toolbar-actions">
        <TbBtn act="add" label="上传入库" @click="openDialog" />
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="metric-row">
      <div class="metric"><span class="metric-num">{{ stats.imageCount ?? 0 }}</span><span class="metric-label">图片</span></div>
      <div class="metric"><span class="metric-num">{{ stats.audioCount ?? 0 }}</span><span class="metric-label">音频</span></div>
      <div class="metric"><span class="metric-num">{{ stats.videoCount ?? 0 }}</span><span class="metric-label">视频</span></div>
      <div class="metric"><span class="metric-num">{{ stats.totalCount ?? 0 }}</span><span class="metric-label">合计</span></div>
    </div>

    <el-form inline>
      <el-form-item label="类型">
        <el-select v-model="keywordType" clearable placeholder="全部" style="width: 140px" @change="load">
          <el-option label="图片" value="image" />
          <el-option label="音频" value="audio" />
          <el-option label="视频" value="video" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="records" stripe v-loading="loading" empty-text="暂无多模态知识" table-layout="fixed" max-height="600">
      <el-table-column prop="knowledgeId" label="知识ID" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <TypeTag :text="TYPE_TEXT[row.mediaType] || row.mediaType || '-'" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createTime" label="入库时间" min-width="160" show-overflow-tooltip />
    </el-table>
    <TablePager v-model:page="page" v-model:size="size" :total="total" />

    <el-dialog title="上传入库" v-model="dialogVisible" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="知识类型">
          <el-radio-group v-model="form.mediaType" @change="onTypeChange">
            <el-radio-button label="image">图片</el-radio-button>
            <el-radio-button label="audio">音频</el-radio-button>
            <el-radio-button label="video">视频</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload
            ref="uploadRef"
            class="mm-upload"
            drag
            :auto-upload="false"
            :limit="1"
            :accept="ACCEPT[form.mediaType]"
            :on-change="onFileChange"
            :on-exceed="onFileExceed"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">{{ TIP_TEXT[form.mediaType] }}</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="一句话说明这条知识" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="这条知识讲什么，检索时会用到" />
        </el-form-item>
        <el-form-item label="分析结果">
          <el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="选填：图片 OCR / 音频转写 / 视频摘要" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="选填，逗号分隔" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="选填，逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">上传并入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  listMultimodalKnowledge, getMultimodalStatistics,
  uploadImageResource, uploadAudioResource, uploadVideoResource,
  indexMultimodalImage, indexMultimodalAudio, indexMultimodalVideo
} from '../api'
import TablePager from '../components/TablePager.vue'
import TbBtn from '../components/TbBtn.vue'
import TypeTag from '../components/TypeTag.vue'
import { useClientPager } from '../composables/useClientPager'

const TYPE_TEXT = { image: '图片', audio: '音频', video: '视频' }
/** 各类型可选文件与提示 */
const ACCEPT = { image: 'image/*', audio: 'audio/*', video: 'video/*' }
const TIP_TEXT = {
  image: '支持 png / jpg / webp，单文件上传',
  audio: '支持 mp3 / wav / m4a，单文件上传',
  video: '支持 mp4 / mov，单文件上传'
}
const UPLOADERS = { image: uploadImageResource, audio: uploadAudioResource, video: uploadVideoResource }
const INDEXERS = { image: indexMultimodalImage, audio: indexMultimodalAudio, video: indexMultimodalVideo }

const list = ref([])
const { page, size, total, records } = useClientPager(list)
const loading = ref(false)
const keywordType = ref('')
const stats = ref({})

const dialogVisible = ref(false)
const submitting = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const form = reactive({ mediaType: 'image', title: '', description: '', analysis: '', keywords: '', tags: '' })

const load = async () => {
  loading.value = true
  try {
    const [l, s] = await Promise.all([
      listMultimodalKnowledge({ mediaType: keywordType.value }),
      getMultimodalStatistics()
    ])
    const data = l.data
    list.value = Array.isArray(data) ? data : (data?.records || [])
    stats.value = s.data || {}
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const openDialog = () => {
  Object.assign(form, { mediaType: 'image', title: '', description: '', analysis: '', keywords: '', tags: '' })
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  dialogVisible.value = true
}

const onFileChange = (file) => {
  selectedFile.value = file.raw
}

const onFileExceed = () => {
  ElMessage.warning('一次只能上传一个文件，请先移除已选文件')
}

/** 切换类型时清空已选文件，避免图片上传框选了音频 */
const onTypeChange = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

const submit = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要上传的文件')
    return
  }
  if (!form.title.trim() || !form.description.trim()) {
    ElMessage.warning('请填写标题与描述')
    return
  }
  submitting.value = true
  try {
    // 1) 先传文件拿资源标识（图片/音频走后端已有上传接口，视频为占位接口）
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    const upRes = await UPLOADERS[form.mediaType](formData)
    const meta = upRes?.data || {}

    // 2) 再按类型登记入库
    await INDEXERS[form.mediaType]({
      resourceId: meta.fileId,
      resourcePath: meta.storagePath,
      title: form.title.trim(),
      description: form.description.trim(),
      analysis: form.analysis,
      keywords: form.keywords,
      tags: form.tags
    })

    ElMessage.success('入库成功')
    dialogVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.metric-row { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.metric {
  flex: 1 1 120px;
  min-width: 120px;
  padding: 12px 14px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-num { font-size: 20px; font-weight: 700; color: #1c2b4a; }
.metric-label { font-size: 12px; color: #6b7280; }
.mm-upload { width: 100%; }
</style>
