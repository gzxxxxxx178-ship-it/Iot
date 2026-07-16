const TOKEN_KEY = 'token'
const USERNAME_KEY = 'username'

// JWT由HttpOnly Cookie保存，脚本不读取令牌
export function getToken() {
  return ''
}

// 兼容旧调用方：令牌不再写入Web Storage
export function setToken(token) {
  void token
}

// 清除兼容键，真实令牌由后端logout接口清除
export function removeToken() {
  sessionStorage.removeItem(TOKEN_KEY)
}

// 从会话存储获取非敏感用户名
export function getUsername() {
  return sessionStorage.getItem(USERNAME_KEY)
}

// 保存非敏感用户名到当前浏览器会话
export function setUsername(username) {
  sessionStorage.setItem(USERNAME_KEY, username)
}

// 清除当前浏览器会话用户名
export function removeUsername() {
  sessionStorage.removeItem(USERNAME_KEY)
}
