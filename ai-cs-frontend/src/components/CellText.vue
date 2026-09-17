<template>
  <!-- 表格长文本：单行省略，点击弹窗看全文 -->
  <span
    class="cell-text"
    :class="{ 'is-empty': !hasText, 'is-clickable': hasText }"
    @click.stop="onClick"
  >{{ display }}</span>
</template>

<script setup>
/**
 * 列表里可能被截断的单元格：一行显示，点开后看完整内容。
 */
import { computed } from 'vue'
import { peekText } from '../composables/useTextPeek'

const props = defineProps({
  /** 单元格原始内容 */
  text: { type: [String, Number], default: '' },
  /** 弹窗标题，一般用列名 */
  title: { type: String, default: '完整内容' }
})

const raw = computed(() => {
  if (props.text == null) return ''
  return String(props.text).trim()
})
const hasText = computed(() => !!raw.value)
const display = computed(() => raw.value || '—')

/** 有内容才打开全文弹窗 */
const onClick = () => {
  if (!hasText.value) return
  peekText(props.title, raw.value)
}
</script>
