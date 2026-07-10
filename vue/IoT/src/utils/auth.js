const TOKEN_KEY = 'token'
const USERNAME_KEY = 'username'

// 从 localStorage 获取 JWT token
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

// 存储 JWT token 到 localStorage
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

// 清除 localStorage 中的 JWT token
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

// 从 localStorage 获取用户名
export function getUsername() {
  return localStorage.getItem(USERNAME_KEY)
}

// 存储用户名到 localStorage
export function setUsername(username) {
  localStorage.setItem(USERNAME_KEY, username)
}

// 清除 localStorage 中的用户名
export function removeUsername() {
  localStorage.removeItem(USERNAME_KEY)
}
