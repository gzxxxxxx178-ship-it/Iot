import { reactive } from 'vue'

// 校验后端根地址，拒绝凭据、查询参数和路径。
export function normalizeBaseUrl(value) {
  const text = String(value || '').trim().replace(/\/+$/, '')
  if (!text) return ''
  if (!/^https?:\/\/(?:[a-z0-9][a-z0-9.-]*|\[[a-f0-9:]+\])(?::\d{1,5})?$/i.test(text)) throw new Error('请输入后端根地址，例如 http://localhost:8080，不含路径和账号密码')
  const port = text.match(/:(\d+)$/)
  if (port && (Number(port[1]) < 1 || Number(port[1]) > 65535)) throw new Error('端口需在 1–65535 之间')
  return text
}

// 读取连接设置；无效设置回到演示模式。
function readConfig() {
  try {
    const saved = uni.getStorageSync('iot-miniapp-connection') || {}
    const apiBaseUrl = normalizeBaseUrl(saved.apiBaseUrl)
    return { mode: saved.mode === 'live' && apiBaseUrl ? 'live' : 'demo', apiBaseUrl }
  } catch { return { mode: 'demo', apiBaseUrl: '' } }
}
export const APP_CONFIG = reactive(readConfig())

// 判断是否使用本地演示数据。
export function isDemoMode() { return APP_CONFIG.mode === 'demo' }

// 仅持久化模式和地址，不保存认证信息。
export function saveConfig(mode, address) {
  const apiBaseUrl = normalizeBaseUrl(address)
  if (mode === 'live' && !apiBaseUrl) throw new Error('请先填写后端地址')
  const settings = { mode: mode === 'live' ? 'live' : 'demo', apiBaseUrl }
  uni.setStorageSync('iot-miniapp-connection', settings)
  Object.assign(APP_CONFIG, settings)
}
