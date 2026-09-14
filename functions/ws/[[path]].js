import { proxyIotRequest } from '../_lib/iot-proxy'

// 代理 WebSocket 升级请求，沿用与 HTTP API 相同的同源访问路径。
export function onRequest(context) {
  return proxyIotRequest(context.request)
}
