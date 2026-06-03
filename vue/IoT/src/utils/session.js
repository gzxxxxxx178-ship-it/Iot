const SESSION_KEY = 'iot_chat_session_id'

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 10)
}

export function getSessionId() {
  let id = localStorage.getItem(SESSION_KEY)
  if (!id) {
    id = generateId()
    localStorage.setItem(SESSION_KEY, id)
  }
  return id
}
