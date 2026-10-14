import { reactive } from 'vue'

// 校验后端根地址，拒绝凭据、路径和查询参数。
export function normalizeBaseUrl(value) {
  const text = String(value || '').trim().replace(/\/+$/, '')
  if (!text) return ''
  if (!/^https?:\/\/(?:[a-z0-9][a-z0-9.-]*|\[[a-f0-9:]+\])(?::\d{1,5})?$/i.test(text)) throw new Error('请输入后端根地址，例如 http://localhost:8080，不含路径和账号密码')
  const port = text.match(/:(\d+)$/)
  if (port && (Number(port[1]) < 1 || Number(port[1]) > 65535)) throw new Error('端口需在 1–65535 之间')
  return text
}

// 读取连接地址；旧版本的演示模式配置自动迁移为真实接口。
function readConfig() {
  try {
    const saved = uni.getStorageSync('iot-miniapp-connection') || {}
    return { mode: 'live', apiBaseUrl: normalizeBaseUrl(saved.apiBaseUrl) || 'http://localhost:8080' }
  } catch { return { mode: 'live', apiBaseUrl: 'http://localhost:8080' } }
}
export const APP_CONFIG = reactive(readConfig())

// 仅保存真实后端地址，不保存密码和令牌。
export function saveConfig(mode, address) {
  if (mode !== 'live') throw new Error('演示模式已移除')
  const apiBaseUrl = normalizeBaseUrl(address)
  if (!apiBaseUrl) throw new Error('请先填写后端地址')
  const settings = { mode: 'live', apiBaseUrl }
  uni.setStorageSync('iot-miniapp-connection', settings)
  Object.assign(APP_CONFIG, settings)
}
