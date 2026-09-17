<template>
  <!-- 运营后台登录：独立入口、深色品牌区，准入角色受限 -->
  <LoginStage
    variant="dark"
    logo="管"
    title="智能客服 · 运营后台"
    subtitle="集中管理会话、知识库与 AI 能力"
    :show-help="false"
    :features="ADMIN_FEATURES"
  >
    <h2 class="login-welcome">欢迎登录</h2>
    <p class="login-welcome-sub">请使用管理员账号登录运营后台</p>

    <!-- 把准入规则写在表单前，别让人试完账号才知道进不来 -->
    <div class="admin-notice">
      <el-icon><Lock /></el-icon>
      <span>供运营后台使用：坐席、客服主管、知识运营、运营管理员、超级管理员均可登录，各自看到的菜单不同。C 端客户请走普通用户入口。</span>
    </div>

    <el-form :model="loginForm" :rules="rules" ref="formRef" class="login-form">
      <el-form-item prop="username">
        <el-input v-model="loginForm.username" placeholder="管理员账号" size="large">
          <template #prefix>
            <el-icon class="login-input-icon"><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
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
        <span class="login-link-quiet">忘记密码请联系超级管理员</span>
      </div>

      <el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form-item>
    </el-form>

    <p class="login-error" v-if="errorMsg">{{ errorMsg }}</p>

    <div class="login-demo" v-if="isMock">
      <p class="login-demo-title"><span>演示账号（密码任意）</span></p>
      <div class="login-demo-chips">
        <button type="button" class="demo-chip" @click="fill('admin')">超级管理员：admin</button>
        <button type="button" class="demo-chip is-green" @click="fill('operator')">运营管理员：operator</button>
        <button type="button" class="demo-chip is-blue" @click="fill('supervisor')">客服主管：supervisor</button>
        <button type="button" class="demo-chip is-blue" @click="fill('kops')">知识运营：kops</button>
        <button type="button" class="demo-chip is-green" @click="fill('agent')">一线坐席：agent</button>
      </div>
    </div>

    <p class="login-switch">
      我是普通用户 <router-link to="/login">去普通用户登录 →</router-link>
    </p>

    <ClickCaptcha v-model="captchaVisible" @success="onCaptchaSuccess" />
  </LoginStage>
</template>

<script setup>
/**
 * 运营后台登录页，路由 `/login/admin`。
 *
 * 与普通用户登录分成两个独立页面：后台承载全量客户数据与平台配置，
 * 入口不混用，且**服务端会按角色拒绝非管理员账号**（`UserService.assertAdminEntrance`），
 * 前端只做同口径提示，不做唯一防线。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { login } from '../api'
import { isMockEnabled } from '../mock'
import { LOGIN_TYPE_ADMIN, homeAfterLogin, saveSession } from '../utils/auth'
import ClickCaptcha from '../components/ClickCaptcha.vue'
import LoginStage from '../components/LoginStage.vue'

/** 后台能力清单：与 C 端刻意不同，让人一眼看出这是内部系统 */
const ADMIN_FEATURES = [
  '会话、客户、工单、知识库与 AI 能力集中管理',
  '提示词与检索参数在线调整，改完即可生效',
  '普通用户与后台账号完全隔离，敏感操作全程留痕'
]

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const captchaVisible = ref(false)
const errorMsg = ref('')
const remember = ref(false)
const isMock = isMockEnabled()

const loginForm = reactive({
  username: isMock ? 'admin' : '',
  password: isMock ? 'password' : ''
})

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const fill = (username) => {
  loginForm.username = username
  loginForm.password = 'password'
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (!valid) return
    captchaVisible.value = true
  })
}

/** 点选验证通过后再调登录接口 */
const onCaptchaSuccess = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password,
      loginType: LOGIN_TYPE_ADMIN
    }, { silent: true })
    if (res.code === 200) {
      const data = { ...res.data, loginType: LOGIN_TYPE_ADMIN }
      saveSession(data)
      router.push(homeAfterLogin(LOGIN_TYPE_ADMIN))
    } else {
      errorMsg.value = res.msg || '登录失败'
    }
  } catch (e) {
    errorMsg.value = e?.msg || e?.response?.data?.msg || e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
