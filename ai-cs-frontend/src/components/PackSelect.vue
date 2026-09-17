<template>
  <!-- 行业包下拉：选项来自开放接入已配置的包 -->
  <el-select
    :model-value="modelValue"
    filterable
    :clearable="clearable"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-option
      v-for="p in packs"
      :key="p.code"
      :label="p.name || p.code"
      :value="p.code"
    />
  </el-select>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listOpenPacks } from '../api'

defineProps({
  /** 当前 packCode */
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '选择行业包' },
  clearable: { type: Boolean, default: true }
})

defineEmits(['update:modelValue'])

const packs = ref([])

onMounted(async () => {
  try {
    const res = await listOpenPacks()
    packs.value = res.data || []
  } catch {
    packs.value = []
  }
})
</script>
