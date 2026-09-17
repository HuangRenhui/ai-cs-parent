<template>
  <el-alert
    type="warning"
    :closable="false"
    show-icon
    title="自定义工具：执行由外部开放接口完成"
    :description="`${tool.httpMethod || 'POST'} ${tool.apiUrl || '（未配置接口地址）'}`"
    style="margin-bottom: 16px"
  />

  <el-form label-width="110px" style="max-width: 780px">
    <el-form-item label="请求参数">
      <el-input
        v-model="params"
        type="textarea"
        :rows="6"
        placeholder='按接口约定填写 JSON，例如 {"orderNo": "SO-10086"}'
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" :loading="testing" @click="test">测试连通性</el-button>
      <el-button :disabled="!params.trim()" @click="invoke">调用接口</el-button>
      <span class="tip">后端未接通前，调用只做占位提示，不会真的发出请求。</span>
    </el-form-item>
  </el-form>

  <el-divider content-position="left">调用结果</el-divider>
  <pre class="result">{{ result || '（暂无结果）' }}</pre>
</template>

<script setup>
/**
 * 绑定外部开放接口的自定义工具面板：
 * 工具定义里带 httpMethod + apiUrl，这里负责拼参数、测连通、展示结果。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { testAiTool } from '../../../api'

const props = defineProps({
  /** 工具定义（来自 /system/ai-tool/list） */
  tool: { type: Object, required: true }
})

const params = ref('')
const result = ref('')
const testing = ref(false)

const test = async () => {
  testing.value = true
  try {
    const res = await testAiTool(props.tool.id)
    result.value = res?.data || '连通性正常'
  } catch {
    result.value = '连通性测试失败'
  } finally {
    testing.value = false
  }
}

const invoke = () => {
  ElMessage.warning('调用链路待后端接通')
  result.value =
    `（占位）将向 ${props.tool.httpMethod || 'POST'} ${props.tool.apiUrl} 发送：\n${params.value}`
}
</script>

<style scoped>
.tip {
  margin-left: 12px;
  font-size: 12px;
  color: #98a2b3;
}
.result {
  margin: 0;
  padding: 12px;
  min-height: 80px;
  max-height: 360px;
  overflow: auto;
  border-radius: 8px;
  background: #f7f9fc;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
