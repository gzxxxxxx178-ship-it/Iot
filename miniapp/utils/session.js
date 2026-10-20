import { reactive } from 'vue'
const refreshStorageKey = 'miniapp.auth.refreshToken'
export const session = reactive({ username: '', token: '', generation: 0, accessExpiresAt: 0 })
const resetHandlers = new Set()
let refreshHandler = null
let refreshPromise = null

// 登记会话失效时同步清除的数据。
export function onSessionReset(handler) { resetHandlers.add(handler) }

// 注册刷新会话处理器，避免会话模块依赖网络请求模块。
export function setSessionRefreshHandler(handler) { refreshHandler = handler }

// 读取本机保存的刷新凭据。
export function getStoredRefreshToken() {
  try {
    const token = uni.getStorageSync(refreshStorageKey)
    return typeof token === 'string' ? token : ''
  } catch { return '' }
}

// 删除本机刷新凭据，兼容未实现 removeStorageSync 的测试替身。
function removeStoredRefreshToken() {
  try {
    if (typeof uni.removeStorageSync === 'function') uni.removeStorageSync(refreshStorageKey)
    else uni.setStorageSync(refreshStorageKey, '')
  } catch {}
}

// 清除认证内存，使此前所有请求失效。
export function clearSession() {
  session.generation += 1
  session.username = ''; session.token = ''; session.accessExpiresAt = 0
  removeStoredRefreshToken()
  for (const handler of resetHandlers) handler()
}

// 设置当前会话令牌，拒绝迟到的登录响应。
export function acceptSession(generation, data) {
  if (generation !== session.generation) return false
  if (!data || typeof data.token !== 'string' || !data.token || typeof data.username !== 'string') throw new Error('登录响应不完整')
  if (data.refreshToken && (!Number.isFinite(Number(data.expiresIn)) || Number(data.expiresIn) <= 0)) throw new Error('登录响应有效期无效')
  session.token = data.token; session.username = data.username
  session.accessExpiresAt = Number.isFinite(Number(data.expiresIn)) && Number(data.expiresIn) > 0
    ? Date.now() + Number(data.expiresIn) * 1000 : Number.POSITIVE_INFINITY
  if (typeof data.refreshToken === 'string' && data.refreshToken) {
    try { uni.setStorageSync(refreshStorageKey, data.refreshToken) } catch {}
  }
  return true
}

// 确保存在有效访问令牌，必要时使用刷新凭据轮换会话。
export async function ensureSession(forceRefresh = false) {
  if (!forceRefresh && session.token && session.accessExpiresAt > Date.now() + 60000) return true
  const refreshToken = getStoredRefreshToken()
  if (!refreshToken || !refreshHandler) return forceRefresh ? false : Boolean(session.token)
  if (refreshPromise) return refreshPromise
  const generation = session.generation
  refreshPromise = Promise.resolve().then(() => refreshHandler(refreshToken)).then((data) => {
    if (!data?.refreshToken) throw new Error('刷新接口未返回新凭据，请更新 Java 后端')
    if (!acceptSession(generation, data)) throw staleSessionError()
    return true
  }).catch((error) => {
    if (generation === session.generation && (error.statusCode === 401 || error.statusCode === 400)) {
      clearSession()
    }
    throw error
  }).finally(() => { refreshPromise = null })
  return refreshPromise
}

// 创建可被调用方识别并忽略的迟到会话错误。
function staleSessionError() { const error = new Error('会话已切换，请重试'); error.stale = true; return error }
