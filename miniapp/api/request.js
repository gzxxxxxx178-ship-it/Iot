import { APP_CONFIG } from '../config/index.js'
import { session, clearSession, ensureSession } from '../utils/session.js'

// 创建可被页面忽略的过期请求错误。
export function staleError() { const error = new Error('会话已切换，请重试'); error.stale = true; return error }

// 将固定接口的查询参数编码到 URL，避免将查询条件错误放入请求体。
function serializeParams(params) {
  if (!params || typeof params !== 'object') return ''
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== null)
  if (!entries.length) return ''
  return '?' + entries.map(([key, value]) => encodeURIComponent(key) + '=' + encodeURIComponent(String(value))).join('&')
}

// 创建包含HTTP状态码的请求错误，供刷新会话判断凭据是否确定失效。
function responseError(message, statusCode) { const error = new Error(message); error.statusCode = statusCode; return error }

// 统一请求固定后端接口，并在访问令牌过期时轮换会话后重试一次。
export function request(path, { method = 'GET', data, params, anonymous = false } = {}) {
  if (!/^\/(api|esp)\//.test(path) || path.includes('..') || path.includes('?')) return Promise.reject(new Error('接口路径无效'))
  if (APP_CONFIG.mode !== 'live' || !APP_CONFIG.apiBaseUrl) return Promise.reject(new Error('请先配置真实后端'))
  if (anonymous) return performRequest(path, { method, data, params, anonymous }, 0)
  if (session.token && session.accessExpiresAt > Date.now() + 60000) return performRequest(path, { method, data, params, anonymous }, 0)
  return ensureSession().then((ready) => {
    if (!ready || !session.token) throw new Error('请先登录账号')
    return performRequest(path, { method, data, params, anonymous }, 0)
  })
}

// 执行一次受认证请求，并隔离账号、服务地址和迟到响应。
function performRequest(path, options, retryCount) {
  const { method, data, params, anonymous } = options
  const generation = session.generation
  const requestToken = session.token
  const base = APP_CONFIG.apiBaseUrl
  const header = { 'Content-Type': 'application/json' }
  if (!anonymous) header.Authorization = 'Bearer ' + requestToken
  return new Promise((resolve, reject) => {
    uni.request({ url: base + path + serializeParams(params), method, data, header, timeout: 15000, withCredentials: false,
      // 只接受仍属于当前会话和地址的响应。
      success(response) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl || APP_CONFIG.mode !== 'live') return reject(staleError())
        const body = response.data
        if (response.statusCode === 401 || body?.code === 401) {
          if (!anonymous && retryCount === 0) {
            const refresh = requestToken !== session.token ? Promise.resolve(true) : ensureSession(true)
            return refresh.then((ready) => {
              if (generation !== session.generation) {
                if (!session.token) throw responseError('登录已过期，请重新登录', 401)
                throw staleError()
              }
              if (!ready || !session.token) {
                clearSession()
                throw responseError('登录已过期，请重新登录', 401)
              }
              return performRequest(path, options, retryCount + 1)
            }).then(resolve, reject)
          }
          if (!anonymous) clearSession()
          return reject(responseError(anonymous ? (path === '/api/auth/refresh' ? '登录已过期，请重新登录' : '用户名或密码错误') : '登录已过期，请重新登录', response.statusCode || 401))
        }
        if (response.statusCode < 200 || response.statusCode >= 300 || body?.code !== 200) return reject(responseError(body?.message || '请求失败（HTTP ' + response.statusCode + '）', response.statusCode))
        resolve(body.data)
      },
      // 将平台异常转为可操作的连接提示，并保留刷新凭据以便网络恢复后重试。
      fail(error) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl) return reject(staleError())
        reject(responseError(error?.errMsg?.includes('timeout') ? '连接超时，请检查后端地址和网络' : '连接失败，请检查后端服务、网络及开发工具域名设置', 0))
      }
    })
  })
}

// 下载仅限当前账号可访问的文件，支持访问令牌过期后的单次刷新重试。
export function downloadAuthenticatedFile(path, { params, timeout = 60000 } = {}) {
  if (!/^\/(api|esp)\//.test(path) || path.includes('..') || path.includes('?')) return Promise.reject(new Error('接口路径无效'))
  if (APP_CONFIG.mode !== 'live' || !APP_CONFIG.apiBaseUrl) return Promise.reject(new Error('请先配置真实后端'))
  if (session.token && session.accessExpiresAt > Date.now() + 60000) return performDownload(path, params, timeout, 0)
  return ensureSession().then((ready) => {
    if (!ready || !session.token) throw new Error('请先登录账号')
    return performDownload(path, params, timeout, 0)
  })
}

// 执行一次认证文件下载，并隔离会话变更和旧账号响应。
function performDownload(path, params, timeout, retryCount) {
  const generation = session.generation
  const requestToken = session.token
  const base = APP_CONFIG.apiBaseUrl
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: base + path + serializeParams(params),
      header: { Authorization: 'Bearer ' + requestToken },
      timeout,
      // 检查文件下载响应并在过期时刷新后仅重试一次。
      success(response) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl || APP_CONFIG.mode !== 'live') return reject(staleError())
        if (response.statusCode === 401 && retryCount === 0) {
          const refresh = requestToken !== session.token ? Promise.resolve(true) : ensureSession(true)
          return refresh.then((ready) => {
            if (generation !== session.generation) {
              if (!session.token) throw responseError('登录已过期，请重新登录', 401)
              throw staleError()
            }
            if (!ready || !session.token) {
              clearSession()
              throw responseError('登录已过期，请重新登录', 401)
            }
            return performDownload(path, params, timeout, retryCount + 1)
          }).then(resolve, reject)
        }
        if (response.statusCode === 401) {
          clearSession()
          return reject(responseError('登录已过期，请重新登录', 401))
        }
        if (response.statusCode < 200 || response.statusCode >= 300 || !response.tempFilePath) {
          return reject(responseError('文件下载失败（HTTP ' + response.statusCode + '）', response.statusCode))
        }
        resolve({ tempFilePath: response.tempFilePath, statusCode: response.statusCode })
      },
      // 连接中断时保留可用刷新凭据，等待用户重试。
      fail(error) {
        if (generation !== session.generation || base !== APP_CONFIG.apiBaseUrl) return reject(staleError())
        reject(responseError(error?.errMsg?.includes('timeout') ? '文件下载超时，请缩小查询范围后重试' : '文件下载失败，请检查后端服务和网络', 0))
      }
    })
  })
}
