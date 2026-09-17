<template>
  <div>
    <el-alert title="识别在当前页面内完成，写入系统功能暂未开通。" type="info" :closable="false" show-icon class="mb" />
    <el-form label-width="96px" @submit.prevent>
      <el-form-item label="写入对象">
        <el-radio-group v-model="target">
          <el-radio-button label="customer">客户档案</el-radio-button>
          <el-radio-button label="workorder">工单</el-radio-button>
          <el-radio-button label="knowledge">知识库</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="原始文字">
        <el-input v-model="raw" type="textarea" :rows="6" placeholder="粘贴聊天记录、邮件或口头记录" />
        <div class="sample-row">
          <el-button text type="primary" @click="fillSample">填入示例</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!raw.trim()" @click="recognize">识别填写</el-button>
      </el-form-item>
    </el-form>

    <el-divider content-position="left">识别结果（可改）</el-divider>
    <el-form v-if="target === 'customer'" label-width="96px">
      <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
      <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      <el-form-item label="标签"><el-input v-model="form.tag" /></el-form-item>
    </el-form>
    <el-form v-else-if="target === 'workorder'" label-width="96px">
      <el-form-item label="类型"><el-input v-model="form.orderType" placeholder="咨询 / 投诉 / 建议" /></el-form-item>
      <el-form-item label="内容"><el-input v-model="form.orderContent" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
    </el-form>
    <el-form v-else label-width="96px">
      <el-form-item label="问句"><el-input v-model="form.question" /></el-form-item>
      <el-form-item label="答案"><el-input v-model="form.answer" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item>
    </el-form>
    <el-button type="primary" @click="save">写入系统</el-button>
  </div>
</template>

<script setup>
/**
 * 智能录入：从一段文字里抽出字段，确认后再写入（写入占位）。
 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pendingBackend } from '../pending'

const target = ref('customer')
const raw = ref('')
const form = reactive({
  nickname: '',
  phone: '',
  email: '',
  tag: '',
  orderType: '',
  orderContent: '',
  question: '',
  answer: '',
  category: ''
})

const SAMPLE = '客户张敏，手机 13800138001，邮箱 zhangmin@example.com，标签贵宾。投诉物流太慢，希望登记并跟进。'

/** 填入一段演示原文，方便直接点识别 */
const fillSample = () => {
  raw.value = SAMPLE
}

/**
 * 用正则从原文抽手机、邮箱等；没有模型时也能演示「识别填写」。
 */
const recognize = () => {
  const text = raw.value.trim()
  if (!text) return
  const phone = text.match(/1[3-9]\d{9}/)?.[0] || ''
  const email = text.match(/[\w.+-]+@[\w-]+\.[\w.]+/)?.[0] || ''
  const name = text.match(/客户([\u4e00-\u9fa5]{2,4})/)?.[1]
    || text.match(/([\u4e00-\u9fa5]{2,4})，手机/)?.[1]
    || ''
  const tag = text.match(/标签([\u4e00-\u9fa5]{2,8})/)?.[1] || (text.includes('贵宾') ? '贵宾' : '')
  form.phone = phone
  form.email = email
  form.nickname = name
  form.tag = tag
  form.orderType = text.includes('投诉') ? '投诉' : (text.includes('建议') ? '建议' : '咨询')
  form.orderContent = text
  form.question = name ? `${name} 相关问题` : text.slice(0, 24)
  form.answer = text
  form.category = text.includes('物流') ? '物流' : '售后'
  ElMessage.success('已根据原文填写，请核对后再写入')
}

/** 真正落库等后端；这里只提示 */
const save = () => pendingBackend('写入')
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.sample-row { margin-top: 4px; }
</style>
