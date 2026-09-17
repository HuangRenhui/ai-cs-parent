<template>
  <el-dialog :title="title" v-model="open" width="520px">
    <p class="dialog-lead">勾选要导出的字段，并选择导出来源。未勾选的列不会写入文件。</p>
    <el-form label-width="88px">
      <el-form-item label="导出范围">
        <el-radio-group v-model="scope">
          <el-radio-button label="selected" :disabled="!selectedCount">已选 {{ selectedCount }} 条</el-radio-button>
          <el-radio-button label="page">本页 {{ pageCount }} 条</el-radio-button>
          <el-radio-button label="all">全部筛选</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="导出字段">
        <div>
          <el-checkbox :model-value="allChecked" :indeterminate="indeterminate" @change="onCheckAll">全选</el-checkbox>
          <el-checkbox-group v-model="picked" class="export-fields">
            <el-checkbox v-for="f in fields" :key="f.key" :label="f.key">{{ f.label }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :disabled="!picked.length" @click="confirm">导出</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 导出前勾选字段和范围：已选行 / 本页 / 当前筛选下的全部。
 */
import { computed, ref, watch } from 'vue'

const props = defineProps({
  /** 是否打开 */
  modelValue: { type: Boolean, default: false },
  /** 弹窗标题 */
  title: { type: String, default: '导出' },
  /** 可选字段，{ key, label } */
  fields: { type: Array, default: () => [] },
  /** 表格里已勾选的行数 */
  selectedCount: { type: Number, default: 0 },
  /** 当前页行数 */
  pageCount: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

/** selected=勾选行 page=本页 all=筛选全部 */
const scope = ref('page')
const picked = ref([])

const allChecked = computed(() => props.fields.length > 0 && picked.value.length === props.fields.length)
const indeterminate = computed(() => {
  const n = picked.value.length
  return n > 0 && n < props.fields.length
})

/** 打开时默认全选字段；有勾选行则优先导出已选 */
watch(() => props.modelValue, (v) => {
  if (!v) return
  picked.value = props.fields.map((f) => f.key)
  scope.value = props.selectedCount ? 'selected' : 'page'
})

const onCheckAll = (on) => {
  picked.value = on ? props.fields.map((f) => f.key) : []
}

const confirm = () => {
  emit('confirm', { scope: scope.value, keys: [...picked.value] })
  open.value = false
}
</script>

<style scoped>
.export-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 8px;
}
.export-fields :deep(.el-checkbox) {
  margin-right: 0;
}
</style>
