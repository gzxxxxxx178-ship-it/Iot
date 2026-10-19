import { APP_CONFIG } from '../config/index.js'
import { session, clearSession } from '../utils/session.js'

// 创建可被页面忽略的过期请求错误。
export function staleError() { const error = new Error('会话已切换，请重试'); error.stale = true; return error }

// 将固定接口的查询参数编码到 URL，避免将查询条件错误放入请求体。
function serializeParams(params) {
  if (!params || typeof params !== 'object') return ''
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== null)
  if (!entries.length) return ''
  return '?' + entries.map(([key, value]) => encodeURIComponent(key) + '=' + encodeURIComponent(String(value))).join('&')
}

// 统一请求固定后端接口，并校验会话、HTTP和业务状态。
export function request(path, { method = 'GET', data, params, anonymous = false } = {}) {
  if (!/^\/(api|esp)\//.test(path) || path.includes('..') || path.includes('?')) return Promise.reject(new Error('接口路径无效'))
  if (APP_CONFIG.mode !== 'live' || !APP_CONFIG.apiBaseUrl) return Promise.reject(new Error('请先配置真实后端'))
  if (!anonymous && !session.token) return Promise.reject(new Error('请先登录账号'))
  const generation = session.generation
  const base = APP_CONFIG.apiBaseUrl
  const header = { 'Content-Type': 'application/json' }
  if (!anonymous) header.Authorization = 'Bearer ' + session.token
  return new Promise((resolve, reject) => {
    uni.request({ url: base + path + serializeParams(params), method, data, header, timeout: 15000, withCredentials: false,
      // 只接受仍属于当前会话和地址的响应。
      success(response) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl || APP_CONFIG.mode !== 'live') return reject(staleError())
        const body = response.data
        if (response.statusCode === 401 || body?.code === 401) {
          if (!anonymous) clearSession()
          return reject(new Error(anonymous ? '用户名或密码错误' : '登录已过期，请重新登录'))
        }
        if (response.statusCode < 200 || response.statusCode >= 300 || body?.code !== 200) return reject(new Error(body?.message || '请求失败（HTTP ' + response.statusCode + '）'))
        resolve(body.data)
      },
      // 将平台异常转为可操作的连接提示。
      fail(error) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl) return reject(staleError())
        reject(new Error(error?.errMsg?.includes('timeout') ? '连接超时，请检查后端地址和网络' : '连接失败，请检查后端服务、网络及开发工具域名设置'))
      }
    })
  })
}

// 下载仅限当前账号可访问的文件，并沿用统一请求的会话隔离与401处理。
export function downloadAuthenticatedFile(path, { params, timeout = 60000 } = {}) {
  if (!/^\/(api|esp)\//.test(path) || path.includes('..') || path.includes('?')) return Promise.reject(new Error('接口路径无效'))
  if (APP_CONFIG.mode !== 'live' || !APP_CONFIG.apiBaseUrl) return Promise.reject(new Error('请先配置真实后端'))
  if (!session.token) return Promise.reject(new Error('请先登录账号'))
  const generation = session.generation
  const base = APP_CONFIG.apiBaseUrl
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: base + path + serializeParams(params),
      header: { Authorization: 'Bearer ' + session.token },
      timeout,
      success(response) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl || APP_CONFIG.mode !== 'live') return reject(staleError())
        if (response.statusCode === 401) {
          clearSession()
          return reject(new Error('登录已过期，请重新登录'))
        }
        if (response.statusCode < 200 || response.statusCode >= 300 || !response.tempFilePath) {
          return reject(new Error('文件下载失败（HTTP ' + response.statusCode + '）'))
        }
        resolve({ tempFilePath: response.tempFilePath, statusCode: response.statusCode })
      },
      fail(error) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl) return reject(staleError())
        reject(new Error(error?.errMsg?.includes('timeout') ? '文件下载超时，请缩小查询范围后重试' : '文件下载失败，请检查后端服务和网络'))
      }
    })
  })
}
