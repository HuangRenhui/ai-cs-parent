<template>
  <div class="page-card">
    <p class="hint">
      图片与音频资源的基础处理与增强能力。两类资源共用同一套流程：上传入库 → 去重校验 →
      版本管理 → 打标签 → 向量化检索，因此用上面的开关切换资源类型，下面的功能完全一致。
    </p>

    <SectionSwitch v-model="mediaType" :options="typeOptions" />
    <SectionSwitch v-model="section" :options="sectionOptions" />

    <component :is="currentPanel" :media-type="mediaType" />
  </div>
</template>

<script setup>
/**
 * 媒体资源管理：图片 / 音频切换，功能标签不随类型变化。
 * 少量类型专属能力（图片防盗链、音频波形）在「专属能力」里按类型切换显示。
 */
import { computed, ref } from 'vue'
import SectionSwitch from '../../components/SectionSwitch.vue'
import MediaExtraPanel from './media/MediaExtraPanel.vue'
import MediaInsightPanel from './media/MediaInsightPanel.vue'
import MediaLibraryPanel from './media/MediaLibraryPanel.vue'
import MediaQualityPanel from './media/MediaQualityPanel.vue'

const mediaType = ref('image')
const section = ref('library')

const typeOptions = [
  { value: 'image', label: '图片资源' },
  { value: 'audio', label: '音频资源' }
]

const sectionOptions = [
  { value: 'library', label: '资源库' },
  { value: 'quality', label: '去重与版本' },
  { value: 'insight', label: '统计与标签' },
  { value: 'extra', label: '专属能力' }
]

const PANELS = {
  library: MediaLibraryPanel,
  quality: MediaQualityPanel,
  insight: MediaInsightPanel,
  extra: MediaExtraPanel
}

const currentPanel = computed(() => PANELS[section.value] || MediaLibraryPanel)
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
</style>
