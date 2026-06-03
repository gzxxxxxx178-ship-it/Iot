<script setup>
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { setToken, setUsername } from '../utils/auth'

const router = useRouter()
const route = useRoute()

onMounted(() => {
  console.log('[OAuthCallback] route.query:', JSON.stringify(route.query))
  console.log('[OAuthCallback] location.href:', window.location.href)
  const { token, username } = route.query
  if (token) {
    console.log('[OAuthCallback] token received, storing and redirecting to /dashboard')
    setToken(token)
    setUsername(username || '')
    router.push('/dashboard')
  } else {
    console.warn('[OAuthCallback] no token in query, redirecting to /login')
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
