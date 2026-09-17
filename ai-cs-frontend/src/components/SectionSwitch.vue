<template>
  <!-- 下划线标签：两项及以上才显示，左对齐不拉满，与表单里的等分切片区分开 -->
  <el-tabs
    v-if="options.length > 1"
    :model-value="String(modelValue)"
    class="section-switch"
    @tab-change="onChange"
  >
    <el-tab-pane
      v-for="opt in options"
      :key="String(opt.value)"
      :label="opt.label"
      :name="String(opt.value)"
    />
  </el-tabs>
</template>

<script setup>
/**
 * 页内/布局区块切换。样式对齐常见后台的下划线 Tabs：当前项蓝字+蓝条，其余灰色。
 * 只有一项时不渲染，避免和侧栏或顶栏重复。
 */
defineProps({
  /** 当前选中值，与某个 option.value 对应 */
  modelValue: { type: [String, Number], required: true },
  /** 标签项：{ value, label } */
  options: { type: Array, required: true }
})

const emit = defineEmits(['update:modelValue', 'change'])

/** 同步 v-model，并通知父组件按新区块拉数或跳转 */
const onChange = (val) => {
  emit('update:modelValue', val)
  emit('change', val)
}
</script>

<style scoped>
.section-switch {
  margin-bottom: 8px;
}
/* 只当导航用，不占用内容区 */
.section-switch :deep(.el-tabs__content) {
  display: none;
}
.section-switch :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.section-switch :deep(.el-tabs__item) {
  height: 42px;
  line-height: 42px;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 500;
}
.section-switch :deep(.el-tabs__item.is-active) {
  font-weight: 600;
}
.section-switch :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}
.section-switch :deep(.el-tabs__nav-scroll) {
  overflow-x: auto;
}
</style>
