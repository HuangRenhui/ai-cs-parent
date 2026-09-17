<template>
  <!-- 调用树弹窗：服务→类→方法三层，可复制纯文本分享，步骤再多也在内部滚动 -->
  <el-dialog
    class="ops-calltree-dialog"
    :model-value="modelValue"
    :title="title"
    width="880px"
    align-center
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @opened="onOpened"
  >
    <div class="ops-ct">
      <div class="ops-ct-bar">
        <span class="ops-ct-meta">
          共 {{ methodCount }} 个方法
          <template v-if="durationMs != null"> · 总耗时 {{ durationLabel(durationMs) }}</template>
        </span>
        <el-input
          v-if="treeData.length > 4"
          v-model="keyword"
          clearable
          size="small"
          placeholder="筛选类名、方法、内容"
          class="ops-ct-search"
        />
        <el-button size="small" type="primary" plain @click="copyShare">复制调用树</el-button>
      </div>
      <div class="ops-ct-scroll" ref="scroller">
        <el-tree
          v-if="filtered.length"
          :data="filtered"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          class="ops-ct-tree"
        >
          <template #default="{ data }">
            <div class="ops-ct-node" :class="data.nodeType">
              <div class="ops-ct-line">
                <el-tag v-if="data.nodeType === 'service'" size="small">{{ data.label }}</el-tag>
                <span v-else class="ops-ct-name">{{ data.label }}</span>
                <el-tag v-if="data.durationMs != null" size="small" :type="durationTone(data.durationMs)" effect="plain">
                  {{ durationLabel(data.durationMs) }}
                </el-tag>
                <el-tag v-if="data.level && data.level !== 'INFO'" size="small" :type="data.level === 'ERROR' ? 'danger' : 'warning'">
                  {{ levelText(data.level) }}
                </el-tag>
                <span v-if="data.startTime" class="ops-ct-time">{{ data.startTime }}</span>
              </div>
              <div v-if="data.nodeType === 'method'" class="ops-ct-detail">
                <code v-if="data.className">{{ data.className }}</code>
                <span v-if="data.line">:{{ data.line }}</span>
                <span v-if="data.message" class="ops-ct-msg">{{ data.message }}</span>
              </div>
              <div v-if="data.analysis" class="ops-ct-hint">{{ data.analysis }}</div>
            </div>
          </template>
        </el-tree>
        <el-empty v-else :description="emptyText" />
      </div>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { durationLabel, durationTone, toTreeData } from '../utils/opsCallTree'
import { displayText, LOG_LEVEL_TEXT, SERVICE_NAME_TEXT } from '../utils/selectOptions'

const props = defineProps({
  /** 是否打开 */
  modelValue: { type: Boolean, default: false },
  /** 弹窗标题 */
  title: { type: String, default: '调用树' },
  /** 树根（服务节点，带 children） */
  roots: { type: Array, default: () => [] },
  /** 整段耗时毫秒 */
  durationMs: { type: Number, default: null },
  /** 可复制的纯文本调用树 */
  shareText: { type: String, default: '' },
  emptyText: { type: String, default: '暂无调用步骤' }
})

defineEmits(['update:modelValue'])

const keyword = ref('')
const scroller = ref(null)

const serviceText = (v) => displayText(SERVICE_NAME_TEXT, v, v)

/** 转成 el-tree 数据，服务名翻中文 */
const treeData = computed(() => toTreeData(props.roots, serviceText))

const methodCount = computed(() => {
  const walk = (nodes) => (nodes || []).reduce((n, x) => n + (x.nodeType === 'method' ? 1 : 0) + walk(x.children), 0)
  return walk(treeData.value)
})

/** 关键字同时匹配类名、方法、正文、排障提示 */
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return treeData.value
  const match = (node) => {
    const blob = [node.label, node.className, node.methodName, node.message, node.analysis, node.service].join(' ').toLowerCase()
    const self = blob.includes(q)
    const kids = (node.children || []).map(match).filter(Boolean)
    if (!self && !kids.length) return null
    return { ...node, children: self ? node.children : kids }
  }
  return treeData.value.map(match).filter(Boolean)
})

const levelText = (lv) => displayText(LOG_LEVEL_TEXT, lv, lv)

const onOpened = () => {
  keyword.value = ''
  nextTick(() => {
    if (scroller.value) scroller.value.scrollTop = 0
  })
}

/** 复制纯文本树，方便贴到工单或群聊 */
const copyShare = async () => {
  const text = props.shareText || '暂无调用步骤'
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('调用树已复制，可直接粘贴分享')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

watch(() => props.modelValue, (open) => {
  if (!open) keyword.value = ''
})
</script>

<style>
.ops-calltree-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  width: min(880px, calc(100vw - 32px)) !important;
  max-height: min(88vh, 860px) !important;
  margin: 0 auto !important;
}
.ops-calltree-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow: hidden !important;
  display: flex;
  flex-direction: column;
  max-height: none !important;
}
</style>

<style scoped>
.ops-ct {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  gap: 8px;
}
.ops-ct-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.ops-ct-meta {
  font-size: 13px;
  color: #667085;
}
.ops-ct-search {
  width: 220px;
  flex: 1;
  min-width: 160px;
}
.ops-ct-scroll {
  flex: 1;
  min-height: 200px;
  max-height: min(64vh, 580px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px 4px 8px 0;
}
.ops-ct-tree {
  background: transparent;
}
.ops-ct-tree :deep(.el-tree-node__content) {
  height: auto;
  align-items: flex-start;
  padding: 6px 0;
}
.ops-ct-node {
  min-width: 0;
  padding: 2px 0 4px;
}
.ops-ct-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ops-ct-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--cs-ink, #1d2939);
  font-family: inherit;
}
.ops-ct-time {
  margin-left: auto;
  font-size: 12px;
  color: #98a2b3;
  white-space: nowrap;
}
.ops-ct-detail {
  margin-top: 4px;
  font-size: 13px;
  color: #667085;
  word-break: break-all;
}
.ops-ct-detail code {
  font-family: inherit;
  font-size: 13px;
  color: #667085;
}
.ops-ct-msg {
  display: block;
  margin-top: 2px;
  color: #1c2b4a;
  white-space: pre-wrap;
}
.ops-ct-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #b54708;
  line-height: 1.5;
}
.ops-ct-node.service .ops-ct-hint { display: none; }
@media (max-width: 640px) {
  .ops-ct-search { width: 100%; }
  .ops-ct-time { width: 100%; margin-left: 0; }
  .ops-ct-scroll { max-height: calc(100vh - 240px); }
}
</style>
