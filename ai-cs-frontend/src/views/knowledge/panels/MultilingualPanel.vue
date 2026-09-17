<template>
  <div>
    <p class="panel-desc">
      同一件事在不同语言里有不同说法（退款 / refund / 返金）。建立跨语言等价关系后，
      客户用日文问也能命中中文知识，不用把知识库翻译成多份。
    </p>

    <div class="lang-row" v-loading="busy === 'dist' && !distribution.length">
      <div v-for="l in distribution" :key="l.language" class="lang-card">
        <span class="lang-code">{{ l.language }}</span>
        <span class="lang-name">{{ l.name }}</span>
        <span class="lang-count">{{ l.count }} 条</span>
        <el-progress :percentage="l.ratio" :show-text="false" :stroke-width="5" />
        <span class="lang-ratio">{{ l.ratio }}%</span>
      </div>
      <el-button :loading="busy === 'dist'" @click="loadDistribution">刷新分布</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="等价词查询" name="equiv">
        <p class="muted">输入中文词，看它在其它语言里的对应说法与置信度。</p>
        <div class="form-row">
          <el-input v-model="term" placeholder="例如 退款" style="width: 200px" />
          <el-button type="primary" :loading="busy === 'equiv'" @click="loadEquivalents">查询等价词</el-button>
        </div>
        <el-table v-if="equivalents.length" :data="equivalents" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="language" label="语言" min-width="90" />
          <el-table-column prop="term" label="等价说法" min-width="160" />
          <el-table-column prop="confidence" label="置信度" min-width="100" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="跨语言关系" name="relations">
        <p class="muted">已建立的跨语言实体关联，可直接用于跨语言检索。</p>
        <el-table :data="relations" stripe v-loading="busy === 'rel'" class="mini-table" table-layout="fixed">
          <el-table-column label="源实体" min-width="180">
            <template #default="{ row }">{{ row.source }} <span class="muted">({{ row.sourceLang }})</span></template>
          </el-table-column>
          <el-table-column label="目标实体" min-width="180">
            <template #default="{ row }">{{ row.target }} <span class="muted">({{ row.targetLang }})</span></template>
          </el-table-column>
          <el-table-column prop="relType" label="关系" min-width="90" />
          <el-table-column prop="confidence" label="置信度" min-width="90" />
        </el-table>
        <el-button :loading="busy === 'rel'" @click="loadRelations">加载关系</el-button>
      </el-tab-pane>

      <el-tab-pane label="批量操作" name="batch">
        <div class="op-card">
          <h6>建立跨语言链接</h6>
          <p class="muted">把同义词在图中连起来，之后两个词都能召回同一批知识。</p>
          <el-button type="primary" :loading="busy === 'link'" @click="doLink">建立链接</el-button>
        </div>
        <div class="op-card">
          <h6>融合多语言实体</h6>
          <p class="muted">词义完全一致时直接合并为一个实体，图中只保留一份。</p>
          <el-button :loading="busy === 'fuse'" @click="doFuse">融合实体</el-button>
        </div>
        <div class="op-card">
          <h6>自动标注语种</h6>
          <p class="muted">对未标注语种的节点批量识别，标注后才能参与跨语言检索。</p>
          <el-button :loading="busy === 'tag'" @click="doTag">开始标注</el-button>
        </div>
        <div class="op-card">
          <h6>保存别名</h6>
          <p class="muted">同语言内的别称（如「退款」/「退货退款」）也走同一套别名表。</p>
          <el-input v-model="aliasText" placeholder="逗号分隔，例如 退款,退货退款,申请退款" class="alias-input" />
          <el-button :loading="busy === 'alias'" @click="doAliases">保存</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/** 多语言融合：语言分布、等价词、跨语言关系与批量操作 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fuseMultilingual, getCrossLingualRelations, getLanguageDistribution,
  getMultilingualEquivalents, linkMultilingual, saveAliases, tagLanguage
} from '../../../api'

const tab = ref('equiv')
const busy = ref('')
const distribution = ref([])
const equivalents = ref([])
const relations = ref([])
const term = ref('退款')
const aliasText = ref('退款,退货退款,申请退款')

const loadDistribution = async () => {
  busy.value = 'dist'
  try {
    const res = await getLanguageDistribution()
    distribution.value = Array.isArray(res.data) ? res.data : []
  } catch {
    distribution.value = []
  } finally {
    busy.value = ''
  }
}

const loadEquivalents = async () => {
  busy.value = 'equiv'
  try {
    const res = await getMultilingualEquivalents({ term: term.value })
    equivalents.value = Array.isArray(res.data) ? res.data : []
  } catch {
    equivalents.value = []
  } finally {
    busy.value = ''
  }
}

const loadRelations = async () => {
  busy.value = 'rel'
  try {
    const res = await getCrossLingualRelations()
    relations.value = Array.isArray(res.data) ? res.data : []
  } catch {
    relations.value = []
  } finally {
    busy.value = ''
  }
}

/** 四个批量动作结构一致，抽出来避免重复 */
const runOp = async (key, fn, payload) => {
  busy.value = key
  try {
    const res = await fn(payload)
    ElMessage.success(res.msg || '操作完成')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const doLink = () => runOp('link', linkMultilingual, {})
const doFuse = () => runOp('fuse', fuseMultilingual, {})
const doTag = () => runOp('tag', tagLanguage, {})
const doAliases = () => runOp('alias', saveAliases, {
  aliases: aliasText.value.split(',').map((s) => s.trim()).filter(Boolean)
})

onMounted(() => {
  loadDistribution()
  loadEquivalents()
  loadRelations()
})
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.lang-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 8px;
}
.lang-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 150px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f7f9fc;
}
.lang-code {
  font-size: 12px;
  font-weight: 700;
  color: #2f6bff;
  text-transform: uppercase;
}
.lang-name { font-size: 13px; color: #1c2b4a; font-weight: 600; }
.lang-count { font-size: 12px; color: #667085; }
.lang-ratio { font-size: 12px; color: #98a2b3; }
.mini-table { margin: 10px 0; max-width: 780px; }
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
}
.op-card {
  padding: 14px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  margin-bottom: 10px;
  max-width: 620px;
}
.op-card h6 { margin: 0 0 6px; font-size: 13.5px; color: #1c2b4a; }
.alias-input { margin-bottom: 8px; }
.muted { color: #98a2b3; font-size: 12.5px; margin: 0 0 8px; }
</style>
