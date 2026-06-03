import request from './request'
import { getSessionId } from '../utils/session'

export function sendMessage(messages) {
  return request
    .post('/api/chat', { sessionId: getSessionId(), messages })
    .then((res) => res.data)
}

export function getHistory() {
  return request
    .get('/api/chat/history', { params: { sessionId: getSessionId() } })
    .then((res) => res.data)
}

export function clearHistory() {
  return request
    .delete('/api/chat/history', { params: { sessionId: getSessionId() } })
    .then((res) => res.data)
}
