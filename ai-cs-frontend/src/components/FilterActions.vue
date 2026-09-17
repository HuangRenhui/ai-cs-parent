<template>
  <!-- 查询 + 重置：带输入框的筛选统一这一对按钮 -->
  <span class="filter-actions">
    <el-button type="primary" native-type="button" @click="$emit('search')">
      <el-icon><Search /></el-icon>
      查询
    </el-button>
    <el-button native-type="button" @click="$emit('reset')">
      <el-icon><RefreshRight /></el-icon>
      重置
    </el-button>
  </span>
</template>

<script setup>
/**
 * 筛选条上的查询/重置，样式对齐参考图里的实心查询与白底重置。
 */
import { RefreshRight, Search } from '@element-plus/icons-vue'

defineEmits(['search', 'reset'])
</script>

<style scoped>
.filter-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
