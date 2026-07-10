const SESSION_KEY = 'iot_chat_session_id'

// 生成唯一会话 ID：时间戳 base36 + 随机数
function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 10)
}

// 获取或创建持久化的聊天会话 ID（localStorage），同一浏览器复用
export function getSessionId() {
  let id = localStorage.getItem(SESSION_KEY)
  if (!id) {
    id = generateId()
    localStorage.setItem(SESSION_KEY, id)
  }
  return id
}
