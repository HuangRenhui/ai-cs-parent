<template>
  <div class="page-card">
    <StubBanner />
    <div class="page-toolbar">
      <div>
        <h3>极简接入向导</h3>
        <p class="page-desc">从开通租户到配置回调，按步骤完成接入，零改内核。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="progress-wrap">
      <el-progress :percentage="progress.percent || 0" :stroke-width="14" striped />
      <span class="progress-text">已完成 {{ progress.done || 0 }} / {{ progress.total || 0 }} 步</span>
    </div>

    <el-steps :active="progress.current || 0" align-center finish-status="success" class="steps">
      <el-step v-for="s in steps" :key="s.order" :title="s.title" :description="s.status" />
    </el-steps>

    <el-table :data="steps" stripe empty-text="暂无步骤">
      <el-table-column prop="order" label="步骤" width="70" />
      <el-table-column prop="title" label="环节" min-width="140" show-overflow-tooltip />
      <el-table-column prop="desc" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="100">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button v-if="row.link" link type="primary" size="small" @click="$router.push(row.link)">前往</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getOnboardingSteps, getOnboardingProgress } from '../api'

const steps = ref([])
const progress = ref({})

const statusType = (status) => {
  if (status === '已完成') return 'success'
  if (status === '进行中') return 'warning'
  return 'info'
}
const load = async () => {
  try {
    const [s, p] = await Promise.all([getOnboardingSteps(), getOnboardingProgress()])
    steps.value = s.data || []
    progress.value = p.data || {}
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.progress-wrap { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.progress-wrap .el-progress { flex: 1; }
.progress-text { color: #6b7280; font-size: 13px; white-space: nowrap; }
.steps { margin-bottom: 20px; }
</style>
