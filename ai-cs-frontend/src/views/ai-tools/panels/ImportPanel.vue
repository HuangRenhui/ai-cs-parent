<template>
  <div>
    <el-alert title="表格在当前页面内解析预览，批量写入功能暂未开通。" type="info" :closable="false" show-icon class="mb" />
    <el-form label-width="96px">
      <el-form-item label="导入到">
        <el-radio-group v-model="target">
          <el-radio-button label="customer">客户</el-radio-button>
          <el-radio-button label="knowledge">知识库</el-radio-button>
          <el-radio-button label="workorder">工单</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="文件">
        <el-upload :show-file-list="true" accept=".csv,.txt" :limit="1" :http-request="onFile">
          <TbBtn act="import" label="选择 CSV" />
        </el-upload>
        <p class="hint">第一行当作表头。也可先填入演示表格再预览。</p>
        <el-button text type="primary" @click="fillSample">填入演示表格</el-button>
      </el-form-item>
    </el-form>

    <el-table v-if="rows.length" :data="rows" stripe max-height="320" empty-text="没有可预览的行" table-layout="fixed">
      <el-table-column v-for="col in columns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
    </el-table>
    <p v-if="rows.length" class="hint">共 {{ rows.length }} 行。列名可在后端接通后做字段映射。</p>
    <el-button type="primary" :disabled="!rows.length" @click="runImport">开始导入</el-button>
  </div>
</template>

<script setup>
/**
 * 智能导入：本地解析 CSV 做预览，真正批量写入留给后端。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pendingBackend } from '../pending'

const target = ref('customer')
const raw = ref('')
const rows = ref([])

const SAMPLE = '昵称,手机,邮箱,标签\n张敏,13800138001,zhangmin@example.com,贵宾\n周杰,13800138002,zhoujie@example.com,普通'

/** 表头来自第一行，动态出列 */
const columns = computed(() => (rows.value[0] ? Object.keys(rows.value[0]) : []))

/** 把 CSV 文本拆成对象数组；空行丢掉 */
const parseCsv = (text) => {
  const lines = String(text || '').split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  if (lines.length < 2) return []
  const headers = lines[0].split(',').map((h) => h.trim() || '列')
  return lines.slice(1).map((line) => {
    const cells = line.split(',')
    const row = {}
    headers.forEach((h, i) => { row[h] = (cells[i] || '').trim() })
    return row
  })
}

const applyText = (text) => {
  raw.value = text
  const parsed = parseCsv(text)
  rows.value = parsed
  if (!parsed.length) ElMessage.warning('没有解析到数据行')
}

/** 上传后读成本地文本，不发到服务器 */
const onFile = async (opt) => {
  const file = opt?.file
  if (!file) return
  const text = await file.text()
  applyText(text)
}

const fillSample = () => applyText(SAMPLE)

const runImport = () => {
  const names = { customer: '客户', knowledge: '知识库', workorder: '工单' }
  pendingBackend(`${names[target.value] || ''}批量导入`)
}
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.hint { margin: 6px 0 12px; font-size: 12px; color: #98a2b3; }
</style>
