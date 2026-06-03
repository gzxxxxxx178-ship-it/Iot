<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { register as registerApi } from '../api/auth'
import { setToken, setUsername } from '../utils/auth'

const router = useRouter()

const form = reactive({ username: '', password: '', confirmPassword: '' })
const loading = ref(false)
const errorMsg = ref('')

async function onSubmit() {
  if (!form.username || !form.password) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  if (form.password.length < 6) {
    errorMsg.value = '密码长度不能少于 6 位'
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await registerApi({ username: form.username, password: form.password })
    setToken(res.token)
    setUsername(res.username)
    ElMessage.success('注册成功')
    router.push('/dashboard')
  } catch (e) {
    errorMsg.value = typeof e.response?.data === 'string' ? e.response.data : '注册失败'
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
        <p>注册新账号</p>
      </div>

      <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" class="auth-alert" />

      <el-form @submit.prevent="onSubmit" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少 6 位密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keydown.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="success" size="large" class="auth-btn" :loading="loading" @click="onSubmit">
          注 册
        </el-button>
      </el-form>

      <div class="auth-footer">
        已有账号？<router-link to="/login">返回登录</router-link>
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
