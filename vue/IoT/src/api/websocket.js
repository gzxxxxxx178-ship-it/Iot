import request from './request'

// 通过受JWT保护的REST接口领取短期一次性WebSocket握手票据
export function createWebSocketTicket() {
  return request.post('/api/ws/ticket')
}
