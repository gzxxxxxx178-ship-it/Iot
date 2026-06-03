import request from './request'

export function login(data) {
  return request.post('/api/auth/login', data).then((res) => res.data)
}

export function register(data) {
  return request.post('/api/auth/register', data).then((res) => res.data)
}

export function getMe() {
  return request.get('/api/auth/me').then((res) => res.data)
}
