import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, getMe } from '../api/auth'
import { getToken as getStoredToken, getUsername as getStoredUsername } from '../utils/auth'

/**
 * 认证状态管理 — 统一管理 token/username 和登录/注册/登出操作
 *
 * 所有组件通过此 Store 访问认证状态，不再直接操作 localStorage。
 * utils/auth.js 中的基础读写函数保留，供 axios 拦截器等非组件上下文使用。
 */
export const useAuthStore = defineStore('auth', () => {
  // ==================== State ====================

  /** JWT token（持久化到 localStorage） */
  const token = ref(getStoredToken() || '')

  /** 当前用户名 */
  const username = ref(getStoredUsername() || '')

  // ==================== Getters ====================

  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value)

  // ==================== Actions ====================

  // 用户名密码登录：调 API → 存储 token/username
  async function login(credentials) {
    const res = await loginApi(credentials)
    token.value = res.token
    username.value = res.username
    localStorage.setItem('token', res.token)
    localStorage.setItem('username', res.username)
  }

  // 用户注册：调 API → 自动登录
  async function register(credentials) {
    const res = await registerApi(credentials)
    token.value = res.token
    username.value = res.username
    localStorage.setItem('token', res.token)
    localStorage.setItem('username', res.username)
  }

  // 退出登录：清除 token/username
  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  // 从 URL 参数设置认证信息（OAuth 回调使用）
  function setAuth(newToken, newUsername) {
    token.value = newToken
    username.value = newUsername
    localStorage.setItem('token', newToken)
    localStorage.setItem('username', newUsername)
  }

  // 从 localStorage 恢复登录态（应用启动时调用）
  function restore() {
    const t = getStoredToken()
    const u = getStoredUsername()
    if (t) {
      token.value = t
      username.value = u || ''
    }
  }

  return { token, username, isLoggedIn, login, register, logout, setAuth, restore }
})
