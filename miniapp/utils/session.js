import { reactive } from 'vue'
export const session = reactive({ username: '', token: '', generation: 0 })
const resetHandlers = new Set()

// 登记会话失效时同步清除的数据。
export function onSessionReset(handler) { resetHandlers.add(handler) }

// 清除认证内存，使此前所有请求失效。
export function clearSession() {
  session.generation += 1
  session.username = ''; session.token = ''
  for (const handler of resetHandlers) handler()
}

// 设置当前会话令牌，拒绝迟到的登录响应。
export function acceptSession(generation, data) {
  if (generation !== session.generation) return false
  if (!data || typeof data.token !== 'string' || !data.token || typeof data.username !== 'string') throw new Error('登录响应不完整')
  session.token = data.token; session.username = data.username
  return true
}
