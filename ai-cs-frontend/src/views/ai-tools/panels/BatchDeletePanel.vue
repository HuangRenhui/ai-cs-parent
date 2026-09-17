<template>
  <div>
    <el-alert title="查找结果为演示名单。实际删除功能暂未开通，开通后需二次确认。" type="warning" :closable="false" show-icon class="mb" />
    <el-form label-width="96px" @submit.prevent>
      <el-form-item label="清理范围">
        <el-radio-group v-model="scope">
          <el-radio-button label="customer">客户</el-radio-button>
          <el-radio-button label="knowledge">知识库</el-radio-button>
          <el-radio-button label="workorder">工单</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="prompt" placeholder="例如：删除标签为测试的客户" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查找匹配</el-button>
        <el-button text type="primary" @click="fillSample">填入示例</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="hits" stripe max-height="360" empty-text="先查找，再勾选要删除的记录" table-layout="fixed" @selection-change="onSelect">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="title" label="名称" min-width="140" />
      <el-table-column prop="extra" label="摘要" min-width="200" show-overflow-tooltip />
    </el-table>
    <div class="actions">
      <TbBtn act="delete" :disabled="!selected.length" @click="remove" />
    </div>
  </div>
</template>

<script setup>
/**
 * 批量清理：按一句话筛出演示名单，勾选后仍走占位，避免误删。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pendingBackend } from '../pending'

const scope = ref('customer')
const prompt = ref('')
const hits = ref([])
const selected = ref([])

const DEMO = {
  customer: [
    { id: 4, title: '赵敏', extra: '手机 13800138004 · 标签 普通' },
    { id: 8, title: '演示用户8', extra: '标签 测试 · 2026-07 导入' },
    { id: 12, title: '测试客户', extra: '邮箱 test@example.com · 标签 测试' }
  ],
  knowledge: [
    { id: 9, title: '演示问答 9', extra: '分类 售后 · 未审核' },
    { id: 11, title: '测试 FAQ', extra: '分类 物流 · 标签 测试' }
  ],
  workorder: [
    { id: 5, title: 'WO_9005', extra: '类型 咨询 · 演示工单' },
    { id: 10, title: 'WO_9010', extra: '类型 投诉 · 测试单' }
  ]
}

const fillSample = () => {
  prompt.value = '删除标签为测试的记录'
}

/** 关键词命中演示名单；没有关键词则列出该范围全部演示行 */
const search = () => {
  const all = DEMO[scope.value] || []
  const k = prompt.value.trim()
  hits.value = k ? all.filter((r) => `${r.title} ${r.extra}`.includes(k.replace(/删除|标签为|的记录|的客户/g, '').trim()) || /测试/.test(k)) : all
  if (!hits.value.length) ElMessage.info('没有匹配的演示记录')
}

const onSelect = (rows) => {
  selected.value = rows
}

/** 二次确认后仍不调删除接口 */
const remove = async () => {
  await ElMessageBox.confirm(`将删除 ${selected.value.length} 条，后端接通前不会真正删库。`, '确认批量删除', { type: 'warning' })
  pendingBackend('批量删除')
}
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.actions { margin-top: 12px; }
</style>
