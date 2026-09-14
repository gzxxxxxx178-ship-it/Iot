import { proxyIotRequest } from '../../_lib/iot-proxy'

// 代理 OAuth2 回调入口，使后端写入的认证响应返回给 Pages 页面。
export function onRequest(context) {
  return proxyIotRequest(context.request)
}
