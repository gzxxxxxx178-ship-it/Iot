import request from './request'

// 用户名密码登录：POST /api/auth/login，认证Cookie由服务端写入
export function login(data) {
  return request.post('/api/auth/login', data)
}

// 用户注册：POST /api/auth/register，认证Cookie由服务端写入
export function register(data) {
  return request.post('/api/auth/register', data)
}

// 退出登录：POST /api/auth/logout，清除服务端HttpOnly认证Cookie
export function logout() {
  return request.post('/api/auth/logout')
}

// 获取当前登录用户信息：GET /api/auth/me，返回 {username, createdAt}
export function getMe() {
  return request.get('/api/auth/me')
}
