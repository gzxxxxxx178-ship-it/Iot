import { proxyIotRequest } from '../_lib/iot-proxy'

// 代理 OAuth2 授权入口，确保授权流程始终从 Pages 的标准 HTTPS 域名发起。
export function onRequest(context) {
  return proxyIotRequest(context.request)
}
