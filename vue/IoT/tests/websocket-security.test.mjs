import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const projectRoot = resolve(import.meta.dirname, '..')

// 验证WebSocket连接先通过受保护REST接口领取票据
test('WebSocket使用一次性票据而不是长期JWT直连', () => {
  const apiSource = readFileSync(resolve(projectRoot, 'src/api/websocket.js'), 'utf8')
  const composableSource = readFileSync(resolve(projectRoot, 'src/composables/useWebSocket.js'), 'utf8')
  assert.match(apiSource, /request\.post\(['"]\/api\/ws\/ticket['"]\)/)
  assert.match(composableSource, /createWebSocketTicket\(\)/)
  assert.match(composableSource, /encodeURIComponent\(ticketResponse\.ticket\)/)
  assert.doesNotMatch(composableSource, /getToken|localStorage/)
})

// 验证断线后会重新领取票据并安排自动重连
test('WebSocket断线后保留自动重连', () => {
  const source = readFileSync(resolve(projectRoot, 'src/composables/useWebSocket.js'), 'utf8')
  assert.match(source, /socket\.onclose\s*=\s*\(\)\s*=>/)
  assert.match(source, /scheduleReconnect\(\)/)
  assert.match(source, /setTimeout\(connect,\s*5000\)/)
})
