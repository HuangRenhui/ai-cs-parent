<template>
  <div class="avatar-upload">
    <el-upload
      class="uploader"
      :show-file-list="false"
      :http-request="doUpload"
      :before-upload="beforeUpload"
      accept="image/jpeg,image/png,image/gif,image/webp"
    >
      <div class="preview">
        <UserAvatar :url="modelValue" alt="头像" />
        <div class="mask">{{ modelValue && !isSystemDefaultAvatar(modelValue) ? '更换头像' : '上传头像' }}</div>
      </div>
    </el-upload>
    <el-button type="primary" link @click="reset">恢复默认</el-button>
    <p class="hint">支持 jpg / png / gif / webp，不超过 2MB。未选性别用灰色剪影，选了性别则随机一张对应默认照片。</p>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { uploadAvatar } from '../api'
import { isSystemDefaultAvatar, pickRandomDefaultAvatar } from '../utils/avatar'
import UserAvatar from './UserAvatar.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  gender: { type: Number, default: undefined }
})
const emit = defineEmits(['update:modelValue'])

const beforeUpload = (file) => {
  const okType = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  if (!okType) {
    ElMessage.error('只支持 jpg、png、gif、webp 图片')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像不能超过 2MB')
    return false
  }
  return true
}

const doUpload = async ({ file }) => {
  const res = await uploadAvatar(file)
  emit('update:modelValue', res.data)
  ElMessage.success('头像已更新')
}

const reset = () => {
  emit('update:modelValue', pickRandomDefaultAvatar(props.gender))
}
</script>

<style scoped>
.avatar-upload { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
.preview {
  position: relative; width: 88px; height: 88px; border-radius: 50%; overflow: hidden;
  cursor: pointer; border: 1px solid #e5e7eb; background: #f3f4f6;
}
.preview :deep(img) { width: 100%; height: 100%; object-fit: cover; display: block; }
.mask {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  background: rgba(15, 23, 42, 0.45); color: #fff; font-size: 12px; opacity: 0; transition: opacity .2s;
}
.preview:hover .mask { opacity: 1; }
.hint { color: #9aa3b2; font-size: 12px; margin: 0; }
</style>
