// 解析后端基址，路径相对站点根目录；禁止把凭据、查询串或片段混入配置。
function normalizeBase(value, origin, protocols) {
  const url = new URL(value, `${origin}/`)
  if (!protocols.includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('后端地址必须使用支持的协议，且不能包含凭据、查询参数或片段')
  }
  return url.href.replace(/\/+$/, '')
}

// HTTP、OAuth与实时连接共用默认后端，显式WS配置可独立覆盖并保留反向代理路径前缀。
export function resolveBackendUrls(env = {}, origin) {
  const apiBaseUrl = normalizeBase(env.VITE_API_BASE_URL?.trim()
    || (env.DEV ? 'http://localhost:8080' : origin), origin, ['http:', 'https:'])
  const webSocketBaseUrl = normalizeBase(env.VITE_WS_BASE_URL?.trim() || apiBaseUrl,
    origin, ['http:', 'https:', 'ws:', 'wss:']).replace(/^http/, 'ws')
  return { apiBaseUrl, webSocketBaseUrl }
}

// 在浏览器运行时读取Vite配置及当前站点，供请求层、登录入口和composable共同使用。
export function getBackendUrls() {
  return resolveBackendUrls(import.meta.env, window.location.origin)
}
