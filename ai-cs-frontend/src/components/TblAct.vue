<template>
  <el-button
    class="tbl-act"
    link
    size="small"
    :type="btnType"
    v-bind="$attrs"
  >
    <el-icon v-if="icon"><component :is="icon" /></el-icon>
    <span>{{ text }}</span>
  </el-button>
</template>

<script setup>
/**
 * 表格操作链：图标 + 文字，样式对齐常见后台的「编辑 / 删除 / 预览」。
 * 用 act 选动作，不必每页再引一套图标。
 */
import { computed } from 'vue'
import {
  CircleCheck,
  CircleClose,
  Clock,
  Connection,
  Delete,
  Document,
  Edit,
  MagicStick,
  Plus,
  RefreshRight,
  Share,
  SwitchButton,
  User,
  VideoPlay,
  View
} from '@element-plus/icons-vue'

defineOptions({ inheritAttrs: false })

const ACTS = {
  view: { label: '查看', icon: View, type: 'primary' },
  detail: { label: '详情', icon: View, type: 'primary' },
  edit: { label: '编辑', icon: Edit, type: 'primary' },
  delete: { label: '删除', icon: Delete, type: 'primary' },
  preview: { label: '预览', icon: View, type: 'primary' },
  test: { label: '测通', icon: Connection, type: 'primary' },
  ping: { label: '测试', icon: Connection, type: 'primary' },
  assign: { label: '分配', icon: User, type: 'primary' },
  complete: { label: '完成', icon: CircleCheck, type: 'primary' },
  close: { label: '关闭', icon: CircleClose, type: 'primary' },
  online: { label: '上线', icon: SwitchButton, type: 'primary' },
  busy: { label: '忙碌', icon: Clock, type: 'primary' },
  offline: { label: '离线', icon: SwitchButton, type: 'primary' },
  active: { label: '生效', icon: CircleCheck, type: 'primary' },
  child: { label: '子项', icon: Plus, type: 'primary' },
  tree: { label: '调用树', icon: Share, type: 'primary' },
  log: { label: '日志', icon: Document, type: 'primary' },
  convert: { label: '转问', icon: RefreshRight, type: 'primary' },
  vector: { label: '向量化', icon: MagicStick, type: 'primary' },
  revector: { label: '重新向量化', icon: RefreshRight, type: 'primary' },
  similar: { label: '相似', icon: Document, type: 'primary' },
  classify: { label: '分类', icon: MagicStick, type: 'primary' },
  only: { label: '仅启用此包', icon: CircleCheck, type: 'primary' },
  run: { label: '执行', icon: VideoPlay, type: 'primary' }
}

const props = defineProps({
  /** 预设动作，决定图标和默认文案 */
  act: { type: String, required: true },
  /** 覆盖默认文案，例如测通/测试共用同一图标 */
  label: { type: String, default: '' }
})

const conf = computed(() => ACTS[props.act] || ACTS.view)
const text = computed(() => props.label || conf.value.label)
const icon = computed(() => conf.value.icon)
const btnType = computed(() => conf.value.type)
</script>
