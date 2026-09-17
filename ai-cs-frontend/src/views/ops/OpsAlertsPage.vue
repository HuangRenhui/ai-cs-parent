<template>
  <div class="page-card">
    <p class="hint">{{ bundle.hint }}</p>
    <SectionSwitch v-model="section" :options="sectionOptions" />

    <template v-if="section === 'rules'">
      <div class="page-toolbar is-actions-only">
        <el-button type="primary" @click="save">保存开关</el-button>
      </div>
      <el-table :data="ruleRecords" stripe empty-text="暂无规则" table-layout="fixed" max-height="680">
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
      <el-table-column label="说明" min-width="160">
        <template #default="{ row }">
          <CellText title="说明" :text="row.description" />
        </template>
      </el-table-column>
      <el-table-column label="通道" min-width="80">
        <template #default="{ row }">
          <TypeTag :text="displayText(CHANNEL_TEXT, row.channel)" />
        </template>
      </el-table-column>
      <el-table-column label="启用" min-width="72">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => row.enabled = on ? 1 : 0" />
        </template>
      </el-table-column>
    </el-table>
      <TablePager v-model:page="rulePage" v-model:size="ruleSize" :total="ruleTotal" />
    </template>

    <template v-else>
      <el-table :data="eventRecords" stripe empty-text="暂无告警事件" table-layout="fixed" max-height="680">
      <el-table-column prop="time" label="时间" min-width="168" />
      <el-table-column label="标题" min-width="160">
        <template #default="{ row }">
          <CellText title="标题" :text="row.title" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="88">
        <template #default="{ row }">
          <TypeTag :text="displayText(ALERT_STATUS_TEXT, row.status)" />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="160">
        <template #default="{ row }">
          <CellText title="说明" :text="row.message" />
        </template>
      </el-table-column>
    </el-table>
      <TablePager v-model:page="eventPage" v-model:size="eventSize" :total="eventTotal" />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOpsAlerts, saveOpsAlerts } from '../../api'
import { ALERT_STATUS_TEXT, CHANNEL_TEXT, displayText } from '../../utils/selectOptions'
import { useClientPager } from '../../composables/useClientPager'
import TablePager from '../../components/TablePager.vue'
import SectionSwitch from '../../components/SectionSwitch.vue'

const bundle = ref({ rules: [], events: [], hint: '' })
const rules = computed(() => bundle.value.rules || [])
const events = computed(() => bundle.value.events || [])
const { page: rulePage, size: ruleSize, total: ruleTotal, records: ruleRecords } = useClientPager(rules)
const { page: eventPage, size: eventSize, total: eventTotal, records: eventRecords } = useClientPager(events)

/** 规则 / 当前事件横排切片 */
const section = ref('rules')
const sectionOptions = [
  { value: 'rules', label: '规则' },
  { value: 'events', label: '当前事件' }
]

const load = async () => {
  const res = await getOpsAlerts()
  bundle.value = res.data || { rules: [], events: [] }
}

const save = async () => {
  const res = await saveOpsAlerts(bundle.value.rules || [])
  bundle.value = res.data || bundle.value
  ElMessage.success('已保存（内存，重启丢失）')
}

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; line-height: 1.6; font-size: 13px; margin-bottom: 12px; }
</style>
