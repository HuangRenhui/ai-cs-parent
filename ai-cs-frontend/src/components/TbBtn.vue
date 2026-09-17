<template>
  <el-button
    class="tb-btn"
    :type="conf.type"
    :plain="conf.plain"
    :disabled="disabled"
    :loading="loading"
    native-type="button"
    v-bind="$attrs"
  >
    <el-icon v-if="icon"><component :is="icon" /></el-icon>
    <span>{{ text }}</span>
  </el-button>
</template>

<script setup>
/**
 * 列表工具条按钮：新增实心蓝、删除选中后实心蓝（更明显）、导入浅底蓝，未选中发灰。
 */
import { computed } from 'vue'
import { CircleCheck, CircleClose, Delete, Download, Plus, Upload } from '@element-plus/icons-vue'

defineOptions({ inheritAttrs: false })

const KINDS = {
  add: { label: '新增', icon: Plus, type: 'primary', plain: false },
  import: { label: '导入', icon: Upload, type: 'primary', plain: true },
  delete: { label: '删除', icon: Delete, type: 'primary', plain: false },
  export: { label: '导出', icon: Download, type: '', plain: false },
  complete: { label: '完成', icon: CircleCheck, type: 'primary', plain: true },
  close: { label: '关闭', icon: CircleClose, type: 'primary', plain: true }
}

const props = defineProps({
  /** add / import / delete / export */
  act: { type: String, required: true },
  /** 覆盖默认文案，例如「新增用户」 */
  label: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const conf = computed(() => KINDS[props.act] || KINDS.add)
const text = computed(() => props.label || conf.value.label)
const icon = computed(() => conf.value.icon)
</script>
