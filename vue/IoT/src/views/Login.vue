<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login as loginApi } from '../api/auth'
import { setToken, setUsername } from '../utils/auth'

import { onMounted } from 'vue'

const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

// 表单校验规则：用户名和密码均不能为空
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 检测 URL 中是否有 OAuth 登录失败错误信息
onMounted(() => {
  if (route.query.oauth_error) {
    errorMsg.value = 'Google 登录失败: ' + route.query.oauth_error
  }
})

// Google OAuth2 登录：跳转到后端 /oauth2/authorization/google 触发 Spring Security 授权流程
function googleLogin() {
  const base = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? 'http://localhost:8080' : window.location.origin)
  window.location.href = base + '/oauth2/authorization/google'
}

// 表单提交：Element Plus 校验 → 调用登录 API → 存储 JWT → 跳转 Dashboard
async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return // 校验不通过，Element Plus 会自动显示错误提示
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await loginApi({ username: form.username, password: form.password })
    setToken(res.token)
    setUsername(res.username)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.push(redirect || '/dashboard')
  } catch (e) {
    errorMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <span class="auth-logo">&#x1F33E;</span>
        <h2>智慧农业 IoT 系统</h2>
        <p>登录您的账号</p>
      </div>

      <!-- 服务端错误提示：认证失败或 OAuth 回调错误 -->
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" class="auth-alert" />

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="onSubmit" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keydown.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="success" size="large" class="auth-btn" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
      </el-form>

      <div class="oauth-divider">
        <span>或</span>
      </div>

      <el-button class="google-btn" @click="googleLogin">
        <svg class="google-icon" viewBox="0 0 24 24" width="18" height="18">
          <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
          <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
          <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
          <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
        </svg>
        使用 Google 登录
      </el-button>

      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
}

.auth-card {
  width: 400px;
  padding: 2.5rem 2rem;
  background: rgba(30, 41, 59, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  backdrop-filter: blur(12px);
}

.auth-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.auth-logo {
  font-size: 2.5rem;
}

.auth-header h2 {
  margin: 0.5rem 0 0.25rem;
  font-size: 1.3rem;
}

.auth-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.auth-alert {
  margin-bottom: 1rem;
}

.auth-btn {
  width: 100%;
  margin-top: 0.5rem;
}

.oauth-divider {
  display: flex;
  align-items: center;
  margin: 1.25rem 0;
  color: var(--text-muted);
  font-size: 0.8rem;
}

.oauth-divider::before,
.oauth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
}

.oauth-divider span {
  padding: 0 1rem;
}

.google-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  background: rgba(255, 255, 255, 0.06) !important;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  color: var(--text-primary) !important;
  height: 44px;
  font-size: 0.9rem;
}

.google-btn:hover {
  background: rgba(255, 255, 255, 0.1) !important;
  border-color: rgba(255, 255, 255, 0.2) !important;
}

.auth-footer {
  text-align: center;
  margin-top: 1.25rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.auth-footer a {
  color: var(--color-green);
  text-decoration: none;
  font-weight: 500;
}

.auth-footer a:hover {
  text-decoration: underline;
}
</style>
