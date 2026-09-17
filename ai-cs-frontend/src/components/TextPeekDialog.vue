<template>
  <!-- 全站共用：点表格被截断的文字后看全文 -->
  <el-dialog
    class="text-peek-dialog"
    :title="textPeek.title"
    v-model="textPeek.visible"
    width="640px"
    append-to-body
    destroy-on-close
  >
    <pre class="text-peek-body">{{ textPeek.content }}</pre>
    <template #footer>
      <el-button @click="copy">复制</el-button>
      <el-button type="primary" @click="textPeek.visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 长文本全文弹窗，状态来自 useTextPeek，全站只挂一次。
 */
import { ElMessage } from 'element-plus'
import { textPeek } from '../composables/useTextPeek'

/** 把弹窗里的全文写到剪贴板 */
const copy = async () => {
  try {
    await navigator.clipboard.writeText(textPeek.content || '')
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}
</script>
