<template>
  <!-- 运维时间线弹窗：步骤再多也限制在视口内滚动，避免把整页撑出去 -->
  <el-dialog
    class="ops-timeline-dialog"
    :model-value="modelValue"
    :title="title"
    width="720px"
    align-center
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @opened="onOpened"
  >
    <div class="ops-tl">
      <div class="ops-tl-bar">
        <span class="ops-tl-count">
          共 {{ items.length }} 步
          <template v-if="keyword">，筛出 {{ filtered.length }} 步</template>
        </span>
        <!-- 步骤较多时才出筛选，短链路不必占一行 -->
        <el-input
          v-if="items.length > 8"
          v-model="keyword"
          clearable
          size="small"
          placeholder="筛选服务、级别或内容"
          class="ops-tl-search"
        />
        <div v-if="filtered.length > 8" class="ops-tl-jump">
          <el-button size="small" text @click="scrollTo('top')">顶部</el-button>
          <el-button size="small" text @click="scrollTo('bottom')">底部</el-button>
        </div>
      </div>
      <div
        ref="scroller"
        class="ops-tl-scroll"
        :class="{ compact: filtered.length > 12 }"
      >
        <ol v-if="filtered.length" class="ops-tl-list">
          <li
            v-for="(item, idx) in filtered"
            :key="item.key || idx"
            class="ops-tl-item"
            :class="item.type || 'primary'"
          >
            <div class="ops-tl-rail">
              <span class="ops-tl-idx">{{ idx + 1 }}</span>
              <i v-if="idx < filtered.length - 1" class="ops-tl-line" />
            </div>
            <div class="ops-tl-card">
              <div class="ops-tl-head">
                <el-tag size="small">{{ item.tag || '未知服务' }}</el-tag>
                <span v-if="item.level" class="ops-tl-level">{{ item.level }}</span>
                <span v-if="item.time" class="ops-tl-time">{{ item.time }}</span>
              </div>
              <p v-if="item.text" class="ops-tl-text">{{ item.text }}</p>
              <div v-if="item.extra" class="ops-tl-extra">{{ item.extra }}</div>
            </div>
          </li>
        </ol>
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

const props = defineProps({
  /** 是否打开 */
  modelValue: { type: Boolean, default: false },
  /** 弹窗标题 */
  title: { type: String, default: '调用时间线' },
  /** 步骤：tag 服务名、level 级别、text 正文、extra 补充、time 时间、type 颜色 */
  items: { type: Array, default: () => [] },
  /** 空状态文案 */
  emptyText: { type: String, default: '暂无步骤' }
})

defineEmits(['update:modelValue'])

const keyword = ref('')
const scroller = ref(null)

/** 关键字同时匹配服务、级别、正文，长链路时用来快速定位某一步 */
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const list = props.items || []
  if (!q) return list
  return list.filter((item) => {
    const blob = [item.tag, item.level, item.text, item.extra, item.time].join(' ').toLowerCase()
    return blob.includes(q)
  })
})

/** 打开时滚回顶部，并清空上次筛选，避免带着旧关键字看新链路 */
const onOpened = () => {
  keyword.value = ''
  nextTick(() => scrollTo('top'))
}

/** 顶部 / 底部跳转，步骤很多时不用一直用手滑 */
const scrollTo = (where) => {
  const el = scroller.value
  if (!el) return
  el.scrollTop = where === 'bottom' ? el.scrollHeight : 0
}

watch(() => props.modelValue, (open) => {
  if (!open) keyword.value = ''
})
</script>

<!-- 弹窗挂到 body，不能用 scoped，否则高度约束套不上 -->
<style>
.ops-timeline-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  width: min(720px, calc(100vw - 32px)) !important;
  max-height: min(88vh, 840px) !important;
  margin: 0 auto !important;
}
.ops-timeline-dialog .el-dialog__header {
  flex-shrink: 0;
}
.ops-timeline-dialog .el-dialog__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: calc(100% - 48px);
  display: block;
}
.ops-timeline-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding-bottom: 4px !important;
}
.ops-timeline-dialog .el-dialog__footer {
  flex-shrink: 0;
}
@media (max-width: 640px) {
  .ops-timeline-dialog.el-dialog {
    width: calc(100vw - 16px) !important;
    max-height: calc(100vh - 16px - var(--safe-top, 0px) - var(--safe-bottom, 0px)) !important;
  }
}
</style>

<style scoped>
.ops-tl {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  gap: 8px;
}
.ops-tl-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.ops-tl-count {
  font-size: 13px;
  color: #667085;
  white-space: nowrap;
}
.ops-tl-search {
  width: 220px;
  flex: 1;
  min-width: 160px;
}
.ops-tl-jump {
  margin-left: auto;
}
.ops-tl-scroll {
  flex: 1;
  min-height: 180px;
  max-height: min(64vh, 560px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px 6px 8px 0;
}
.ops-tl-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ops-tl-item {
  display: flex;
  gap: 10px;
  min-width: 0;
}
.ops-tl-rail {
  width: 28px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ops-tl-idx {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #eef3fb;
  color: #2f6bff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ops-tl-item.warning .ops-tl-idx { background: #fff7e8; color: #f79009; }
.ops-tl-item.danger .ops-tl-idx { background: #fef3f2; color: #d92d20; }
.ops-tl-line {
  width: 2px;
  flex: 1;
  min-height: 12px;
  background: #e7edf5;
  margin: 4px 0;
}
.ops-tl-card {
  flex: 1;
  min-width: 0;
  background: #f8fafc;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.ops-tl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ops-tl-level {
  font-size: 12px;
  color: #667085;
}
.ops-tl-time {
  margin-left: auto;
  font-size: 12px;
  color: #98a2b3;
  white-space: nowrap;
}
.ops-tl-text {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: #1c2b4a;
  word-break: break-word;
  white-space: pre-wrap;
}
.ops-tl-extra {
  margin-top: 4px;
  font-size: 12px;
  color: #667085;
  word-break: break-all;
}
/* 步骤很多时收紧行距，同一屏能看到更多节点 */
.ops-tl-scroll.compact .ops-tl-card {
  padding: 6px 10px;
  margin-bottom: 6px;
}
.ops-tl-scroll.compact .ops-tl-text {
  margin-top: 4px;
}
@media (max-width: 640px) {
  .ops-tl-search { width: 100%; }
  .ops-tl-jump { margin-left: 0; }
  .ops-tl-time { width: 100%; margin-left: 0; }
  .ops-tl-scroll { max-height: calc(100vh - 220px); }
}
</style>
