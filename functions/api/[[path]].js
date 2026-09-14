import { proxyIotRequest } from '../_lib/iot-proxy'

// 代理同源 API 请求，使浏览器无需直接连接 VPS 的非标准 HTTPS 端口。
export function onRequest(context) {
  return proxyIotRequest(context.request)
}
