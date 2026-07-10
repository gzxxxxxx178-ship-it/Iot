import request from './request'

// 用户名密码登录：POST /api/auth/login，返回 {token, username}
export function login(data) {
  return request.post('/api/auth/login', data).then((res) => res.data)
}

// 用户注册：POST /api/auth/register，返回 {token, username}
export function register(data) {
  return request.post('/api/auth/register', data).then((res) => res.data)
}

// 获取当前登录用户信息：GET /api/auth/me，返回 {username, createdAt}
export function getMe() {
  return request.get('/api/auth/me').then((res) => res.data)
}
