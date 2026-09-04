<template>
  <div class="login-container">
    <div class="login-card">
      <h1 class="login-title">AI 智能客服系统</h1>
      <p class="login-subtitle">登录您的账户</p>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" class="login-form">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" size="large"
            @keyup.enter="handleLogin" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" @click="handleLogin" :loading="loading">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <p class="login-error" v-if="errorMsg">{{ errorMsg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)
const errorMsg = ref('')

const loginForm = reactive({
  username: 'admin',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    errorMsg.value = ''
    try {
      const res = await request.post('/auth/login', {
        username: loginForm.username,
        password: loginForm.password
      }, { silent: true })
      if (res.code === 200) {
        localStorage.setItem('token', res.data.token)
        localStorage.setItem('userInfo', JSON.stringify(res.data))
        router.push('/')
      } else {
        errorMsg.value = res.msg || '登录失败'
      }
    } catch (e) {
      errorMsg.value = e?.msg || e?.response?.data?.msg || e?.message || '登录失败'
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  background: white;
  padding: 40px;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 400px;
  max-width: 90%;
}
.login-title {
  text-align: center;
  color: #2a3f5f;
  font-size: 24px;
  margin-bottom: 8px;
}
.login-subtitle {
  text-align: center;
  color: #909399;
  margin-bottom: 30px;
  font-size: 14px;
}
.login-form {
  width: 100%;
}
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
}
.login-error {
  color: #f56c6c;
  text-align: center;
  font-size: 14px;
  margin-top: 8px;
}
</style>
