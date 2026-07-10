<script setup>
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// Google OAuth2 回调处理：从 URL query 提取 JWT → Pinia setAuth() → 跳转 Dashboard
onMounted(() => {
  const { token, username } = route.query
  if (token) {
    authStore.setAuth(token, username || '')
    router.push('/dashboard')
  } else {
    // 无 token 说明回调异常，跳回登录页
    router.push('/login')
  }
})
</script>

<template>
  <div class="callback-page">
    <div class="callback-loading">
      <span class="logo">&#x1F33E;</span>
      <p>登录中，请稍候...</p>
    </div>
  </div>
</template>

<style scoped>
.callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
}

.callback-loading {
  text-align: center;
}

.logo {
  font-size: 3rem;
  display: block;
  margin-bottom: 1rem;
  animation: bounce 0.6s infinite alternate;
}

@keyframes bounce {
  from { transform: translateY(0); }
  to { transform: translateY(-10px); }
}

.callback-loading p {
  color: var(--text-secondary);
  font-size: 0.9rem;
}
</style>
