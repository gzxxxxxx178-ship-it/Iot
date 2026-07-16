import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, logout as logoutApi, getMe } from '../api/auth'
import { setMemoryAccessToken } from '../api/request'
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
  const initialized = ref(false)
  let restorePromise = null

  // ==================== Getters ====================

  /** 是否已登录 */
  const isLoggedIn = computed(() => authenticated.value)

  // ==================== Actions ====================

  // 用户名密码登录：调 API → 存储 token/username
  async function login(credentials) {
    const res = await loginApi(credentials)
    token.value = res.token || 'cookie'
    setMemoryAccessToken(res.token)
    username.value = res.username
    setUsername(res.username)
    authenticated.value = true
    initialized.value = true
  }

  // 用户注册：调 API → 自动登录
  async function register(credentials) {
    const res = await registerApi(credentials)
    token.value = res.token || 'cookie'
    setMemoryAccessToken(res.token)
    username.value = res.username
    setUsername(res.username)
    authenticated.value = true
    initialized.value = true
  }

  // 清除内存和会话存储中的认证状态，供退出登录和401处理复用
  function clearAuthentication() {
    token.value = 'cookie'
    setMemoryAccessToken('')
    username.value = ''
    authenticated.value = false
    initialized.value = true
    removeUsername()
  }

  // 退出登录：清除服务端Cookie及本地认证状态
  async function logout() {
    try { await logoutApi() } catch {}
    clearAuthentication()
  }

  // 首次进入应用时从HttpOnly Cookie恢复登录态，后续路由切换复用校验结果
  async function restore(force = false) {
    if (initialized.value && !force) return authenticated.value
    if (restorePromise) return restorePromise

    restorePromise = (async () => {
      try {
        const me = await getMe()
        token.value = 'cookie'
        setMemoryAccessToken('')
        username.value = me.username || ''
        setUsername(username.value)
        authenticated.value = true
        initialized.value = true
        return true
      } catch {
        clearAuthentication()
        return false
      }
    })()

    try {
      return await restorePromise
    } finally {
      restorePromise = null
    }
  }

  return {
    token,
    username,
    initialized,
    isLoggedIn,
    login,
    register,
    logout,
    restore,
    clearAuthentication,
  }
})
