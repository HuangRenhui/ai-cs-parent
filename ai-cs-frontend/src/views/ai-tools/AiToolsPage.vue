<template>
  <div class="page-card">
    <StubBanner description="内置工具仅前端 catalog；自定义工具接口为内存桩，保存后不会真正落库。" />
    <p class="hint">
      内置工具开箱可用；自定义工具通过注册接口创建，执行方式可选「复用内置能力」或「调用开放接口」。
    </p>
    <div class="page-toolbar is-actions-only">
      <TbBtn act="add" label="新建工具" @click="openDialog()" />
    </div>

    <el-form inline @submit.prevent>
      <el-form-item label="分类">
        <el-radio-group v-model="category">
          <el-radio-button label="全部" />
          <el-radio-button v-for="c in categories" :key="c" :label="c" />
        </el-radio-group>
      </el-form-item>
      <el-form-item label="搜索">
        <el-input v-model="keyword" clearable placeholder="工具名称或用途" style="width: 220px" />
      </el-form-item>
    </el-form>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="8" v-for="tool in filtered" :key="tool.code">
        <div class="tool-card" :class="{ 'is-off': tool.enabled === 0 }" @click="openTool(tool)">
          <div class="tool-icon" :style="{ background: tool.color || '#2f6bff' }">
            <el-icon :size="22"><component :is="iconMap[tool.icon] || MagicStick" /></el-icon>
          </div>
          <div class="tool-body">
            <div class="tool-head">
              <span class="tool-name">{{ tool.name }}</span>
              <el-tag size="small" :type="tool.builtin ? 'info' : 'success'" effect="plain">
                {{ tool.builtin ? '内置' : '自定义' }}
              </el-tag>
              <el-tag v-if="tool.enabled === 0" size="small" type="warning" effect="plain">已停用</el-tag>
            </div>
            <div class="tool-meta">
              <el-tag size="small" effect="plain">{{ tool.category }}</el-tag>
              <span class="tool-exec">{{ execText(tool) }}</span>
            </div>
            <p class="tool-summary">{{ tool.summary }}</p>
            <div class="tool-foot">
              <span class="tool-link">打开工具</span>
              <!-- 自定义工具才能改，内置工具不允许编辑/删除 -->
              <div v-if="!tool.builtin" class="tool-actions" @click.stop>
                <el-switch
                  :model-value="tool.enabled === 1"
                  size="small"
                  @change="(v) => toggleEnabled(tool, v)"
                />
                <el-button link type="primary" @click="openDialog(tool)">编辑</el-button>
                <el-button link type="danger" @click="remove(tool)">删除</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
    <el-empty v-if="!filtered.length" description="没有匹配的工具" />

    <el-dialog :title="form.id ? '编辑工具' : '新建工具'" v-model="dialogVisible" width="640px">
      <el-form :model="form" label-width="110px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="工具编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="小写字母 / 数字 / 中划线，如 order-query" />
        </el-form-item>
        <el-form-item label="工具名称" required>
          <el-input v-model="form.name" maxlength="30" placeholder="卡片与工作台展示的名称" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select
            v-model="form.category"
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="选择已有分类，或直接输入新分类"
            style="width: 100%"
          >
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="form-tip">手动输入新名称回车即创建新分类</span>
        </el-form-item>
        <el-form-item label="用途摘要" required>
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="使用提示">
          <el-input v-model="form.hint" placeholder="选填，工作台标题下方展示这行提示" />
        </el-form-item>
        <el-form-item label="图标">
          <el-select
            v-model="form.icon"
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="选择已有图标，或直接输入图标名"
            style="width: 220px"
          >
            <el-option v-for="(comp, name) in iconMap" :key="name" :label="ICON_TEXT[name] || name" :value="name">
              <span class="icon-opt">
                <el-icon><component :is="comp" /></el-icon>
                <span>{{ ICON_TEXT[name] || name }}</span>
              </span>
            </el-option>
          </el-select>
          <span class="form-tip">未识别的图标名会回退为默认图标</span>
        </el-form-item>
        <el-form-item label="主题色">
          <el-color-picker v-model="form.color" />
          <el-input v-model="form.color" class="color-input" placeholder="#2f6bff" @blur="normalizeColor" />
          <span class="form-tip">也可直接输入十六进制色值</span>
        </el-form-item>
        <el-form-item label="卡片预览">
          <div class="tool-preview">
            <div class="tool-icon" :style="{ background: form.color || '#2f6bff' }">
              <el-icon :size="20"><component :is="iconMap[form.icon] || MagicStick" /></el-icon>
            </div>
            <div class="preview-copy">
              <div class="tool-name">{{ form.name || '工具名称' }}</div>
              <el-tag size="small" effect="plain">{{ form.category || '未分类' }}</el-tag>
            </div>
          </div>
        </el-form-item>

        <el-divider content-position="left">执行方式</el-divider>
        <el-form-item label="执行方式">
          <el-radio-group v-model="form.execType">
            <el-radio value="builtin">复用内置能力</el-radio>
            <el-radio value="http">调用开放接口</el-radio>
          </el-radio-group>
          <div class="strategy-tip">{{ EXEC_TIP[form.execType] }}</div>
        </el-form-item>
        <el-form-item v-if="form.execType === 'builtin'" label="内置面板">
          <el-select v-model="form.panelCode" style="width: 100%">
            <el-option v-for="o in PANEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value">
              <span class="panel-opt">
                <span>{{ o.label }}</span>
                <span class="panel-desc">{{ o.desc }}</span>
              </span>
            </el-option>
          </el-select>
          <span class="form-tip">{{ currentPanelDesc }}</span>
        </el-form-item>
        <template v-else>
          <el-form-item label="请求方式">
            <el-radio-group v-model="form.httpMethod">
              <el-radio-button label="GET" />
              <el-radio-button label="POST" />
              <el-radio-button label="PUT" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="接口地址" required>
            <el-input v-model="form.apiUrl" placeholder="https://api.example.com/your-endpoint" />
            <span class="form-tip">需以 http:// 或 https:// 开头</span>
          </el-form-item>
        </template>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * AI 工具箱：内置工具（catalog）+ 自定义工具（注册接口）合并展示。
 * 自定义工具可绑定外部开放接口，实现不改代码就能扩展工具。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Cpu, DataAnalysis, Delete, Document, Download, EditPen, Filter,
  MagicStick, Reading, Refresh, Search, SetUp, Star, Tickets, Upload
} from '@element-plus/icons-vue'
import { AI_TOOLS } from './catalog'
import { listAiTools, saveAiTool, deleteAiTool, setAiToolEnabled } from '../../api'
import TbBtn from '../../components/TbBtn.vue'

/** 卡片图标：存组件名，这里映射成真实组件；填了未识别的名字会回退为 MagicStick */
const iconMap = {
  EditPen, Upload, Delete, Reading, Tickets,
  Search, Download, Document, DataAnalysis, Cpu, MagicStick, SetUp, Filter, Refresh, Star
}
const ICON_TEXT = {
  EditPen: '录入', Upload: '导入', Delete: '删除', Reading: '阅读', Tickets: '工单',
  Search: '查询', Download: '下载', Document: '文档', DataAnalysis: '分析', Cpu: '模型',
  MagicStick: '通用', SetUp: '设置', Filter: '筛选', Refresh: '刷新', Star: '收藏'
}
/** 可复用的内置面板，取值需与 AiToolWorkspacePage 的 PANELS 一致 */
const PANEL_OPTIONS = [
  { value: 'entry', label: '智能录入', desc: '粘贴文字识别字段后写入客户 / 工单 / 知识库' },
  { value: 'import', label: '智能导入', desc: '上传表格，对列预览后批量写入' },
  { value: 'batch-delete', label: '批量清理', desc: '按条件筛出名单，确认后批量删除' },
  { value: 'faq-draft', label: '知识起草', desc: '把说明或对话整理成 FAQ 草稿' },
  { value: 'ticket-fill', label: '工单补全', desc: '从原文自动填工单类型与内容' }
]
/** 两种执行方式的取舍，写清楚免得选错 */
const EXEC_TIP = {
  builtin: '直接复用工具箱已有的能力面板，无需额外开发；适合把现有动作换个名字做成新工具。',
  http: '工作台会按下面的地址发起请求；适合把外部系统的接口包装成一个工具。'
}

const router = useRouter()
const keyword = ref('')
const category = ref('全部')
const customTools = ref([])

/** 内置工具 + 自定义工具，内置在前 */
const allTools = computed(() => [
  ...AI_TOOLS.map((t) => ({ ...t, builtin: true, enabled: 1, execType: 'builtin', panelCode: t.code })),
  ...customTools.value
])

const categories = computed(() => [...new Set(allTools.value.map((t) => t.category).filter(Boolean))])

/** 按分类和关键字筛工具 */
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return allTools.value.filter((t) => {
    if (category.value !== '全部' && t.category !== category.value) return false
    if (!k) return true
    return [t.name, t.summary, t.category].some((s) => String(s || '').toLowerCase().includes(k))
  })
})

/** 执行方式的人话描述 */
const execText = (t) =>
  t.execType === 'http' ? `${t.httpMethod || 'POST'} ${t.apiUrl || ''}` : '复用内置能力'

/** 色值容错：允许省略 #，非法值回退默认色，避免存进奇怪的值 */
const normalizeColor = () => {
  let c = String(form.color || '').trim()
  if (!c) {
    form.color = '#2f6bff'
    return
  }
  if (!c.startsWith('#')) c = '#' + c
  form.color = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(c) ? c.toLowerCase() : '#2f6bff'
}

const load = async () => {
  try {
    const res = await listAiTools()
    const data = res.data
    customTools.value = Array.isArray(data) ? data : (data?.records || [])
  } catch {
    customTools.value = []
  }
}

// ===== 新建 / 编辑 =====
const dialogVisible = ref(false)
const saving = ref(false)
const EMPTY = {
  id: null,
  code: '',
  name: '',
  category: '',
  summary: '',
  hint: '',
  icon: 'EditPen',
  color: '#2f6bff',
  execType: 'builtin',
  panelCode: 'entry',
  httpMethod: 'POST',
  apiUrl: '',
  enabled: 1
}
const form = reactive({ ...EMPTY })

/** 当前所选内置面板的说明，选完能确认自己选对了 */
const currentPanelDesc = computed(
  () => PANEL_OPTIONS.find((o) => o.value === form.panelCode)?.desc || ''
)

const openDialog = (tool) => {
  Object.assign(form, EMPTY)
  if (tool) Object.assign(form, tool)
  dialogVisible.value = true
}

const submit = async () => {
  if (!form.code.trim() || !form.name.trim() || !form.category || !form.summary.trim()) {
    ElMessage.warning('请填写工具编码、名称、分类与用途摘要')
    return
  }
  if (!form.id && !/^[a-z0-9-]+$/.test(form.code.trim())) {
    ElMessage.warning('工具编码只能用英文小写、数字或中划线')
    return
  }
  if (form.execType === 'http') {
    const url = String(form.apiUrl || '').trim()
    if (!url) {
      ElMessage.warning('执行方式为「调用开放接口」时，请填写接口地址')
      return
    }
    if (!/^https?:\/\//i.test(url)) {
      ElMessage.warning('接口地址需要以 http:// 或 https:// 开头')
      return
    }
  }
  saving.value = true
  try {
    const res = await saveAiTool({ ...form, code: form.code.trim() })
    ElMessage.success(res?.data || '已保存')
    dialogVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

const remove = async (tool) => {
  await ElMessageBox.confirm(`确认删除工具「${tool.name}」？`, '删除工具', { type: 'warning' })
  try {
    await deleteAiTool(tool.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

const toggleEnabled = async (tool, v) => {
  try {
    await setAiToolEnabled(tool.id, v ? 1 : 0)
    ElMessage.success(v ? '已启用' : '已停用')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

/** 进入某个工具的工作台 */
const openTool = (tool) => {
  if (tool.enabled === 0) {
    ElMessage.warning('该工具已停用，请先启用')
    return
  }
  router.push(`/ai/tools/${tool.code}`)
}

onMounted(load)
</script>

<style scoped>
.tool-card {
  display: flex;
  gap: 14px;
  width: 100%;
  text-align: left;
  border: 1px solid #e7edf5;
  background: linear-gradient(180deg, #fbfcfe 0%, #f5f8fc 100%);
  border-radius: 14px;
  padding: 16px 18px;
  margin-bottom: 16px;
  cursor: pointer;
  color: inherit;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}
.tool-card:hover {
  transform: translateY(-2px);
  border-color: #c4d4ff;
  box-shadow: 0 10px 24px rgba(47, 107, 255, 0.08);
}
.is-off {
  opacity: 0.62;
}
.tool-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tool-body {
  flex: 1;
  min-width: 0;
}
.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tool-name {
  font-size: 16px;
  font-weight: 650;
  color: #1c2b4a;
}
.tool-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.tool-exec {
  font-size: 12px;
  color: #98a2b3;
  word-break: break-all;
}
.tool-summary {
  margin: 8px 0 10px;
  font-size: 13px;
  line-height: 1.5;
  color: #667085;
  min-height: 40px;
}
.tool-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 26px;
}
.tool-link {
  font-size: 13px;
  color: #2f6bff;
  font-weight: 600;
}
.tool-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #98a2b3;
}
.icon-opt {
  display: flex;
  align-items: center;
  gap: 8px;
}
.color-input {
  width: 130px;
  margin-left: 12px;
}
.tool-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: linear-gradient(180deg, #fbfcfe 0%, #f5f8fc 100%);
}
.tool-preview .tool-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
}
.preview-copy {
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-copy .tool-name {
  margin-bottom: 0;
}
.strategy-tip {
  flex-basis: 100%;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #98a2b3;
}
.panel-opt {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.panel-desc {
  font-size: 12px;
  color: #98a2b3;
}
</style>
