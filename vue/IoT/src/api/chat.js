import request from './request'
import { getSessionId } from '../utils/session'

// 发送 AI 对话消息：POST /api/chat，携带 sessionId 和完整对话历史，返回 AI 回复
export function sendMessage(messages) {
  return request
    .post('/api/chat', { sessionId: getSessionId(), messages })
    .then((res) => res.data)
}

// 加载当前会话的聊天历史：GET /api/chat/history?sessionId=xxx
export function getHistory() {
  return request
    .get('/api/chat/history', { params: { sessionId: getSessionId() } })
    .then((res) => res.data)
}

// 清除当前会话的聊天记录：DELETE /api/chat/history?sessionId=xxx
export function clearHistory() {
  return request
    .delete('/api/chat/history', { params: { sessionId: getSessionId() } })
    .then((res) => res.data)
}
