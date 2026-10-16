import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

// 用隔离模块和平台替身测试真实业务逻辑，不请求网络和硬件。
async function setup(initialSettings = '') {
  const pending = []; const saved = []
  const context = vm.createContext({ Date, Set, Number, String, Object, Array, Promise, Error, uni: {
    // 模拟仅配置持久化。
    getStorageSync() { return initialSettings },
    // 记录落盘内容供认证安全断言。
    setStorageSync(key, value) { saved.push({ key, value }) },
    // 收集平台请求供测试控制返回顺序。
    request(options) { pending.push(options) }
  } })
  const modules = new Map()
  // 在同一个隔离上下文中加载并缓存业务模块。
  function moduleFor(path) {
    if (modules.has(path)) return modules.get(path)
    const source = path === 'vue' ? 'export const reactive = value => value' : readFileSync(path, 'utf8')
    const module = new vm.SourceTextModule(source, { context, identifier: path })
    modules.set(path, module); return module
  }
  const entry = new vm.SourceTextModule("export * as api from './api/request.js'; export * as auth from './utils/session.js'; export * as store from './utils/live-store.js'; export * as config from './config/index.js'; export * as history from './utils/history-store.js'", { context, identifier: root + '/entry.js' })
  await entry.link((specifier, ref) => moduleFor(specifier === 'vue' ? 'vue' : resolve(dirname(ref.identifier), specifier)))
  await entry.evaluate()
  const env = { ...entry.namespace, pending, saved }
  env.initialConfig = { ...env.config.APP_CONFIG }
  env.store.configure('live', 'http://localhost:8080')
  // 构造一个当前会话认证状态。
  env.signIn = (name = 'a') => env.auth.acceptSession(env.auth.session.generation, { token: name + '-token', username: name })
  // 通过真实请求回调交付受控响应。
  env.reply = (request, data, status = 200, code = status) => request.success({ statusCode: status, data: { code, data, message: status === 200 ? 'success' : '后端拒绝' } })
  return env
}

test('只使用真实接口，旧演示配置仅迁移地址且拒绝切回演示', async () => {
  const e = await setup({ mode: 'demo', apiBaseUrl: 'http://localhost:8080' })
  assert.equal(e.initialConfig.mode, 'live')
  assert.equal(e.initialConfig.apiBaseUrl, 'http://localhost:8080')
  assert.throws(() => e.store.configure('demo', 'http://localhost:8080'), /已移除/)
  assert.equal(e.pending.length, 0)
})

test('地址拒绝凭据、路径及非法端口，只持久化配置', async () => {
  const e = await setup()
  for (const address of ['https://u:p@host', 'http://host/api', 'http://host?x=1', 'file:///tmp', 'http://host:99999', 'http://host/#x']) assert.throws(() => e.config.normalizeBaseUrl(address))
  assert.equal(e.config.normalizeBaseUrl(' http://localhost:8080/ '), 'http://localhost:8080')
  assert.equal(e.config.normalizeBaseUrl('https://[::1]:443'), 'https://[::1]:443')
  e.signIn(); assert.equal(JSON.stringify(e.saved).includes('token'), false)
  assert.throws(() => e.store.configure('live', 'http://user:password@host'))
  assert.equal(e.auth.session.username, 'a')
})

test('登录不带令牌，受保护接口发送Bearer和超时设置', async () => {
  const e = await setup(); e.signIn()
  const login = e.store.login(' b ', 'test-only')
  assert.equal(e.pending[0].header.Authorization, undefined)
  assert.equal(e.pending[0].data.username, 'b')
  assert.equal(e.pending[0].timeout, 15000)
  e.reply(e.pending.shift(), { token: 'b-token', username: 'b' }); await login
  const get = e.api.request('/api/devices')
  assert.equal(e.pending[0].header.Authorization, 'Bearer b-token')
  e.reply(e.pending.shift(), []); await get
  await assert.rejects(e.api.request('https://elsewhere/api/devices'), /路径/)
})

test('HTTP、业务错误、超时均拒绝，401清理认证与数据', async () => {
  const e = await setup(); e.signIn(); e.store.live.devices = [{ id: 'private' }]
  let p = e.api.request('/api/devices'); e.reply(e.pending.shift(), null, 500); await assert.rejects(p, /后端拒绝/)
  p = e.api.request('/api/devices'); e.reply(e.pending.shift(), null, 200, 400); await assert.rejects(p)
  p = e.api.request('/api/devices'); e.pending.shift().fail({ errMsg: 'request:fail timeout' }); await assert.rejects(p, /超时/)
  p = e.api.request('/api/devices'); e.reply(e.pending.shift(), null, 401); await assert.rejects(p, /登录已过期/)
  assert.equal(e.auth.session.token, ''); assert.equal(e.store.live.devices.length, 0)
})

test('旧响应、旧401、迟到登录均不能污染新账号', async () => {
  const e = await setup(); e.signIn()
  const old = e.api.request('/api/devices'); const request = e.pending.shift()
  e.auth.clearSession(); e.signIn('b')
  e.reply(request, null, 401); await assert.rejects(old, err => err.stale)
  assert.equal(e.auth.session.token, 'b-token')
  const login = e.store.login('c', 'test-only'); const delayed = e.pending.shift()
  e.auth.clearSession(); e.signIn('d'); e.reply(delayed, { token: 'c-token', username: 'c' })
  await assert.rejects(login, err => err.stale); assert.equal(e.auth.session.username, 'd')
})

test('切换连接后旧刷新和控制回执不能恢复旧设备', async () => {
  const e = await setup(); e.signIn()
  const refresh = e.store.refreshLive(); const requests = e.pending.splice(0)
  e.store.configure('live', 'http://localhost:8081'); e.signIn('b')
  for (const r of requests) e.reply(r, [])
  await refresh; assert.equal(e.store.live.devices.length, 0)
  e.store.live.devices = [{ id: 'd', status: 'online', enabled: true, ownedByCurrentUser: true }]
  const control = e.store.issueCommand('d', 'start'); const req = e.pending.shift()
  e.reply(req, { commandId: 'old', status: 'DISPATCHED' }); e.auth.clearSession()
  await assert.rejects(control, err => err.stale); assert.equal(e.store.live.commands.length, 0)
})

test('设备DTO保持零值和缺测，控制不乐观改变运行状态', async () => {
  const e = await setup(); e.signIn()
  const device = e.store.mapDevice({ deviceId: 'd', status: 'online', water: 0, temperature: null, humidity: 0, rssi: -60, linkage: false, ownedByCurrentUser: true })
  assert.equal(device.id, 'd'); assert.equal(device.temp, null); assert.equal(device.water, 0)
  e.store.live.devices = [device]
  const control = e.store.issueCommand('d', 'start')
  assert.equal(e.pending[0].data.deviceId, 'd')
  e.reply(e.pending.shift(), { commandId: 'one', status: 'DISPATCHED' }); await control
  assert.equal(device.controlled, false)
  assert.match(e.store.commandStatus('DISPATCHED'), /等待/)
  assert.equal(e.store.commandStatus('ACKNOWLEDGED'), '设备已确认')
  device.status = 'offline'; await assert.rejects(e.store.issueCommand('d', 'start'), /不可控制/)
  const sharedDevice = e.store.mapDevice({ deviceId: 'shared', status: 'online', ownedByCurrentUser: false })
  e.store.live.devices = [sharedDevice]
  await assert.rejects(e.store.issueCommand('shared', 'start'), /共享设备仅支持查看/)
  assert.equal(e.pending.length, 0)
})

test('历史分页冻结时间、筛选时忽略旧页、失败可重试且不跳页', async () => {
  const e = await setup(); e.signIn()
  const first = e.history.reloadHistory('old', 24); const old = e.pending.shift()
  const next = e.history.reloadHistory('new', 168); const active = e.pending.shift()
  const end = active.data.end
  assert.equal(active.data.page, 0); assert.equal(active.data.deviceId, 'new'); assert.equal(end.includes('Z'), false)
  e.reply(old, { content: [{ id: 99 }], page: 0, totalPages: 1, totalElements: 1 }); await first
  e.reply(active, { content: [{ id: 1, temperature: 0, humidity: null, water: 650, serverReceivedTime: '2026-10-14T12:00:00' }], page: 0, totalPages: 2, totalElements: 2 }); await next
  assert.equal(e.history.history.rows.length, 1); assert.equal(e.history.history.rows[0].temp, 0)
  const more = e.history.loadHistory(); const failed = e.pending.shift()
  assert.equal(failed.data.end, end); assert.equal(failed.data.page, 1)
  failed.fail({ errMsg: 'timeout' }); await more; assert.equal(e.history.history.page, 0)
  const retry = e.history.loadHistory(); e.reply(e.pending.shift(), { content: [{ id: 2 }], page: 1, totalPages: 2, totalElements: 2 }); await retry
  assert.equal(e.history.history.rows.length, 2)
  await e.history.loadHistory(); assert.equal(e.pending.length, 0)
  e.auth.clearSession(); assert.equal(e.history.history.rows.length, 0)
})

test('查询参数安全编码并可用于GET和DELETE接口', async () => {
  const e = await setup(); e.signIn()
  const get = e.api.request('/api/chat/history', { params: { sessionId: 'account a&b' } })
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/chat/history?sessionId=account%20a%26b')
  e.reply(e.pending.shift(), []); await get
  const del = e.api.request('/api/chat/history', { method: 'DELETE', params: { sessionId: 'account a&b' } })
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/chat/history?sessionId=account%20a%26b')
  e.reply(e.pending.shift(), { ok: true }); await del
})
