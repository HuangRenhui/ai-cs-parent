<template>
  <div>
    <p class="panel-desc">
      {{ mediaType === 'image'
        ? '图片专属：防盗链防止别人直接盗用你的图，OCR 把图里的文字提出来变成可检索内容，内容审核拦住违规图。'
        : '音频专属：波形用于人工核对片段，ASR 把录音转成文字后就能进知识库被检索，内容审核拦住违规内容。' }}
    </p>

    <el-tabs v-model="tab">
      <!-- 图片：防盗链 -->
      <el-tab-pane v-if="mediaType === 'image'" label="防盗链" name="hotlink">
        <div class="form-row">
          <el-input v-model="newDomain" placeholder="允许的域名，例如 shop.example.com" style="width: 280px" />
          <el-input v-model="newRemark" placeholder="备注（选填）" style="width: 180px" />
          <el-button type="primary" :loading="busy === 'addDomain'" @click="addDomain">加入白名单</el-button>
        </div>

        <el-table :data="whitelist" stripe v-loading="busy === 'list'" empty-text="白名单为空，当前所有域名都可直接引用" class="mini-table" table-layout="fixed">
          <el-table-column prop="domain" label="域名" min-width="200" />
          <el-table-column prop="remark" label="备注" min-width="140" />
          <el-table-column prop="createTime" label="加入时间" min-width="150" />
          <el-table-column label="操作" min-width="100" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="danger" @click="removeDomain(row)">移除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <h6 class="sub-title">签名与临时链接</h6>
        <div class="form-row">
          <el-input v-model="signFileId" placeholder="资源ID，例如 img-2001" style="width: 220px" />
          <el-button :loading="busy === 'signed'" @click="genSignedUrl">生成签名链接</el-button>
          <el-button :loading="busy === 'token'" @click="genToken">生成访问令牌</el-button>
        </div>
        <el-alert v-if="signResult" type="success" :closable="false" class="mini-table">
          <template #title>{{ signResult.title }}</template>
          <template #default>{{ signResult.value }}</template>
        </el-alert>
      </el-tab-pane>

      <!-- 音频：波形 -->
      <el-tab-pane v-if="mediaType === 'audio'" label="波形与时长" name="waveform">
        <div class="form-row">
          <el-input v-model="waveFileId" placeholder="资源ID，例如 aud-3001" style="width: 220px" />
          <el-button type="primary" :loading="busy === 'wave'" @click="loadWave">加载波形</el-button>
          <el-button :loading="busy === 'duration'" @click="loadDuration">读取时长</el-button>
        </div>
        <div v-if="wave" class="wave-box">
          <div class="wave-head">
            <span>{{ waveFileId }}</span>
            <span class="muted">时长 {{ wave.duration }} 秒</span>
          </div>
          <div class="wave-bars">
            <span v-for="(v, i) in wave.peaks" :key="i" class="bar" :style="{ height: Math.max(4, v * 64) + 'px' }" />
          </div>
          <p class="muted">点击柱子可定位播放位置（演示仅展示波形，不接播放器）。</p>
        </div>
        <el-descriptions v-if="duration" :column="3" border size="small" class="mini-table">
          <el-descriptions-item label="时长">{{ duration.duration }} 秒</el-descriptions-item>
          <el-descriptions-item label="格式">{{ duration.format }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- 识别 -->
      <el-tab-pane :label="mediaType === 'image' ? 'OCR 识别' : '语音转写'" name="recognize">
        <div class="form-row">
          <el-input v-model="recFileId" placeholder="资源ID" style="width: 220px" />
          <el-button type="primary" :loading="busy === 'recognize'" @click="recognize">
            {{ mediaType === 'image' ? '开始 OCR' : '开始转写' }}
          </el-button>
        </div>
        <template v-if="recResult">
          <el-alert type="success" :closable="false" class="mini-table">
            <template #title>{{ recResult.text }}</template>
            <template #default>置信度 {{ recResult.confidence }}</template>
          </el-alert>
          <el-table :data="recResult.blocks || recResult.segments || []" stripe class="mini-table" table-layout="fixed">
            <el-table-column :label="mediaType === 'image' ? '文本块' : '片段'" min-width="300">
              <template #default="{ row }">
                <span v-if="mediaType === 'image'">{{ row.text }}</span>
                <span v-else>{{ row.start }} - {{ row.end }} 秒：{{ row.text }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="mediaType === 'image'" label="位置" min-width="180">
              <template #default="{ row }">{{ (row.box || []).join(', ') }}</template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <!-- 内容审核 -->
      <el-tab-pane label="内容审核" name="moderate">
        <div class="form-row">
          <el-input v-model="modFileId" placeholder="资源ID" style="width: 220px" />
          <el-button type="primary" :loading="busy === 'moderate'" @click="moderate">送审</el-button>
        </div>
        <template v-if="modResult">
          <el-alert :type="modResult.conclusion === 'pass' ? 'success' : 'warning'" :closable="false" class="mini-table">
            <template #title>
              结论：{{ modResult.conclusion === 'pass' ? '通过' : '需人工复核' }}　风险等级：{{ modResult.riskLevel }}
            </template>
          </el-alert>
          <el-table :data="modResult.categories || []" stripe class="mini-table" table-layout="fixed">
            <el-table-column prop="name" label="违规类型" min-width="160" />
            <el-table-column label="风险分" min-width="200">
              <template #default="{ row }">
                <el-progress :percentage="Math.round(row.risk * 100)" :stroke-width="6"
                  :color="row.risk > 0.5 ? '#f04438' : row.risk > 0.1 ? '#f79009' : '#12b76a'" />
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/** 资源类型专属能力：图片防盗链 / 音频波形，以及通用的识别与内容审核 */
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHotlinkWhitelist, asrAudio, createHotlinkToken, createSignedUrl, getAudioDuration,
  getAudioWaveform, listHotlinkWhitelist, moderateMedia, ocrImage, removeHotlinkWhitelist
} from '../../../api'

const props = defineProps({
  mediaType: { type: String, default: 'image' }
})

const tab = ref('hotlink')
const busy = ref('')
// 防盗链
const whitelist = ref([])
const newDomain = ref('')
const newRemark = ref('')
const signFileId = ref('img-2001')
const signResult = ref(null)
// 波形
const waveFileId = ref('aud-3001')
const wave = ref(null)
const duration = ref(null)
// 识别与审核
const recFileId = ref('img-2001')
const recResult = ref(null)
const modFileId = ref('img-2001')
const modResult = ref(null)

/** 切换资源类型时，标签与默认资源ID都要跟着变 */
watch(() => props.mediaType, (t) => {
  tab.value = t === 'image' ? 'hotlink' : 'waveform'
  const id = t === 'image' ? 'img-2001' : 'aud-3001'
  signFileId.value = id
  recFileId.value = id
  modFileId.value = id
  waveFileId.value = id
  recResult.value = null
  modResult.value = null
  signResult.value = null
  wave.value = null
  duration.value = null
  if (t === 'image') loadWhitelist()
})

const loadWhitelist = async () => {
  busy.value = 'list'
  try {
    const res = await listHotlinkWhitelist()
    whitelist.value = Array.isArray(res.data) ? res.data : []
  } catch {
    whitelist.value = []
  } finally {
    busy.value = ''
  }
}

const addDomain = async () => {
  if (!newDomain.value.trim()) {
    ElMessage.warning('请填写域名')
    return
  }
  busy.value = 'addDomain'
  try {
    const res = await addHotlinkWhitelist({ domain: newDomain.value.trim(), remark: newRemark.value.trim() })
    ElMessage.success(res.msg || '已加入')
    newDomain.value = ''
    newRemark.value = ''
    loadWhitelist()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const removeDomain = async (row) => {
  try {
    await ElMessageBox.confirm(`移除「${row.domain}」后，该域名将无法直接引用你的图片。确认移除？`, '移出白名单', { type: 'warning' })
  } catch {
    return
  }
  await removeHotlinkWhitelist({ domain: row.domain })
  ElMessage.success('已移除')
  loadWhitelist()
}

const genSignedUrl = async () => {
  busy.value = 'signed'
  try {
    const res = await createSignedUrl({ fileId: signFileId.value })
    signResult.value = { title: '签名链接（1 小时有效）', value: res.data?.url }
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const genToken = async () => {
  busy.value = 'token'
  try {
    const res = await createHotlinkToken({})
    signResult.value = { title: '访问令牌（2 小时有效）', value: res.data?.token }
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const loadWave = async () => {
  busy.value = 'wave'
  try {
    const res = await getAudioWaveform(waveFileId.value, {})
    wave.value = res.data
  } catch {
    wave.value = null
  } finally {
    busy.value = ''
  }
}

const loadDuration = async () => {
  busy.value = 'duration'
  try {
    const res = await getAudioDuration({ fileId: waveFileId.value })
    duration.value = res.data
  } catch {
    duration.value = null
  } finally {
    busy.value = ''
  }
}

const recognize = async () => {
  busy.value = 'recognize'
  try {
    const res = props.mediaType === 'image'
      ? await ocrImage({ fileId: recFileId.value })
      : await asrAudio({ fileId: recFileId.value })
    recResult.value = res.data
    ElMessage.success(res.msg || '识别完成')
  } catch {
    recResult.value = null
  } finally {
    busy.value = ''
  }
}

const moderate = async () => {
  busy.value = 'moderate'
  try {
    const res = await moderateMedia(props.mediaType, { fileId: modFileId.value })
    modResult.value = res.data
  } catch {
    modResult.value = null
  } finally {
    busy.value = ''
  }
}

onMounted(() => {
  if (props.mediaType === 'image') loadWhitelist()
})
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.mini-table { margin: 10px 0; max-width: 820px; }
.form-row { display: flex; gap: 10px; align-items: center; margin: 10px 0; flex-wrap: wrap; }
.sub-title { margin: 20px 0 8px; font-size: 13.5px; color: #1c2b4a; }
.wave-box {
  padding: 14px 16px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  max-width: 760px;
  margin-top: 10px;
}
.wave-head {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  color: #1c2b4a;
  margin-bottom: 10px;
}
/* 演示波形：用一排定高柱子近似，不做真实音频解码 */
.wave-bars {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 70px;
  padding: 0 2px;
  background: #f7f9fc;
  border-radius: 6px;
}
.bar {
  flex: 1;
  min-width: 2px;
  background: linear-gradient(180deg, #6ea4ff, #2f6bff);
  border-radius: 2px;
}
.muted { color: #98a2b3; font-size: 12.5px; margin: 6px 0 0; }
</style>
