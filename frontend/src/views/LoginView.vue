<template>
  <div class="login-container">
    <n-card class="login-card" title="OpenClaw Orchestrator">
      <n-tabs v-model:value="activeTab" type="line" animated>
        <n-tab-pane name="login" tab="登录">
          <n-form :model="loginForm" @submit.prevent="handleLogin">
            <n-form-item path="username" label="用户名">
              <n-input v-model:value="loginForm.username" placeholder="请输入用户名" />
            </n-form-item>
            <n-form-item path="password" label="密码">
              <n-input v-model:value="loginForm.password" type="password" placeholder="请输入密码" show-password-on="mousedown" />
            </n-form-item>
            <n-button type="primary" block :loading="loading" @click="handleLogin">
              登录
            </n-button>
          </n-form>
        </n-tab-pane>

        <n-tab-pane name="register" tab="注册">
          <n-form :model="registerForm" @submit.prevent="handleRegister">
            <n-form-item path="username" label="用户名">
              <n-input v-model:value="registerForm.username" placeholder="请输入用户名" />
            </n-form-item>
            <n-form-item path="password" label="密码">
              <n-input v-model:value="registerForm.password" type="password" placeholder="请输入密码" show-password-on="mousedown" />
            </n-form-item>
            <n-form-item path="confirmPassword" label="确认密码">
              <n-input v-model:value="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" show-password-on="mousedown" />
            </n-form-item>
            <n-button type="primary" block :loading="loading" @click="handleRegister">
              注册
            </n-button>
          </n-form>
        </n-tab-pane>
      </n-tabs>

      <n-alert v-if="errorMessage" type="error" class="mt-4">
        {{ errorMessage }}
      </n-alert>
    </n-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NTabs, NTabPane, NForm, NFormItem, NInput, NButton, NAlert, useMessage } from 'naive-ui'
import { authApi } from '../api/authApi'

const router = useRouter()
const message = useMessage()
const activeTab = ref('login')
const loading = ref(false)
const errorMessage = ref('')

const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', confirmPassword: '' })

const handleLogin = async () => {
  errorMessage.value = ''
  if (!loginForm.value.username || !loginForm.value.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const response = await authApi.login(loginForm.value.username, loginForm.value.password)
    localStorage.setItem('token', response.token)
    localStorage.setItem('username', response.username)
    message.success('登录成功')
    router.push('/monitor')
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  errorMessage.value = ''
  if (!registerForm.value.username || !registerForm.value.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  if (registerForm.value.password.length < 6) {
    errorMessage.value = '密码至少6位'
    return
  }
  loading.value = true
  try {
    await authApi.register(registerForm.value.username, registerForm.value.password)
    message.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.value.username = registerForm.value.username
    loginForm.value.password = ''
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '注册失败'
  } finally {
    loading.value = false
  }
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
  width: 400px;
  max-width: 90vw;
}
.mt-4 {
  margin-top: 16px;
}
</style>