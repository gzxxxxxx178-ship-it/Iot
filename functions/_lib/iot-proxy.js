const DEFAULT_IOT_ORIGIN = 'https://node.61153652.xyz:8443'

// 将 Pages 同源请求转发到 IoT 后端，保留原始方法、请求体、查询参数与认证 Cookie。
export function proxyIotRequest(request, env = {}) {
  let targetUrl
  try {
    targetUrl = new URL(env.IOT_ORIGIN?.trim() || DEFAULT_IOT_ORIGIN)
    if (targetUrl.protocol !== 'https:' || targetUrl.username || targetUrl.password
        || targetUrl.pathname !== '/' || targetUrl.search || targetUrl.hash) {
      throw new Error('invalid origin')
    }
  } catch {
    return new Response('IoT upstream configuration is invalid', { status: 503 })
  }
  const incomingUrl = new URL(request.url)
  targetUrl.pathname = incomingUrl.pathname
  targetUrl.search = incomingUrl.search
  return fetch(new Request(targetUrl, new Request(request, { redirect: 'manual' })))
}
