<template>
  <!-- 普通用户登录（C 端）：账号密码进聊天页，租户走系统默认 -->
  <LoginStage variant="light">
    <h2 class="login-welcome">欢迎登录</h2>
    <p class="login-welcome-sub">请使用下发的坐席账号登录聊天工作台</p>

    <el-form :model="form" :rules="rules" ref="formRef" class="login-form">
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="账号" size="large">
          <template #prefix>
            <el-icon class="login-input-icon"><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          size="large"
          show-password
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <el-icon class="login-input-icon"><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <div class="login-extra">
        <el-checkbox v-model="remember">记住登录状态</el-checkbox>
        <span class="login-link-quiet">忘记密码请联系管理员</span>
      </div>

      <el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form-item>
    </el-form>

    <p class="login-error" v-if="errorMsg">{{ errorMsg }}</p>

    <div class="login-mini-links">
      <el-button text :loading="ssoLoading" @click="handleSso">对接平台登录</el-button>
      <span>|</span>
      <el-button text @click="$router.push('/help-center')">帮助中心</el-button>
    </div>

    <div class="login-demo" v-if="isMock">
      <p class="login-demo-title"><span>演示账号（密码任意）</span></p>
      <div class="login-demo-chips">
        <button type="button" class="demo-chip is-blue" @click="fill('agent001')">普通用户：agent001</button>
      </div>
    </div>

    <!-- 后台入口做得很轻：C 端客户不该被它吸引，但内部人员要能找到 -->
    <p class="login-switch">
      内部人员？<router-link to="/login/admin">运营后台登录 →</router-link>
    </p>

    <ClickCaptcha v-model="captchaVisible" @success="onCaptchaSuccess" />
  </LoginStage>
</template>

<script setup>
/**
 * 普通用户登录页，路由 `/login`。
 * 与运营后台登录（`/login/admin`）是两个独立页面，互不共用表单，避免入口混用。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { platformLogin } from '../api'
import { isMockEnabled } from '../mock'
import { LOGIN_TYPE_PLATFORM, homeAfterLogin, saveSession } from '../utils/auth'
import ClickCaptcha from '../components/ClickCaptcha.vue'
import LoginStage from '../components/LoginStage.vue'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const ssoLoading = ref(false)
const captchaVisible = ref(false)
const pendingKind = ref('password')
const errorMsg = ref('')
const remember = ref(false)
const isMock = isMockEnabled()

const form = reactive({
  username: isMock ? 'agent001' : '',
  password: isMock ? 'password' : ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 演示账号一键填入，省得每次手敲 */
const fill = (username) => {
  form.username = username
  form.password = 'password'
}

const submit = async (payload) => {
  errorMsg.value = ''
  const res = await platformLogin(payload, { silent: true })
  if (res.code === 200) {
    const data = { ...res.data, loginType: LOGIN_TYPE_PLATFORM }
    saveSession(data)
    router.push(homeAfterLogin(LOGIN_TYPE_PLATFORM))
    return
  }
  errorMsg.value = res.msg || '登录失败'
}

/** 账密登录：表单通过后先弹点选验证 */
const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (!valid) return
    pendingKind.value = 'password'
    captchaVisible.value = true
  })
}

/** 演示单点：同样要过图片验证 */
const handleSso = async () => {
  pendingKind.value = 'sso'
  captchaVisible.value = true
}

/** 点选通过后换票，租户固定走默认值 */
const onCaptchaSuccess = async () => {
  const isSso = pendingKind.value === 'sso'
  if (isSso) ssoLoading.value = true
  else loading.value = true
  try {
    await submit(
      isSso
        ? {
            tenantCode: 'default',
            username: form.username || 'agent001',
            password: 'sso',
            loginType: LOGIN_TYPE_PLATFORM,
            ssoTicket: 'demo-sso-ticket'
          }
        : {
            tenantCode: 'default',
            username: form.username,
            password: form.password,
            loginType: LOGIN_TYPE_PLATFORM
          }
    )
  } catch (e) {
    errorMsg.value = e?.msg || e?.response?.data?.msg || e?.message || (isSso ? '单点登录失败' : '登录失败')
  } finally {
    loading.value = false
    ssoLoading.value = false
  }
}
</script>
