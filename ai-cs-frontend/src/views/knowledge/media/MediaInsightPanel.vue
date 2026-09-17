<template>
  <div>
    <p class="panel-desc">
      访问统计看哪些资源真的被用到（长期没人看的可以考虑下架）；
      智能标签让资源能被语义检索命中，人工标签则是补充业务口径。
    </p>

    <el-tabs v-model="tab">
      <el-tab-pane label="访问统计" name="stats">
        <div class="metric-row" v-if="summary">
          <div class="metric"><span class="m-label">总浏览</span><span class="m-value">{{ summary.totalViews }}</span></div>
          <div class="metric"><span class="m-label">总下载</span><span class="m-value">{{ summary.totalDownloads }}</span></div>
          <div class="metric"><span class="m-label">热门资源</span><span class="m-value">{{ summary.hotCount }}</span></div>
          <div class="metric"><span class="m-label">平均加载</span><span class="m-value">{{ summary.avgLoadMs }} ms</span></div>
        </div>

        <div class="page-toolbar is-actions-only">
          <div class="table-actions">
            <el-button type="primary" :loading="busy === 'hot'" @click="loadHot">热门排行</el-button>
            <el-button :loading="busy === 'summary'" @click="loadSummary">汇总</el-button>
          </div>
        </div>

        <el-table :data="hot" stripe v-loading="busy === 'hot'" empty-text="暂无统计数据" class="mini-table" table-layout="fixed">
          <el-table-column prop="name" label="资源" min-width="220" show-overflow-tooltip />
          <el-table-column prop="views" label="浏览" min-width="100" />
          <el-table-column prop="downloads" label="下载" min-width="100" />
          <el-table-column label="趋势" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.trend === 'up' ? 'success' : row.trend === 'down' ? 'danger' : 'info'" size="small" effect="plain">
                {{ row.trend === 'up' ? '上升' : row.trend === 'down' ? '下降' : '持平' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="120" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="showDetail(row)">明细</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="智能标签" name="tags">
        <div class="form-row">
          <el-input v-model="tagFileId" placeholder="资源ID" style="width: 200px" />
          <el-button type="primary" :loading="busy === 'taglist'" @click="loadTags">查看标签</el-button>
          <el-button :loading="busy === 'auto'" @click="autoTag">自动打标</el-button>
          <el-button :loading="busy === 'suggest'" @click="suggest">推荐标签</el-button>
        </div>

        <div class="tag-box">
          <span class="tag-label">当前标签</span>
          <el-tag v-for="t in tags" :key="t" closable @close="removeTag(t)" size="small">{{ t }}</el-tag>
          <span v-if="!tags.length" class="muted">暂无标签</span>
        </div>

        <div class="form-row">
          <el-input v-model="newTags" placeholder="输入标签，逗号分隔，例如：商品,主图" style="width: 320px" />
          <el-button :loading="busy === 'save'" @click="saveTags">保存标签</el-button>
        </div>

        <div v-if="suggestions.length" class="suggest-box">
          <span class="tag-label">推荐</span>
          <el-tag v-for="s in suggestions" :key="s.tag" size="small" effect="plain" class="suggest-tag" @click="adoptTag(s.tag)">
            {{ s.tag }} <span class="score">{{ s.score }}</span>
          </el-tag>
        </div>

        <h6 class="sub-title">按标签找资源</h6>
        <div class="form-row">
          <el-input v-model="tagKeyword" placeholder="例如 售后" style="width: 200px" />
          <el-button :loading="busy === 'searchtag'" @click="searchByTag">查找</el-button>
          <el-button :loading="busy === 'predefined'" @click="loadPredefined">预置标签</el-button>
        </div>
        <div class="tag-box">
          <el-tag v-for="t in predefined" :key="t" size="small" effect="plain">{{ t }}</el-tag>
        </div>
        <el-table v-if="tagHits.length" :data="tagHits" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="fileId" label="资源ID" min-width="110" />
          <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
          <el-table-column label="标签" min-width="180">
            <template #default="{ row }">
              <el-tag v-for="t in row.tags" :key="t" size="small" effect="plain" style="margin-right: 4px">{{ t }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="访问明细" v-model="detailVisible" width="640px">
      <el-descriptions v-if="detail" :column="3" border size="small">
        <el-descriptions-item label="资源">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="浏览">{{ detail.views }}</el-descriptions-item>
        <el-descriptions-item label="下载">{{ detail.downloads }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail?.daily || []" stripe class="mini-table" table-layout="fixed">
        <el-table-column prop="date" label="日期" min-width="130" />
        <el-table-column prop="views" label="浏览" min-width="90" />
        <el-table-column label="趋势" min-width="220">
          <template #default="{ row }">
            <el-progress :percentage="Math.min(100, Math.round((row.views / 320) * 100))" :show-text="false" :stroke-width="6" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
/** 访问统计与智能标签 */
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  autoTagMedia, deleteMediaTag, getHotMedia, getMediaStats, getMediaStatsSummary,
  listMediaTags, listPredefinedTags, saveMediaTags, searchMediaTags, suggestTags
} from '../../../api'

const props = defineProps({
  mediaType: { type: String, default: 'image' }
})

const tab = ref('stats')
const busy = ref('')
const summary = ref(null)
const hot = ref([])
const detail = ref(null)
const detailVisible = ref(false)
const tagFileId = ref('img-2001')
const tags = ref([])
const newTags = ref('')
const suggestions = ref([])
const tagKeyword = ref('')
const tagHits = ref([])
const predefined = ref([])

watch(() => props.mediaType, () => {
  tagFileId.value = props.mediaType === 'image' ? 'img-2001' : 'aud-3001'
  summary.value = null
  hot.value = []
  tags.value = []
  suggestions.value = []
  tagHits.value = []
})

const loadSummary = async () => {
  busy.value = 'summary'
  try {
    const res = await getMediaStatsSummary(props.mediaType)
    summary.value = res.data
  } catch {
    summary.value = null
  } finally {
    busy.value = ''
  }
}

const loadHot = async () => {
  busy.value = 'hot'
  try {
    const res = await getHotMedia(props.mediaType)
    hot.value = Array.isArray(res.data) ? res.data : []
  } catch {
    hot.value = []
  } finally {
    busy.value = ''
  }
}

const showDetail = async (row) => {
  const res = await getMediaStats(props.mediaType, row.fileId)
  detail.value = res.data
  detailVisible.value = true
}

const loadTags = async () => {
  busy.value = 'taglist'
  try {
    const res = await listMediaTags(props.mediaType, tagFileId.value)
    tags.value = Array.isArray(res.data) ? res.data : []
  } catch {
    tags.value = []
  } finally {
    busy.value = ''
  }
}

const autoTag = async () => {
  busy.value = 'auto'
  try {
    const res = await autoTagMedia(props.mediaType, { fileId: tagFileId.value })
    tags.value = Array.from(new Set([...tags.value, ...(res.data?.tags || [])]))
    ElMessage.success(res.msg || '已自动打标')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const suggest = async () => {
  busy.value = 'suggest'
  try {
    const res = await suggestTags(props.mediaType, tagFileId.value)
    suggestions.value = Array.isArray(res.data) ? res.data : []
    if (!suggestions.value.length) ElMessage.info('暂无推荐标签')
  } catch {
    suggestions.value = []
  } finally {
    busy.value = ''
  }
}

const adoptTag = (tag) => {
  if (!tags.value.includes(tag)) tags.value.push(tag)
}

const removeTag = async (tag) => {
  tags.value = tags.value.filter((t) => t !== tag)
  await deleteMediaTag(props.mediaType, tagFileId.value, { tag })
}

const saveTags = async () => {
  busy.value = 'save'
  try {
    const merged = Array.from(new Set([
      ...tags.value,
      ...newTags.value.split(',').map((s) => s.trim()).filter(Boolean)
    ]))
    await saveMediaTags(props.mediaType, tagFileId.value, { tags: merged })
    tags.value = merged
    newTags.value = ''
    ElMessage.success('标签已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const searchByTag = async () => {
  busy.value = 'searchtag'
  try {
    const res = await searchMediaTags(props.mediaType, { tag: tagKeyword.value })
    tagHits.value = Array.isArray(res.data) ? res.data : []
  } catch {
    tagHits.value = []
  } finally {
    busy.value = ''
  }
}

const loadPredefined = async () => {
  busy.value = 'predefined'
  try {
    const res = await listPredefinedTags()
    predefined.value = Array.isArray(res.data) ? res.data : []
  } catch {
    predefined.value = []
  } finally {
    busy.value = ''
  }
}

onMounted(() => {
  loadSummary()
  loadHot()
  loadPredefined()
})
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.mini-table { margin: 10px 0; max-width: 880px; }
.metric-row { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 16px;
  border-radius: 8px;
  background: #f7f9fc;
  min-width: 110px;
}
.m-label { font-size: 12px; color: #98a2b3; }
.m-value { font-size: 15px; font-weight: 700; color: #1c2b4a; }
.form-row { display: flex; gap: 10px; align-items: center; margin: 10px 0; flex-wrap: wrap; }
.tag-box {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin: 8px 0;
  min-height: 26px;
}
.tag-label { font-size: 12.5px; color: #667085; margin-right: 4px; }
.suggest-box { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin: 8px 0; }
.suggest-tag { cursor: pointer; }
.score { color: #98a2b3; font-size: 11px; }
.sub-title { margin: 18px 0 8px; font-size: 13.5px; color: #1c2b4a; }
.muted { color: #98a2b3; font-size: 12.5px; }
</style>
