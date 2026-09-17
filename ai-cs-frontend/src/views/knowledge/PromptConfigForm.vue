<template>
  <div class="prompt-form">
    <div class="form-head">
      <span class="head-tip">共 5 组配置，点标题展开/收起；收起时标题右侧仍显示当前值</span>
      <el-button link type="primary" @click="toggleAll">{{ allOpen ? '全部收起' : '全部展开' }}</el-button>
    </div>

    <el-collapse v-model="activePanels" class="prompt-collapse">
      <!-- 开关与预设 -->
      <el-collapse-item name="basic">
        <template #title>
          <span class="panel-title">开关与预设</span>
          <span class="panel-summary">{{ basicSummary }}</span>
        </template>
        <el-form :model="model" label-width="150px">
          <el-form-item label="启用 Prompt 调优">
            <el-switch v-model="model.enabled" />
            <span class="form-tip">总开关，关闭后其余配置不生效</span>
          </el-form-item>
          <el-form-item label="调试日志">
            <el-switch v-model="model.debugLog" />
            <span class="form-tip">输出 Prompt 拼装过程，便于排查</span>
          </el-form-item>
          <el-form-item label="预设模板">
            <el-select v-model="model.preset" clearable placeholder="不使用预设" style="width: 280px">
              <el-option v-for="p in presets" :key="p.name" :label="p.label" :value="p.name" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- 角色限定 -->
      <el-collapse-item v-if="model.enabled" name="role">
        <template #title>
          <span class="panel-title">角色限定</span>
          <span class="panel-summary">{{ roleSummary }}</span>
        </template>
        <el-form :model="model" label-width="150px">
          <el-form-item label="角色设定">
            <el-input v-model="model.systemRole" placeholder="例如：专业客服助手" />
          </el-form-item>
          <el-form-item label="角色描述">
            <el-input v-model="model.roleDescription" type="textarea" :rows="2" placeholder="对该角色的补充说明" />
          </el-form-item>
          <el-form-item label="所属领域">
            <el-input v-model="model.roleDomain" placeholder="例如：电商售后" />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- 输出格式与推理 -->
      <el-collapse-item v-if="model.enabled" name="format">
        <template #title>
          <span class="panel-title">输出格式与推理</span>
          <span class="panel-summary">{{ formatSummary }}</span>
        </template>
        <el-form :model="model" label-width="150px">
          <el-form-item label="输出格式">
            <el-select v-model="model.outputFormat" style="width: 200px">
              <el-option label="纯文本" value="text" />
              <el-option label="Markdown" value="markdown" />
              <el-option label="表格" value="table" />
              <el-option label="JSON" value="json" />
            </el-select>
          </el-form-item>
          <el-form-item label="严格按格式输出">
            <el-switch v-model="model.strictOutput" />
          </el-form-item>

          <el-form-item label="启用思维链">
            <el-switch v-model="model.cotEnabled" />
            <span class="form-tip">开启后填写下方的思维链指令</span>
          </el-form-item>
          <!-- 从属字段：只有开了思维链才需要填 -->
          <el-form-item v-if="model.cotEnabled" label="思维链指令">
            <el-input
              v-model="model.cotInstruction"
              type="textarea"
              :rows="2"
              placeholder="例如：先分析问题意图，再检索依据，最后给出结论"
            />
          </el-form-item>

          <el-form-item label="启用少样本示例">
            <el-switch v-model="model.fewShotEnabled" />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- 变量与占位符 -->
      <el-collapse-item v-if="model.enabled" name="variables">
        <template #title>
          <span class="panel-title">变量与占位符</span>
          <span class="panel-summary">{{ variablesSummary }}</span>
        </template>
        <el-form :model="model" label-width="150px">
          <el-form-item label="启用变量替换">
            <el-switch v-model="model.variablesEnabled" />
            <span class="form-tip">关闭后正文按原文发送，占位符不会被替换</span>
          </el-form-item>
          <template v-if="model.variablesEnabled">
            <el-form-item label="变量列表">
              <div class="var-list">
                <div v-for="(v, i) in model.variables || []" :key="i" class="var-row">
                  <el-input v-model="v.name" placeholder="变量名" class="var-name" />
                  <el-input v-model="v.desc" placeholder="说明" class="var-desc" />
                  <el-input v-model="v.defaultValue" placeholder="默认值" class="var-default" />
                  <el-checkbox v-model="v.required" :true-value="1" :false-value="0">必填</el-checkbox>
                  <el-button link type="danger" @click="removeVariable(i)">删除</el-button>
                </div>
                <el-button link type="primary" @click="addVariable">+ 添加变量</el-button>
              </div>
            </el-form-item>
            <el-form-item label="占位符写法">
              <span class="form-tip">
                正文里写 <code v-pre>{{变量名}}</code>，例如 <code v-pre>{{user_name}}</code>、<code v-pre>{{order_no}}</code>。
                取值失败时用默认值补齐；标了必填却为空，会走「无依据时回复」兜底。
              </span>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>

      <!-- 边界与上下文 -->
      <el-collapse-item v-if="model.enabled" name="boundary">
        <template #title>
          <span class="panel-title">边界与上下文</span>
          <span class="panel-summary">{{ boundarySummary }}</span>
        </template>
        <el-form :model="model" label-width="150px">
          <el-form-item label="启用边界约束">
            <el-switch v-model="model.boundaryEnabled" />
            <span class="form-tip">开启后可配置检索不到时的兜底话术</span>
          </el-form-item>
          <!-- 从属字段：只有开了边界约束才需要填 -->
          <template v-if="model.boundaryEnabled">
            <el-form-item label="无依据时回复">
              <el-input v-model="model.noDataReply" placeholder="例如：抱歉，我暂时没有找到相关信息" />
            </el-form-item>
            <el-form-item label="禁止编造">
              <el-switch v-model="model.noFabrication" />
            </el-form-item>
            <el-form-item label="附加约束">
              <el-input v-model="model.additionalConstraints" type="textarea" :rows="2" placeholder="选填：额外限制条件" />
            </el-form-item>
            <!-- 合规口径：检索不到是「不知道」，被诱导是「不该说」，两者话术要分开 -->
            <el-form-item label="违规问题拒答话术">
              <el-input v-model="model.sensitiveReply" placeholder="例如：这个问题我帮不上忙，您可以换个问题问我" />
            </el-form-item>
            <el-form-item label="提示词注入防护">
              <el-switch v-model="model.injectionGuard" />
              <span class="form-tip">拦截「忽略以上指令」类诱导，防止越权调用工具</span>
            </el-form-item>
          </template>

          <el-form-item label="上下文条数">
            <el-input-number v-model="model.contextWindowSize" :min="1" :max="20" />
            <span class="form-tip">注入提示词的检索片段条数</span>
          </el-form-item>
          <el-form-item label="仅依据上下文回答">
            <el-switch v-model="model.contextOnlyReply" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>

    <el-alert
      v-if="!model.enabled"
      type="info"
      :closable="false"
      show-icon
      title="Prompt 调优已关闭"
      description="其余配置分组已收起且不会生效，开启总开关后再编辑。"
      style="margin-top: 12px"
    />
  </div>
</template>

<script setup>
/**
 * 提示词配置表单：当前配置与模板编辑共用，避免两处字段写重复。
 * 字段为扁平结构，与后端 POST /api/prompt/config 的入参一致。
 *
 * 5 组配置改为折叠面板：收起时标题右侧显示当前值摘要，仍能一眼看到配了什么；
 * 从属字段（思维链指令、边界兜底话术、变量列表）只在对应开关打开时才出现。
 */
import { computed, ref, watch } from 'vue'

defineProps({
  /** 预设模板选项：{ name, label } */
  presets: { type: Array, default: () => [] }
})

const model = defineModel({ type: Object, required: true })

const ALL_PANELS = ['basic', 'role', 'format', 'variables', 'boundary']
/** 默认只展开最常用的「开关与预设」，其余收起 */
const activePanels = ref(['basic'])
const allOpen = computed(() => ALL_PANELS.every((n) => activePanels.value.includes(n)))

const toggleAll = () => {
  activePanels.value = allOpen.value ? ['basic'] : [...ALL_PANELS]
}

/** 关掉总开关时收起其余分组，避免看着像还在生效 */
watch(
  () => model.value.enabled,
  (on) => {
    if (!on) activePanels.value = ['basic']
  }
)

const basicSummary = computed(() =>
  [
    model.value.enabled ? '调优开' : '调优关',
    model.value.debugLog ? '调试日志开' : '调试日志关',
    model.value.preset ? `预设 ${model.value.preset}` : '未用预设'
  ].join(' · ')
)

const roleSummary = computed(() => {
  const role = model.value.systemRole || '未设定角色'
  return model.value.roleDomain ? `${role} · ${model.value.roleDomain}` : role
})

const formatSummary = computed(() =>
  [
    model.value.outputFormat || 'text',
    model.value.strictOutput ? '严格格式' : '宽松格式',
    model.value.cotEnabled ? '思维链开' : '思维链关',
    model.value.fewShotEnabled ? '少样本开' : '少样本关'
  ].join(' · ')
)

const variablesSummary = computed(() => {
  if (!model.value.variablesEnabled) return '变量替换关'
  const n = (model.value.variables || []).length
  return n ? `${n} 个变量` : '未定义变量'
})

/** 变量行内增删，字段留空的行提交时会被后端忽略 */
const addVariable = () => {
  if (!model.value.variables) model.value.variables = []
  model.value.variables.push({ name: '', desc: '', defaultValue: '', required: 0 })
}
const removeVariable = (i) => {
  model.value.variables.splice(i, 1)
}

const boundarySummary = computed(() =>
  [
    model.value.boundaryEnabled ? '边界开' : '边界关',
    `上下文 ${model.value.contextWindowSize} 条`,
    model.value.contextOnlyReply ? '仅依据上下文' : '允许自由发挥',
    model.value.injectionGuard ? '注入防护开' : '注入防护关'
  ].join(' · ')
)
</script>

<style scoped>
.form-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.head-tip {
  font-size: 12px;
  color: #98a2b3;
}
.prompt-collapse {
  border-top: none;
  border-bottom: none;
}
.prompt-collapse :deep(.el-collapse-item__header) {
  height: 42px;
  line-height: 42px;
  border-bottom: 1px solid #eef1f6;
}
.prompt-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid #eef1f6;
}
.panel-title {
  font-size: 14px;
  font-weight: 650;
  color: #1c2b4a;
}
.panel-summary {
  margin-left: 12px;
  font-size: 12px;
  font-weight: 400;
  color: #98a2b3;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
/* 变量行：一行放全，窄屏自动换行，避免四个输入框挤在一起 */
.var-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.var-name { width: 150px; flex-shrink: 0; }
.var-desc { width: 200px; flex-shrink: 0; }
.var-default { width: 170px; flex-shrink: 0; }
.form-tip code {
  background: #f1f5fb;
  border-radius: 4px;
  padding: 1px 5px;
  color: #1f4bd8;
}
/* 折叠区内表单不再顶到边 */
.prompt-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 6px;
}
.prompt-collapse :deep(.el-form-item:last-child) {
  margin-bottom: 12px;
}
</style>
