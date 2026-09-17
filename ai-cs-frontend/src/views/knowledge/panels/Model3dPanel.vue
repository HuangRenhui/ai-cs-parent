<template>
  <div>
    <p class="panel-desc">
      3D 模型入库后可支撑商品 360° 展示、AR 摆放与结构类问答。导入时自动统计顶点/面数，
      面数过高的模型会建议生成低模（LOD），否则移动端加载会卡。
    </p>

    <div class="page-toolbar is-actions-only">
      <div class="table-actions">
        <el-button type="primary" :loading="importing" @click="openImport">导入模型</el-button>
        <el-button :loading="importing" @click="batchImport">批量导入</el-button>
        <el-input v-model="keyword" placeholder="按名称或标签搜索" clearable style="width: 220px" @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无 3D 模型" table-layout="fixed" max-height="420">
      <el-table-column prop="knowledgeId" label="模型ID" min-width="110" />
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="格式" min-width="76">
        <template #default="{ row }"><TypeTag :text="row.format" /></template>
      </el-table-column>
      <el-table-column label="体积" min-width="90">
        <template #default="{ row }">{{ (row.size / 1024 / 1024).toFixed(1) }} MB</template>
      </el-table-column>
      <el-table-column label="顶点 / 面数" min-width="140">
        <template #default="{ row }">{{ row.vertices }} / {{ row.faces }}</template>
      </el-table-column>
      <el-table-column label="标签" min-width="130">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags" :key="t" size="small" effect="plain" style="margin-right: 4px">{{ t }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="来源" min-width="100" show-overflow-tooltip />
      <el-table-column prop="createTime" label="入库时间" min-width="150" />
      <el-table-column label="操作" width="228" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="showReport(row)">检测报告</el-button>
            <el-button link type="primary" @click="showSimilar(row)">相似</el-button>
            <el-button link type="primary" @click="showAdvice(row)">转换建议</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <h5 class="sub-title">模型问答</h5>
    <div class="ask-bar">
      <el-input v-model="question" placeholder="例如：这个模型有哪些可拆解部件？" clearable @keyup.enter="ask" />
      <el-button type="primary" :loading="asking" @click="ask">提问</el-button>
    </div>
    <el-alert v-if="answer" type="success" :closable="false" class="answer-box">
      <template #title>{{ answer.answer }}</template>
      <template #default>
        <span class="muted">置信度 {{ answer.confidence }}　引用 {{ (answer.references || []).join('、') }}</span>
      </template>
    </el-alert>

    <!-- 对比 -->
    <el-dialog title="模型对比" v-model="compareVisible" width="640px">
      <el-table :data="compareRows" stripe empty-text="请先在列表勾选两个模型（演示固定对比前两个）" table-layout="fixed">
        <el-table-column prop="name" label="模型" min-width="150" />
        <el-table-column prop="vertices" label="顶点数" min-width="100" />
        <el-table-column prop="faces" label="面数" min-width="100" />
        <el-table-column prop="sizeKb" label="体积（KB）" min-width="110" />
      </el-table>
    </el-dialog>

    <!-- 通用结果 -->
    <el-dialog :title="resultTitle" v-model="resultVisible" width="680px">
      <div v-if="resultKind === 'report' && result">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模型">{{ result.name }}</el-descriptions-item>
          <el-descriptions-item label="格式">{{ result.format }}</el-descriptions-item>
          <el-descriptions-item label="顶点数">{{ result.vertices }}</el-descriptions-item>
          <el-descriptions-item label="面数">{{ result.faces }}</el-descriptions-item>
          <el-descriptions-item label="体积">{{ result.sizeKb }} KB</el-descriptions-item>
          <el-descriptions-item label="LOD 层级">{{ result.lodLevels }}</el-descriptions-item>
          <el-descriptions-item label="贴图数">{{ result.textureCount }}</el-descriptions-item>
          <el-descriptions-item label="动画数">{{ result.animationCount }}</el-descriptions-item>
        </el-descriptions>
        <h5 class="sub-title">优化建议</h5>
        <ul class="advice-list">
          <li v-for="(t, i) in result.issues" :key="i">{{ t }}</li>
        </ul>
      </div>

      <el-table v-else-if="resultKind === 'similar'" :data="result || []" stripe table-layout="fixed">
        <el-table-column prop="name" label="相似模型" min-width="160" />
        <el-table-column prop="format" label="格式" min-width="80" />
        <el-table-column label="相似度" min-width="100">
          <template #default="{ row }">{{ row.similarity }}</template>
        </el-table-column>
      </el-table>

      <div v-else-if="resultKind === 'advice' && result">
        <ul class="advice-list">
          <li v-for="(t, i) in result.advice" :key="i">{{ t }}</li>
        </ul>
        <p class="muted">预计优化后体积：{{ result.estimatedSizeKb }} KB</p>
      </div>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog title="导入 3D 模型" v-model="importVisible" width="520px">
      <el-form label-width="90px">
        <el-form-item label="模型文件" required>
          <el-upload drag :auto-upload="false" :limit="1" accept=".glb,.gltf,.obj,.fbx">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip><div class="el-upload__tip">支持 glb / gltf / obj / fbx</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="form.name" placeholder="便于检索识别的名称" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，例如：电子,音箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** 3D 模型：入库、检测、相似、对比、转换建议与结构问答 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, UploadFilled } from '@element-plus/icons-vue'
import {
  batchImport3dModel, compare3dModels, get3dConversionAdvice, get3dModelReport,
  import3dModel, list3dModels, qa3dModel, search3dModel, similar3dModels
} from '../../../api'

const list = ref([])
const loading = ref(false)
const importing = ref(false)
const asking = ref(false)
const keyword = ref('')
const question = ref('')
const answer = ref(null)
const importVisible = ref(false)
const resultVisible = ref(false)
const compareVisible = ref(false)
const resultKind = ref('report')
const resultTitle = ref('')
const result = ref(null)
const compareRows = ref([])
const form = reactive({ name: '', tags: '' })

const load = async () => {
  loading.value = true
  try {
    const res = keyword.value.trim()
      ? await search3dModel({ keyword: keyword.value.trim() })
      : await list3dModels()
    list.value = Array.isArray(res.data) ? res.data : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const openImport = () => {
  form.name = ''
  form.tags = ''
  importVisible.value = true
}

const submitImport = async () => {
  importing.value = true
  try {
    const res = await import3dModel({ name: form.name, tags: form.tags })
    ElMessage.success(res.msg || '导入成功')
    importVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    importing.value = false
  }
}

const batchImport = async () => {
  importing.value = true
  try {
    const res = await batchImport3dModel({})
    ElMessage.success(res.msg || '批量导入完成')
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    importing.value = false
  }
}

const openResult = (kind, title, data) => {
  resultKind.value = kind
  resultTitle.value = title
  result.value = data
  resultVisible.value = true
}

const showReport = async (row) => {
  const res = await get3dModelReport(row.knowledgeId)
  openResult('report', '模型检测报告', res.data)
}

const showSimilar = async (row) => {
  const res = await similar3dModels(row.knowledgeId)
  openResult('similar', `与「${row.name}」相似的模型`, res.data)
}

const showAdvice = async (row) => {
  const res = await get3dConversionAdvice(row.knowledgeId)
  openResult('advice', '转换优化建议', res.data)
}

const ask = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请先输入问题')
    return
  }
  asking.value = true
  try {
    const res = await qa3dModel({ question: question.value.trim() })
    answer.value = res.data
    compareRows.value = []
    // 顺带刷新一次对比数据，让「对比」弹窗有内容可看
    const cmp = await compare3dModels({})
    compareRows.value = cmp.data || []
  } catch {
    /* 拦截器已提示 */
  } finally {
    asking.value = false
  }
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
.sub-title {
  margin: 20px 0 10px;
  font-size: 13.5px;
  color: #1c2b4a;
}
.ask-bar {
  display: flex;
  gap: 10px;
  max-width: 720px;
}
.answer-box {
  margin-top: 12px;
  max-width: 720px;
}
.advice-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: #475467;
}
.muted {
  color: #98a2b3;
  font-size: 12.5px;
}
</style>
