<template>
  <div class="page-card">
    <p class="hint">
      面向坐席与运营的 AI 辅助能力：识图、语音、工具链编排，以及实时话术辅助。
      这些能力也开放给机器人主链路调用，页面主要用于调试与单点验证。
    </p>
    <StubBanner description="坐席工作台尚未接入推荐回复/摘要/下一句；本页可调试识图、语音与工具链，不代表接待主链路已落地。" />

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <!-- 识图 -->
    <template v-if="section === 'vision'">
      <div class="op-card">
        <h6>图片识图问答</h6>
        <p class="muted">客户发来截图时，先把图里的文字与要素识别出来，再回答问题。</p>
        <div class="form-row">
          <el-input v-model="imageForm.question" placeholder="问题，例如：这张图里是什么订单？" />
          <el-button type="primary" :loading="busy === 'image'" @click="doChatImage">识图问答</el-button>
        </div>
        <el-alert v-if="imageResult" type="success" :closable="false" class="result-box">
          <template #title>{{ imageResult.answer }}</template>
          <template #default>
            识别类型：{{ imageResult.recognized?.type }}　置信度 {{ imageResult.confidence }}
            <div class="recognized">{{ imageResult.recognized?.text }}</div>
          </template>
        </el-alert>
      </div>
    </template>

    <!-- 语音 -->
    <template v-else-if="section === 'voice'">
      <div class="op-card">
        <h6>语音转文字（ASR）</h6>
        <p class="muted">把客户语音留言转成文字，转完即可进知识库检索。</p>
        <div class="form-row">
          <el-input v-model="voiceForm.audioUrl" placeholder="音频地址或资源ID" />
          <el-button type="primary" :loading="busy === 'stt'" @click="doStt">转写</el-button>
        </div>
        <el-alert v-if="sttResult" type="success" :closable="false" class="result-box">
          <template #title>{{ sttResult.text }}</template>
          <template #default>语种 {{ sttResult.language }}　时长 {{ sttResult.duration }} 秒　置信度 {{ sttResult.confidence }}</template>
        </el-alert>
      </div>

      <div class="op-card">
        <h6>文字转语音（TTS）</h6>
        <p class="muted">把回复合成为语音，用于电话外呼或语音播报场景。</p>
        <div class="form-row">
          <el-input v-model="voiceForm.text" placeholder="要合成的文本" />
          <el-select v-model="voiceForm.voice" style="width: 160px">
            <el-option label="女声（中文）" value="female_zh" />
            <el-option label="男声（中文）" value="male_zh" />
            <el-option label="女声（英文）" value="female_en" />
          </el-select>
          <el-button type="primary" :loading="busy === 'tts'" @click="doTts">合成</el-button>
        </div>
        <el-descriptions v-if="ttsResult" :column="3" border size="small" class="result-box">
          <el-descriptions-item label="音频地址">{{ ttsResult.audioUrl }}</el-descriptions-item>
          <el-descriptions-item label="格式">{{ ttsResult.format }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ ttsResult.duration }} 秒</el-descriptions-item>
        </el-descriptions>
      </div>
    </template>

    <!-- 工具链 -->
    <template v-else-if="section === 'tools'">
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <el-button type="primary" :loading="busy === 'chain'" :disabled="!chainPicked.length" @click="runChain">
            执行工具链（{{ chainPicked.length }}）
          </el-button>
          <el-button :loading="busy === 'clear'" @click="doClearCache">清空工具缓存</el-button>
        </div>
      </div>

      <el-table :data="tools" stripe v-loading="busy === 'tools'" empty-text="暂无可用工具"
        table-layout="fixed" max-height="320" @selection-change="onToolSelect">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="toolName" label="工具名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="风险" min-width="90">
          <template #default="{ row }"><TypeTag :text="riskText(row.risk)" /></template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="100" />
        <el-table-column label="启用" min-width="72">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template v-if="chainResult">
        <h5 class="sub-title">执行结果（按顺序串联）</h5>
        <el-table :data="chainResult.steps || []" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="step" label="步骤" min-width="70" />
          <el-table-column prop="tool" label="工具" min-width="150" />
          <el-table-column prop="output" label="输出" min-width="260" show-overflow-tooltip />
          <el-table-column prop="elapsedMs" label="耗时（毫秒）" min-width="110" />
        </el-table>
        <p class="muted">整链耗时 {{ chainResult.totalElapsedMs }} 毫秒</p>
      </template>
    </template>

    <!-- 坐席辅助 -->
    <template v-else-if="section === 'assist'">
      <div class="assist-grid">
        <div class="op-card">
          <h6>推荐回复</h6>
          <p class="muted">根据当前对话推荐可发送的候选话术。</p>
          <el-button type="primary" :loading="busy === 'recommend'" @click="doRecommend">生成推荐</el-button>
          <ul v-if="recommendations.length" class="assist-list">
            <li v-for="(r, i) in recommendations" :key="i">{{ r }}</li>
          </ul>
        </div>

        <div class="op-card">
          <h6>会话摘要</h6>
          <p class="muted">把长对话压缩成几句话，交接班时最有用。</p>
          <div class="form-row">
            <el-input v-model="assistForm.sessionId" placeholder="会话ID" style="width: 200px" />
            <el-button type="primary" :loading="busy === 'summary'" @click="doSummary">生成摘要</el-button>
          </div>
          <el-alert v-if="summary" type="success" :closable="false" class="result-box">
            <template #title>{{ summary.summary }}</template>
            <template #default>
              情绪：{{ summary.sentiment }}　需跟进：{{ summary.needFollowUp ? '是' : '否' }}
              <div class="tag-line">
                <el-tag v-for="k in summary.keywords" :key="k" size="small" effect="plain">{{ k }}</el-tag>
              </div>
            </template>
          </el-alert>
        </div>

        <div class="op-card">
          <h6>下一句建议</h6>
          <p class="muted">坐席打完一句后，给出自然的接话方向。</p>
          <el-button type="primary" :loading="busy === 'next'" @click="doNext">给建议</el-button>
          <ul v-if="nextSentences.length" class="assist-list">
            <li v-for="(s, i) in nextSentences" :key="i">{{ s }}</li>
          </ul>
        </div>

        <div class="op-card">
          <h6>流程步进</h6>
          <p class="muted">按对话所处的流程阶段决定下一步动作。</p>
          <div class="form-row">
            <el-input-number v-model="flowStep" :min="1" :max="5" />
            <el-button type="primary" :loading="busy === 'flow'" @click="doFlowStep">推进一步</el-button>
          </div>
          <el-descriptions v-if="flowResult" :column="2" border size="small" class="result-box">
            <el-descriptions-item label="当前节点">{{ flowResult.node }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ flowResult.elapsedMs }} 毫秒</el-descriptions-item>
            <el-descriptions-item label="可选动作" :span="2">{{ (flowResult.nextActions || []).join('、') }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <h5 class="sub-title">行业包内容速查</h5>
      <div class="form-row">
        <el-select v-model="packCode" style="width: 180px">
          <el-option label="电商" value="ecommerce" />
          <el-option label="金融" value="finance" />
        </el-select>
        <el-button :loading="busy === 'pack'" @click="loadPack">查看</el-button>
      </div>
      <el-descriptions v-if="pack" :column="1" border size="small" class="mini-table">
        <el-descriptions-item label="行业">{{ pack.name }}（{{ pack.code }}）</el-descriptions-item>
        <el-descriptions-item label="工具">{{ (pack.tools || []).join('、') }}</el-descriptions-item>
        <el-descriptions-item label="意图">{{ (pack.intents || []).join('、') }}</el-descriptions-item>
        <el-descriptions-item label="入口场景">{{ (pack.scenes || []).join('、') }}</el-descriptions-item>
      </el-descriptions>
    </template>
  </div>
</template>

<script setup>
/** AI 对话扩展：识图、语音、工具链、坐席辅助、流程步进与行业包速查 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SectionSwitch from '../components/SectionSwitch.vue'
import {
  aiFlowStep, assistNextSentence, assistRecommend, assistSummary, chatImage, clearToolCache,
  getIndustryPack, listAgentTools, runToolChain, speechToText, textToSpeech
} from '../api'

/**
 * 默认落在「坐席辅助」：本页的入口就是侧栏与顶栏里的「坐席辅助」，
 * 落在这个切片才和入口名对得上；识图、语音、工具链在页内切过去即可。
 */
const section = ref('assist')
const sectionOptions = [
  { value: 'vision', label: '识图' },
  { value: 'voice', label: '语音' },
  { value: 'tools', label: '工具链' },
  { value: 'assist', label: '坐席辅助' }
]

const busy = ref('')
const imageForm = reactive({ question: '这张图里是什么订单？' })
const imageResult = ref(null)
const voiceForm = reactive({ audioUrl: 'aud-3001', text: '您好，您的退款已提交，预计 1-3 个工作日到账。', voice: 'female_zh' })
const sttResult = ref(null)
const ttsResult = ref(null)
const tools = ref([])
const chainPicked = ref([])
const chainResult = ref(null)
const recommendations = ref([])
const assistForm = reactive({ sessionId: 'sess_demo_2001' })
const summary = ref(null)
const nextSentences = ref([])
const flowStep = ref(1)
const flowResult = ref(null)
const packCode = ref('ecommerce')
const pack = ref(null)

const riskText = (r) => ({ read: '只读', write: '写入', critical: '高风险' }[r] || r)

const doChatImage = async () => {
  busy.value = 'image'
  try {
    const res = await chatImage({ question: imageForm.question, fileId: 'img-2001' })
    imageResult.value = res.data
  } catch {
    imageResult.value = null
  } finally {
    busy.value = ''
  }
}

const doStt = async () => {
  busy.value = 'stt'
  try {
    const res = await speechToText({ fileId: voiceForm.audioUrl })
    sttResult.value = res.data
  } catch {
    sttResult.value = null
  } finally {
    busy.value = ''
  }
}

const doTts = async () => {
  busy.value = 'tts'
  try {
    const res = await textToSpeech({ text: voiceForm.text, voice: voiceForm.voice })
    ttsResult.value = res.data
  } catch {
    ttsResult.value = null
  } finally {
    busy.value = ''
  }
}

const loadTools = async () => {
  busy.value = 'tools'
  try {
    const res = await listAgentTools()
    tools.value = Array.isArray(res.data) ? res.data : []
  } catch {
    tools.value = []
  } finally {
    busy.value = ''
  }
}

const onToolSelect = (rows) => {
  chainPicked.value = rows
}

const runChain = async () => {
  busy.value = 'chain'
  try {
    const res = await runToolChain({ toolNames: chainPicked.value.map((t) => t.toolName) })
    chainResult.value = res.data
    ElMessage.success(res.msg || '执行完成')
  } catch {
    chainResult.value = null
  } finally {
    busy.value = ''
  }
}

const doClearCache = async () => {
  busy.value = 'clear'
  try {
    const res = await clearToolCache()
    ElMessage.success(res.msg || '已清空')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const doRecommend = async () => {
  busy.value = 'recommend'
  try {
    const res = await assistRecommend({ sessionId: assistForm.sessionId })
    recommendations.value = res.data?.recommendations || []
  } catch {
    recommendations.value = []
  } finally {
    busy.value = ''
  }
}

const doSummary = async () => {
  busy.value = 'summary'
  try {
    const res = await assistSummary({ sessionId: assistForm.sessionId })
    summary.value = res.data
  } catch {
    summary.value = null
  } finally {
    busy.value = ''
  }
}

const doNext = async () => {
  busy.value = 'next'
  try {
    const res = await assistNextSentence({ sessionId: assistForm.sessionId })
    nextSentences.value = res.data?.suggestions || []
  } catch {
    nextSentences.value = []
  } finally {
    busy.value = ''
  }
}

const doFlowStep = async () => {
  busy.value = 'flow'
  try {
    const res = await aiFlowStep({ sessionId: assistForm.sessionId, step: flowStep.value })
    flowResult.value = res.data
  } catch {
    flowResult.value = null
  } finally {
    busy.value = ''
  }
}

const loadPack = async () => {
  busy.value = 'pack'
  try {
    const res = await getIndustryPack(packCode.value)
    pack.value = res.data
  } catch {
    pack.value = null
  } finally {
    busy.value = ''
  }
}

onMounted(loadTools)
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
.op-card {
  padding: 14px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  margin-bottom: 12px;
}
.op-card h6 { margin: 0 0 6px; font-size: 13.5px; color: #1c2b4a; }
.assist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
  flex-wrap: wrap;
}
.result-box { margin-top: 10px; }
.recognized { margin-top: 4px; font-size: 12.5px; color: #475467; }
.tag-line { margin-top: 6px; display: flex; gap: 6px; flex-wrap: wrap; }
.assist-list {
  margin: 10px 0 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: #475467;
}
.sub-title { margin: 18px 0 10px; font-size: 13.5px; color: #1c2b4a; }
.mini-table { max-width: 820px; }
.muted { color: #98a2b3; font-size: 12.5px; margin: 0 0 8px; }
@media (max-width: 860px) {
  .assist-grid { grid-template-columns: 1fr; }
}
</style>
