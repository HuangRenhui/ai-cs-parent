<template>
  <div class="page-card">
    <p class="hint">
      图谱增强能力：在知识图谱之上再做图数据库分析、规则推理、时序演化、多语言融合、图嵌入与 GraphRAG。
      多数能力依赖图库与向量库同时就绪，未就绪时按钮会给出明确提示而不是静默失败。
    </p>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <component :is="currentPanel" />
  </div>
</template>

<script setup>
/**
 * 知识图谱增强：把 8 个子域收在一个页面里用标签切换。
 * 每个子域独立成组件，避免单文件膨胀到难以维护。
 */
import { computed, ref } from 'vue'
import SectionSwitch from '../../components/SectionSwitch.vue'
import EvolutionPanel from './panels/EvolutionPanel.vue'
import EmbeddingPanel from './panels/EmbeddingPanel.vue'
import GraphRagPanel from './panels/GraphRagPanel.vue'
import Model3dPanel from './panels/Model3dPanel.vue'
import MultilingualPanel from './panels/MultilingualPanel.vue'
import Neo4jPanel from './panels/Neo4jPanel.vue'
import ReasoningPanel from './panels/ReasoningPanel.vue'
import TemporalPanel from './panels/TemporalPanel.vue'

const section = ref('model3d')
const sectionOptions = [
  { value: 'model3d', label: '3D 模型' },
  { value: 'neo4j', label: '图数据库' },
  { value: 'reasoning', label: '图谱推理' },
  { value: 'temporal', label: '时序演化' },
  { value: 'multilingual', label: '多语言' },
  { value: 'embedding', label: '图嵌入' },
  { value: 'graphrag', label: 'GraphRAG' },
  { value: 'evolution', label: '图谱健康' }
]

const PANELS = {
  model3d: Model3dPanel,
  neo4j: Neo4jPanel,
  reasoning: ReasoningPanel,
  temporal: TemporalPanel,
  multilingual: MultilingualPanel,
  embedding: EmbeddingPanel,
  graphrag: GraphRagPanel,
  evolution: EvolutionPanel
}

const currentPanel = computed(() => PANELS[section.value] || Model3dPanel)
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
</style>
