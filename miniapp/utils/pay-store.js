import { request } from '../api/request.js'

// 在真实后端创建支付宝沙箱预下单并返回订单号与二维码内容。
export function createPaymentOrder(amount, subject) {
  return request('/api/alipay/create', { method: 'POST', data: { amount, subject } })
}

// 查询当前账号所属订单的支付状态。
export function queryPaymentOrder(outTradeNo) {
  return request('/api/alipay/query', { params: { outTradeNo } })
}
