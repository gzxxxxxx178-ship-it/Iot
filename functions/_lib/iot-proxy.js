const IOT_ORIGIN = 'https://node.61153652.xyz:8443'

// 将 Pages 同源请求转发到 IoT 后端，保留原始方法、请求体、查询参数与认证 Cookie。
export function proxyIotRequest(request) {
  const incomingUrl = new URL(request.url)
  const targetUrl = new URL(IOT_ORIGIN)
  targetUrl.pathname = incomingUrl.pathname
  targetUrl.search = incomingUrl.search
  return fetch(new Request(targetUrl, request))
}
