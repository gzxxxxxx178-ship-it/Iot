import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, logout as logoutApi, getMe } from '../api/auth'
import { getUsername as getStoredUsername, setUsername, removeUsername } from '../utils/auth'

/**
 * 认证状态管理 — 统一管理 token/username 和登录/注册/登出操作
 *
 * JWT由后端HttpOnly Cookie保存；前端只保存当前会话用户名。
 */
export const useAuthStore = defineStore('auth', () => {
  // ==================== State ====================

  /** 认证状态由启动时的 /me 校验结果确定 */
  const token = ref('cookie')

  /** 当前用户名 */
  const username = ref(getStoredUsername() || '')
  const authenticated = ref(false)

  // ==================== Getters ====================

  /** 是否已登录 */
  const isLoggedIn = computed(() => authenticated.value)

  // ==================== Actions ====================

  // 用户名密码登录：调 API → 存储 token/username
  async function login(credentials) {
    const res = await loginApi(credentials)
    username.value = res.username
    setUsername(res.username)
    authenticated.value = true
  }

  // 用户注册：调 API → 自动登录
  async function register(credentials) {
    const res = await registerApi(credentials)
    username.value = res.username
    setUsername(res.username)
    authenticated.value = true
  }

  // 退出登录：清除 token/username
  async function logout() {
    try { await logoutApi() } catch {}
    token.value = 'cookie'
    username.value = ''
    authenticated.value = false
    removeUsername()
  }

  // 从HttpOnly Cookie校验并恢复登录态
  async function restore() {
    try {
      const me = await getMe()
      username.value = me.username || ''
      setUsername(username.value)
      authenticated.value = true
      return true
    } catch {
      authenticated.value = false
      return false
    }
  }

  return { token, username, isLoggedIn, login, register, logout, restore }
})
