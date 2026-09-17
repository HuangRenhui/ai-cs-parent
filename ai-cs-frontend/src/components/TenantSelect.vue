<template>
  <!-- 租户编码下拉：默认 default，允许临时新建编码 -->
  <el-select
    :model-value="modelValue"
    filterable
    allow-create
    default-first-option
    :clearable="clearable"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-option
      v-for="t in TENANT_OPTIONS"
      :key="t.value"
      :label="t.label"
      :value="t.value"
    />
  </el-select>
</template>

<script setup>
import { TENANT_OPTIONS } from '../utils/selectOptions'

defineProps({
  /** 当前租户编码 */
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '选择租户' },
  clearable: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])
</script>
