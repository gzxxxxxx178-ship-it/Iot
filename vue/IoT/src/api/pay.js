import request from './request'

export function createOrder(data) {
  return request.post('/api/alipay/create', data).then((res) => res.data)
}

export function queryOrder(outTradeNo) {
  return request.get('/api/alipay/query', { params: { outTradeNo } }).then((res) => res.data)
}
