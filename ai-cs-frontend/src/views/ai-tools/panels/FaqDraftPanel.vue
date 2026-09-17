<template>
  <div>
    <el-alert title="起草在当前页面内完成，保存到知识库功能暂未开通。" type="info" :closable="false" show-icon class="mb" />
    <el-form label-width="96px">
      <el-form-item label="原始材料">
        <el-input v-model="raw" type="textarea" :rows="7" placeholder="粘贴对话、帮助文档或坐席备注" />
        <el-button text type="primary" @click="fillSample">填入示例</el-button>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!raw.trim()" @click="draft">生成草稿</el-button>
      </el-form-item>
      <el-form-item label="问句"><el-input v-model="question" /></el-form-item>
      <el-form-item label="答案"><el-input v-model="answer" type="textarea" :rows="5" /></el-form-item>
      <el-form-item label="分类"><el-input v-model="category" placeholder="售后 / 物流 / 订单" /></el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">保存到知识库</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
/**
 * 知识起草：把一段材料收成 FAQ 草稿，保存留给后端。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pendingBackend } from '../pending'

const raw = ref('')
const question = ref('')
const answer = ref('')
const category = ref('')

const SAMPLE = `顾客：发票能开专票吗？
坐席：可以。请在订单详情提交抬头和税号，审核后 1-3 个工作日开具。`

const fillSample = () => {
  raw.value = SAMPLE
}

/** 优先拆「问/答」行；否则第一句当问、全文当答 */
const draft = () => {
  const text = raw.value.trim()
  const ask = text.match(/顾客[：:](.*)/)?.[1]?.trim()
    || text.match(/问[：:](.*)/)?.[1]?.trim()
    || text.split(/[？?]/)[0]
  const reply = text.match(/坐席[：:](.*)/)?.[1]?.trim()
    || text.match(/答[：:]([\s\S]*)/)?.[1]?.trim()
    || text
  question.value = ask ? (ask.endsWith('？') || ask.endsWith('?') ? ask : `${ask}？`) : text.slice(0, 30)
  answer.value = reply
  category.value = /发票|专票/.test(text) ? '订单' : (/物流|快递/.test(text) ? '物流' : '售后')
  ElMessage.success('已生成草稿，请核对后再保存')
}

const save = () => pendingBackend('保存知识')
</script>

<style scoped>
.mb { margin-bottom: 16px; }
</style>
