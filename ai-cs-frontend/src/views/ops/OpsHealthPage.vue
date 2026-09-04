<template>
  <div class="page-card">
    <div class="page-toolbar">
      <h3>服务健康</h3>
      <el-button @click="load">刷新</el-button>
    </div>
    <p class="hint">探测各服务 HTTP 端口是否可达。未把 Actuator 管理端口暴露到公网；本页经网关访问。</p>
    <el-table :data="list" stripe empty-text="无法探测，请确认 ai-cs-ops 已启动">
      <el-table-column prop="name" label="服务" width="140" />
      <el-table-column prop="url" label="探测地址" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'UP' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="httpStatus" label="HTTP" width="80" />
      <el-table-column prop="latencyMs" label="延迟 ms" width="100" />
      <el-table-column prop="message" label="说明" min-width="140" />
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getOpsHealth } from '../../api'

const list = ref([])

const load = async () => {
  const res = await getOpsHealth()
  list.value = res.data || []
}

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; line-height: 1.6; font-size: 13px; margin-bottom: 12px; }
h3 { font-size: 16px; }
</style>
