import request from './request'

// 创建支付订单：POST /api/alipay/create，返回 {outTradeNo, qrCode}
export function createOrder(data) {
  return request.post('/api/alipay/create', data).then((res) => res.data)
}

// 查询支付状态：GET /api/alipay/query?outTradeNo=xxx，返回 {status, amount, tradeNo}
export function queryOrder(outTradeNo) {
  return request.get('/api/alipay/query', { params: { outTradeNo } }).then((res) => res.data)
}
