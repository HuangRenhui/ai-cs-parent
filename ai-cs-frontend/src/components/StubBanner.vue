<template>
  <!-- 内存桩 / 假数据页统一横幅：不要把演示数据当成已落地能力 -->
  <el-alert
    type="warning"
    :closable="false"
    show-icon
    title="后端未完成"
    :description="description"
    class="stub-banner"
  />
</template>

<script setup>
/**
 * 后端未完成横幅：报表、排队、租户等内存桩页共用同一条文案。
 */
defineProps({
  /** 补充说明，默认强调假数据不落库、不能当生产 */
  description: {
    type: String,
    default: '当前为内存桩或假数据，刷新或重启后不保留，请勿当生产能力。'
  }
})
</script>

<style scoped>
.stub-banner {
  margin-bottom: 12px;
}
</style>
