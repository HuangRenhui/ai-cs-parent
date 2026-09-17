<template>
  <div>
    <el-alert title="字段在当前页面内补全，创建工单功能暂未开通。" type="info" :closable="false" show-icon class="mb" />
    <el-form label-width="96px">
      <el-form-item label="投诉原文">
        <el-input v-model="raw" type="textarea" :rows="6" placeholder="粘贴顾客留言或通话纪要" />
        <el-button text type="primary" @click="fillSample">填入示例</el-button>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!raw.trim()" @click="fill">补全工单</el-button>
      </el-form-item>
      <el-form-item label="工单类型"><el-input v-model="orderType" /></el-form-item>
      <el-form-item label="工单内容"><el-input v-model="orderContent" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="phone" /></el-form-item>
      <el-form-item label="紧急程度">
        <el-radio-group v-model="priority">
          <el-radio-button label="普通">普通</el-radio-button>
          <el-radio-button label="紧急">紧急</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">创建工单</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
/**
 * 工单补全：从原文抽类型和电话，创建工单留给后端。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pendingBackend } from '../pending'

const raw = ref('')
const orderType = ref('')
const orderContent = ref('')
const phone = ref('')
const priority = ref('普通')

const SAMPLE = '顾客周杰来电 13800138002，非常着急：物流已经超期三天还没到，要求尽快处理并补偿。'

const fillSample = () => {
  raw.value = SAMPLE
}

const fill = () => {
  const text = raw.value.trim()
  phone.value = text.match(/1[3-9]\d{9}/)?.[0] || ''
  orderType.value = /投诉|不满|差评/.test(text) ? '投诉' : (/建议/.test(text) ? '建议' : (/物流|快递/.test(text) ? '咨询' : '咨询'))
  if (/着急|紧急|超期/.test(text)) priority.value = '紧急'
  orderContent.value = text
  ElMessage.success('已补全工单字段，请核对后再创建')
}

const save = () => pendingBackend('创建工单')
</script>

<style scoped>
.mb { margin-bottom: 16px; }
</style>
