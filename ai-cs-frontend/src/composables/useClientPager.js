/**
 * 前端分页：接口一次返回全量列表时，只渲染当前页，表格用滚轮看本页，底部分页翻页。
 */
import { computed, ref, watch } from 'vue'

export function useClientPager(sourceRef, defaultSize = 10) {
  const page = ref(1)
  const size = ref(defaultSize)
  const total = computed(() => (sourceRef.value || []).length)
  const records = computed(() => {
    const list = sourceRef.value || []
    const start = (page.value - 1) * size.value
    return list.slice(start, start + size.value)
  })
  /** 总条数变少或改每页条数时，避免停在空页 */
  watch([total, size], () => {
    const maxPage = Math.max(1, Math.ceil(total.value / size.value) || 1)
    if (page.value > maxPage) page.value = maxPage
  })
  return { page, size, total, records }
}
