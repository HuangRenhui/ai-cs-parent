<template>
  <div class="page-card">
    <p class="hint">{{ bundle.hint }}</p>
    <div class="page-toolbar">
      <h3>规则</h3>
      <el-button type="primary" @click="save">保存开关</el-button>
    </div>
    <el-table :data="bundle.rules" stripe empty-text="暂无规则">
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="code" label="编码" width="180" />
      <el-table-column prop="description" label="说明" min-width="200" />
      <el-table-column prop="channel" label="通道" width="90" />
      <el-table-column label="启用" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => row.enabled = on ? 1 : 0" />
        </template>
      </el-table-column>
    </el-table>
    <div class="page-toolbar" style="margin-top: 24px">
      <h3>当前事件</h3>
    </div>
    <el-table :data="bundle.events" stripe empty-text="暂无告警事件">
      <el-table-column prop="time" label="时间" width="180" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="message" label="说明" min-width="180" />
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOpsAlerts, saveOpsAlerts } from '../../api'

const bundle = ref({ rules: [], events: [], hint: '' })

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
h3 { font-size: 16px; }
</style>
