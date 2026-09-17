<template>
  <!-- 统一分页条：总数、每页条数、页码；改条数回到第 1 页。服务端分页页监听 change 再拉数据 -->
  <div class="pager">
    <el-pagination
      background
      :layout="layout"
      :total="total"
      :current-page="page"
      :page-size="size"
      :page-sizes="pageSizes"
      :pager-count="pagerCount"
      @current-change="onPage"
      @size-change="onSize"
    />
  </div>
</template>

<script setup>
import { nextTick } from 'vue'

defineProps({
  /** 当前页，从 1 起 */
  page: { type: Number, default: 1 },
  /** 每页条数 */
  size: { type: Number, default: 10 },
  /** 总条数 */
  total: { type: Number, default: 0 },
  /** 每页条数选项 */
  pageSizes: { type: Array, default: () => [10, 20, 50] },
  layout: { type: String, default: 'total, sizes, prev, pager, next' }
})

const emit = defineEmits(['update:page', 'update:size', 'change'])

/** 窄屏少显示几个页码按钮，避免挤出屏幕 */
const pagerCount = typeof window !== 'undefined' && window.innerWidth < 640 ? 5 : 7

/** 等父组件完成 v-model 同步后再通知拉数，避免用到旧的页码/条数 */
const notify = async () => {
  await nextTick()
  emit('change')
}

/** 翻页 */
const onPage = (val) => {
  emit('update:page', val)
  notify()
}

/** 改每页条数时回到第一页 */
const onSize = (val) => {
  emit('update:size', val)
  emit('update:page', 1)
  notify()
}
</script>
