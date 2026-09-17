<template>
  <el-tag class="type-tag" :type="tagType" size="small" effect="light">{{ label }}</el-tag>
</template>

<script setup>
/**
 * 表格类型色标：同类枚举用固定颜色区分，避免一列纯文字看不出差异。
 */
import { computed } from 'vue'

const props = defineProps({
  /** 展示文案，同时用来选颜色 */
  text: { type: String, default: '' },
  /** 强制指定 Element Plus tag 类型 */
  type: { type: String, default: '' }
})

/** 文案 → 颜色，未收录的走 info，保证不会花成一团 */
const COLOR_MAP = {
  智能: 'primary',
  人工: 'warning',
  咨询: 'primary',
  投诉: 'danger',
  建议: 'success',
  售后: 'warning',
  贵宾: 'warning',
  普通: 'info',
  VIP: 'warning',
  退款: 'danger',
  目录: 'info',
  菜单: 'primary',
  按钮: 'warning',
  人设: 'primary',
  拒答: 'danger',
  槽位: 'warning',
  系统: 'info',
  业务: 'success',
  实体卡: 'primary',
  表单: 'success',
  演示: 'info',
  接口: 'primary',
  只读: 'success',
  写入: 'warning',
  高风险: 'danger',
  低: 'success',
  中: 'warning',
  高: 'danger',
  文本: 'info',
  数字: 'primary',
  布尔: 'success',
  对象: 'warning',
  日期: 'success',
  手机号: 'warning',
  订单号: 'primary',
  未恢复: 'danger',
  已恢复: 'success',
  启用: 'success',
  禁用: 'info',
  会话: 'primary',
  消息: 'warning',
  知识库: 'success',
  工具调用: 'info',
  日志: 'info',
  邮件: 'primary',
  短信: 'warning',
  无: 'info',
  令牌: 'primary',
  签名: 'warning'
}

const label = computed(() => props.text || '—')
const tagType = computed(() => {
  if (props.type) return props.type
  return COLOR_MAP[props.text] || 'info'
})
</script>
