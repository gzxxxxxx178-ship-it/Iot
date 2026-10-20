import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createQrMatrix } from '../utils/qr-code.js'
import { automationStatusClass, automationStatusLabel, filterAutomationTargets } from '../utils/automation.js'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const flushAsync = () => new Promise((resolve) => setImmediate(resolve))

// 用隔离模块和平台替身测试真实业务逻辑，不请求网络和硬件。
async function setup(initialSettings = '') {
  const pending = []; const requestLog = []; const pendingDownloads = []; const saved = []; const sockets = []; const timers = []
  const context = vm.createContext({ Date, Set, Number, String, Object, Array, Promise, Error,
    setTimeout(callback, delay) { const timer = { callback, delay, active: true }; timers.push(timer); return timer },
    clearTimeout(timer) { if (timer) timer.active = false },
    uni: {
    // 模拟仅配置持久化。
    getStorageSync() { return initialSettings },
    // 记录落盘内容供认证安全断言。
    setStorageSync(key, value) { saved.push({ key, value }) },
    // 收集平台请求供测试控制返回顺序。
    request(options) { pending.push(options); requestLog.push(options) },
    // 收集文件下载供测试控制HTTP状态和会话切换。
    downloadFile(options) { pendingDownloads.push(options) },
    // 模拟微信 SocketTask 并允许测试触发连接事件。
    connectSocket(options) {
      const handlers = {}
      const task = { options, handlers, closed: false,
        onOpen(callback) { handlers.open = callback },
        onMessage(callback) { handlers.message = callback },
        onError(callback) { handlers.error = callback },
        onClose(callback) { handlers.close = callback },
        close() { task.closed = true }
      }
      sockets.push(task)
      return task
    }
  } })
  const modules = new Map()
  // 在同一个隔离上下文中加载并缓存业务模块。
  function moduleFor(path) {
    if (modules.has(path)) return modules.get(path)
    const source = path === 'vue' ? 'export const reactive = value => value' : readFileSync(path, 'utf8')
    const module = new vm.SourceTextModule(source, { context, identifier: path })
    modules.set(path, module); return module
  }
  const entry = new vm.SourceTextModule("export * as api from './api/request.js'; export * as auth from './utils/session.js'; export * as store from './utils/live-store.js'; export * as config from './config/index.js'; export * as history from './utils/history-store.js'; export * as realtime from './utils/realtime.js'; export * as trends from './utils/trend.js'; export * as dashboardMetrics from './utils/dashboard-metrics.js'; export * as research from './utils/research-store.js'; export * as simulation from './utils/simulation-store.js'; export * as pay from './utils/pay-store.js'", { context, identifier: root + '/entry.js' })
  await entry.link((specifier, ref) => moduleFor(specifier === 'vue' ? 'vue' : resolve(dirname(ref.identifier), specifier)))
  await entry.evaluate()
  const env = { ...entry.namespace, pending, requestLog, pendingDownloads, saved, sockets, timers }
  env.initialConfig = { ...env.config.APP_CONFIG }
  env.store.configure('live', 'http://localhost:8080')
  // 构造一个当前会话认证状态。
  env.signIn = (name = 'a') => env.auth.acceptSession(env.auth.session.generation, { token: name + '-token', username: name })
  // 通过真实请求回调交付受控响应。
  env.reply = (request, data, status = 200, code = status) => request.success({ statusCode: status, data: { code, data, message: status === 200 ? 'success' : '后端拒绝' } })
  env.replyDownload = (download, tempFilePath, statusCode = 200) => download.success({ statusCode, tempFilePath })
  return env
}

test('只使用真实接口，旧演示配置仅迁移地址且拒绝切回演示', async () => {
  const e = await setup({ mode: 'demo', apiBaseUrl: 'http://localhost:8080' })
  assert.equal(e.initialConfig.mode, 'live')
  assert.equal(e.initialConfig.apiBaseUrl, 'http://localhost:8080')
  assert.throws(() => e.store.configure('demo', 'http://localhost:8080'), /已移除/)
  assert.equal(e.pending.length, 0)
})

test('自动化命令等待设备确认使用警示状态，状态文字与 Web 一致', () => {
  assert.equal(automationStatusLabel('DISPATCHED'), '等待设备确认')
  assert.equal(automationStatusClass('DISPATCHED'), 'is-pending')
  assert.equal(automationStatusLabel('ACKNOWLEDGED'), '设备已确认')
  assert.equal(automationStatusClass('ACKNOWLEDGED'), 'is-enabled')
  for (const status of ['TIMED_OUT', 'REJECTED', 'FAILED']) {
    assert.equal(automationStatusClass(status), 'is-failed')
  }
  assert.equal(automationStatusLabel('UNKNOWN'), '未知状态')
})

test('自动化规则目标只保留本人持有且已启用的设备', () => {
  const devices = [
    { id: 'owned', ownedByCurrentUser: true, lifecycleStatus: 'ACTIVE', enabled: true },
    { id: 'shared', ownedByCurrentUser: false, lifecycleStatus: 'ACTIVE', enabled: true },
    { id: 'disabled', ownedByCurrentUser: true, lifecycleStatus: 'ACTIVE', enabled: false },
    { id: 'archived', ownedByCurrentUser: true, lifecycleStatus: 'ARCHIVED', enabled: true }
  ]
  assert.deepEqual(filterAutomationTargets(devices).map((device) => device.id), ['owned'])
  assert.deepEqual(filterAutomationTargets(null), [])
})

// 验证工作台不会把已经完成真实接口迁移的自动化入口标为部分迁移。
test('工作台将自动化规则标记为已接入', () => {
  const source = readFileSync(resolve(root, 'pages/workbench/workbench.vue'), 'utf8')
  assert.match(source, /title: '自动化规则',[^\n]*status: '已接入', kind: 'ready'/)
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
  const sharedControl = e.store.issueCommand('shared', 'read')
  assert.equal(e.pending[0].data.command, 'read')
  e.reply(e.pending.shift(), { commandId: 'shared-read', status: 'DISPATCHED' }); await sharedControl
  await assert.rejects(e.store.issueCommand('shared', 'invalid'), /控制指令无效/)
})

test('设备档案注册编辑归档恢复和共享名单使用真实鉴权接口', async () => {
  const e = await setup(); e.signIn()
  const listing = e.store.listDeviceProfiles(true)
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/devices?includeArchived=true')
  e.reply(e.pending.shift(), [{ deviceId: 'device01', deviceName: '棚内节点', deviceType: 'ESP32', location: '东侧', description: '土壤区', enabled: true, lifecycleStatus: 'ACTIVE', ownedByCurrentUser: true }])
  const devices = await listing
  assert.equal(devices[0].location, '东侧'); assert.equal(devices[0].description, '土壤区')

  const create = e.store.createDeviceProfile({ deviceId: 'device02', deviceName: '新节点' })
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/devices'); assert.equal(e.pending[0].header.Authorization, 'Bearer a-token')
  e.reply(e.pending.shift(), { deviceId: 'device02', deviceName: '新节点' }); await create
  const update = e.store.updateDeviceProfile('device01', { deviceName: '新名称', enabled: false })
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/devices/device01'); assert.equal(e.pending[0].method, 'PUT')
  e.reply(e.pending.shift(), { deviceId: 'device01', deviceName: '新名称' }); await update
  const archive = e.store.archiveDeviceProfile('device01')
  assert.equal(e.pending[0].method, 'DELETE'); e.reply(e.pending.shift(), null); await archive
  const restore = e.store.restoreDeviceProfile('device01')
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/devices/device01/restore'); e.reply(e.pending.shift(), { deviceId: 'device01' }); await restore
  const shares = e.store.listDeviceShares('device01')
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/devices/device01/shares'); e.reply(e.pending.shift(), []); await shares
  const add = e.store.addDeviceShare('device01', 'member a')
  assert.equal(e.pending[0].data.username, 'member a'); e.reply(e.pending.shift(), ['member a']); await add
  const remove = e.store.removeDeviceShare('device01', 'member a')
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/devices/device01/shares/member%20a'); e.reply(e.pending.shift(), []); await remove
})

test('历史分页冻结时间、筛选时忽略旧页、失败可重试且不跳页', async () => {
  const e = await setup(); e.signIn()
  const first = e.history.reloadHistory('old', 24); const old = e.pending.shift()
  const next = e.history.reloadHistory('new', 168); const active = e.pending.shift()
  const end = active.data.end
  assert.equal(active.data.page, 0); assert.equal(active.data.deviceId, 'new'); assert.equal(end.includes('Z'), false)
  e.reply(old, { content: [{ id: 99 }], page: 0, totalPages: 1, totalElements: 1 }); await first
  e.reply(active, { content: [{ id: 1, deviceId: 'new', temperature: 0, humidity: null, water: 650, rssi: -62, linkage: false, sendCount: 10, uptimeMillis: 12345, qualityValid: false, qualityIssues: '温度异常', serverReceivedTime: '2026-10-14T12:00:00' }], page: 0, totalPages: 2, totalElements: 2 }); await next
  assert.equal(e.history.history.rows.length, 1); assert.equal(e.history.history.rows[0].temp, 0)
  assert.equal(e.history.history.rows[0].rssi, -62); assert.equal(e.history.history.rows[0].linkage, false); assert.equal(e.history.history.rows[0].sendCount, 10)
  assert.equal(e.history.history.rows[0].uptimeMillis, 12345); assert.equal(e.history.history.rows[0].qualityValid, false); assert.equal(e.history.history.rows[0].qualityIssues, '温度异常')
  const more = e.history.loadHistory(); const failed = e.pending.shift()
  assert.equal(failed.data.end, end); assert.equal(failed.data.page, 1)
  failed.fail({ errMsg: 'timeout' }); await more; assert.equal(e.history.history.page, 0)
  const retry = e.history.loadHistory(); e.reply(e.pending.shift(), { content: [{ id: 2 }], page: 1, totalPages: 2, totalElements: 2 }); await retry
  assert.equal(e.history.history.rows.length, 2)
  await e.history.loadHistory(); assert.equal(e.pending.length, 0)
  e.auth.clearSession(); assert.equal(e.history.history.rows.length, 0)
})

test('历史查询支持自定义本地起止时间并在分页和导出状态中冻结边界', async () => {
  const e = await setup(); e.signIn()
  const start = new Date(2026, 5, 3, 8, 15, 0, 0).getTime()
  const end = new Date(2026, 5, 4, 17, 45, 59, 999).getTime()
  const query = e.history.reloadHistory('device/custom', { start, end }); const request = e.pending.shift()
  assert.equal(request.data.start, '2026-06-03T08:15:00')
  assert.equal(request.data.end, '2026-06-04T17:45:59')
  assert.equal(e.history.history.start, start); assert.equal(e.history.history.end, end)
  e.reply(request, { content: [], page: 0, totalPages: 1, totalElements: 0 }); await query
})

test('看板近期数据使用真实鉴权接口并生成按时间递增的分组趋势', async () => {
  const e = await setup(); e.signIn()
  const recent = e.store.queryRecentHistory()
  assert.equal(e.pending[0].url, 'http://localhost:8080/esp/history')
  assert.equal(e.pending[0].header.Authorization, 'Bearer a-token')
  e.reply(e.pending.shift(), [
    { id: 3, temperature: 22, humidity: null, serverReceivedTime: '2026-10-19T12:02:00' },
    { id: 2, temperature: 20, humidity: 40, serverReceivedTime: '2026-10-19T12:01:00' },
    { id: 1, temperature: 18, humidity: 30, serverReceivedTime: '2026-10-19T12:00:00' }
  ])
  const rows = await recent
  const trend = e.trends.buildTrend(rows, 'temp', (time) => String(time))
  assert.deepEqual(trend.map((point) => point.value), [18, 20, 22])
  assert.deepEqual(trend.map((point) => point.label), rows.slice().reverse().map((row) => String(row.time)))
  assert.equal(e.trends.buildTrend([{ time: 1, temp: null }], 'temp', String).length, 0)
})

test('大屏合并历史与实时数据时去重、保留最新点并生成正序趋势', async () => {
  const e = await setup()
  const rows = e.trends.mergeTrendRows(
    [
      { id: 1, deviceId: 'sensor-a', time: 1000, temp: 10 },
      { id: 2, deviceId: 'sensor-a', time: 2000, temp: 20 },
      { id: 3, deviceId: 'sensor-b', time: 2000, temp: 30 }
    ],
    [
      { id: 'sensor-a-2000', deviceId: 'sensor-a', time: 2000, temp: 20 },
      { id: 'sensor-a-2000b', deviceId: 'sensor-a', time: 2000, temp: 21 },
      { id: 'sensor-a-3000', deviceId: 'sensor-a', time: 3000, temp: 40 }
    ],
    4
  )
  assert.deepEqual(Array.from(rows, (row) => row.time), [3000, 2000, 2000, 2000])
  assert.deepEqual(Array.from(rows, (row) => row.temp), [40, 20, 30, 21])
  const chart = e.trends.buildTrend(rows, 'temp', (time) => String(time), 4)
  assert.deepEqual(Array.from(chart, (point) => point.label), ['2000', '2000', '2000', '3000'])
})

test('仪表盘辅助指标保留零值、最新联动状态和最近记录趋势', async () => {
  const e = await setup()
  const result = e.dashboardMetrics.summarizeRecentRows([
    { time: 2000, temp: 20, hum: 60, water: 100, rssi: -61, linkage: false, sendCount: 4 },
    { time: 1000, temp: 10, hum: 40, water: 0, rssi: -70, linkage: true, sendCount: 3 }
  ], (time) => String(time))
  assert.equal(result.avgWater, 50)
  assert.equal(result.latestRssi, -61)
  assert.equal(result.latestLinkage, 'OFF')
  assert.equal(result.recentMessages, 7)
  assert.deepEqual(result.temperatureTrend.map((item) => item.value), [10, 20])
  assert.deepEqual(result.humidityTrend.map((item) => item.value), [40, 60])
})

test('研究工作台读取概览、运行详情并以认证请求执行RAG证据查询', async () => {
  const e = await setup(); e.signIn()
  const overview = e.research.getResearchOverview()
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/research/overview')
  assert.equal(e.pending[0].header.Authorization, 'Bearer a-token')
  e.reply(e.pending.shift(), { latestMpc: { runId: 7 } }); await overview
  const runs = e.research.listExperiments(25)
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/research/experiments?limit=25')
  e.reply(e.pending.shift(), [{ id: 7, title: 'MPC run' }]); assert.equal((await runs)[0].id, 7)
  const detail = e.research.getExperimentDetail('7/8')
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/research/experiments/7%2F8')
  e.reply(e.pending.shift(), { metrics: [{ metricName: 'MAE', metricValue: 1.2 }] }); assert.equal((await detail).metrics[0].metricName, 'MAE')
  const rag = e.research.queryResearchEvidence('灌溉阈值怎么确定？', 5)
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/research/rag/query')
  assert.equal(e.pending[0].data.question, '灌溉阈值怎么确定？')
  assert.equal(e.pending[0].data.topK, 5)
  e.reply(e.pending.shift(), { abstained: true, answer: '证据不足' }); assert.equal((await rag).abstained, true)
})

test('仿真工作台只访问仿真API并覆盖数据、规则、报警及命令查询', async () => {
  const e = await setup(); e.signIn()
  const latest = e.simulation.getLatestSimulationTelemetry('SIM-PADDY-001')
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/simulation/telemetry/latest?deviceId=SIM-PADDY-001')
  assert.equal(e.pending[0].header.Authorization, 'Bearer a-token')
  e.reply(e.pending.shift(), { sourceType: 'SIMULATION', waterLevelMm: 0 }); assert.equal((await latest).waterLevelMm, 0)
  const history = e.simulation.getSimulationTelemetryHistory('SIM-PADDY-001', 100)
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/simulation/telemetry/history?deviceId=SIM-PADDY-001&limit=100')
  e.reply(e.pending.shift(), [{ sampleId: 'one' }]); assert.equal((await history)[0].sampleId, 'one')
  const rules = e.simulation.listSimulationRules(); assert.equal(e.pending[0].url, 'http://localhost:8080/api/simulation/rules'); e.reply(e.pending.shift(), []); await rules
  const payload = { name: 'water', deviceId: '*', metric: 'waterLevelMm', operator: 'gt', threshold: 200, recoveryThreshold: 180, debounceCount: 2, severity: 'WARN', action: 'NOTIFY', enabled: true }
  const created = e.simulation.createSimulationRule(payload); assert.equal(e.pending[0].method, 'POST'); assert.equal(e.pending[0].data.recoveryThreshold, 180); e.reply(e.pending.shift(), { id: 8 }); await created
  const updated = e.simulation.updateSimulationRule('8/9', { ...payload, enabled: false }); assert.equal(e.pending[0].url, 'http://localhost:8080/api/simulation/rules/8%2F9'); assert.equal(e.pending[0].method, 'PUT'); e.reply(e.pending.shift(), { id: '8/9', enabled: false }); await updated
  const deleted = e.simulation.deleteSimulationRule(8); assert.equal(e.pending[0].method, 'DELETE'); e.reply(e.pending.shift(), null); await deleted
  const alarms = e.simulation.listSimulationAlarms('SIM-PADDY-001'); assert.equal(e.pending[0].url, 'http://localhost:8080/api/simulation/alarms?deviceId=SIM-PADDY-001'); e.reply(e.pending.shift(), []); await alarms
  const acknowledged = e.simulation.acknowledgeSimulationAlarm(3); assert.equal(e.pending[0].url, 'http://localhost:8080/api/simulation/alarms/3/acknowledge'); assert.equal(e.pending[0].method, 'POST'); e.reply(e.pending.shift(), { id: 3, status: 'ACKNOWLEDGED' }); await acknowledged
  const commands = e.simulation.listPendingSimulationCommands('SIM-PADDY-001'); assert.equal(e.pending[0].url, 'http://localhost:8080/api/simulation/commands/pending?deviceId=SIM-PADDY-001'); e.reply(e.pending.shift(), []); await commands
  assert.equal(e.requestLog.every((request) => request.url.includes('/api/simulation/')), true)
  assert.equal(e.requestLog.some((request) => request.url.includes('/api/device/control') || request.url.includes('/feedback')), false)
})

test('支付页只创建当前账号沙箱订单并查询订单状态，二维码矩阵可绘制', async () => {
  const e = await setup(); e.signIn()
  const created = e.pay.createPaymentOrder('0.01', '本地沙箱测试')
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/alipay/create')
  assert.equal(e.pending[0].method, 'POST')
  assert.equal(e.pending[0].header.Authorization, 'Bearer a-token')
  assert.equal(e.pending[0].data.amount, '0.01')
  e.reply(e.pending.shift(), { outTradeNo: 'PAY123', qrCode: 'https://qr.alipay.com/sandbox-order' }); assert.equal((await created).outTradeNo, 'PAY123')
  const queried = e.pay.queryPaymentOrder('PAY123')
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/alipay/query?outTradeNo=PAY123')
  e.reply(e.pending.shift(), { status: 'SUCCESS', tradeNo: 'trade-1', amount: '0.01' }); assert.equal((await queried).tradeNo, 'trade-1')
  const matrix = createQrMatrix('https://qr.alipay.com/sandbox-order')
  assert.ok(matrix.size > 20); assert.equal(matrix.data.length, matrix.size * matrix.size)
  assert.ok(matrix.data.every((module) => module === 0 || module === 1))
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

test('完整历史CSV导出沿用当前账号、设备及冻结时间范围', async () => {
  const e = await setup(); e.signIn()
  const start = new Date(2026, 9, 18, 10, 0, 0).getTime()
  const end = new Date(2026, 9, 19, 10, 0, 0).getTime()
  const downloading = e.store.downloadHistoryCsv({ deviceId: 'device 01', start, end })
  const request = e.pendingDownloads.shift()
  assert.equal(request.url, 'http://localhost:8080/esp/history/export?deviceId=device%2001&start=2026-10-18T10%3A00%3A00&end=2026-10-19T10%3A00%3A00')
  assert.equal(request.header.Authorization, 'Bearer a-token')
  e.replyDownload(request, 'wxfile://tmp/history.csv')
  const downloaded = await downloading
  assert.equal(downloaded.tempFilePath, 'wxfile://tmp/history.csv')
  assert.equal(downloaded.statusCode, 200)

  const expired = e.api.downloadAuthenticatedFile('/esp/history/export')
  e.replyDownload(e.pendingDownloads.shift(), '', 401)
  await assert.rejects(expired, /登录已过期/)
  assert.equal(e.auth.session.token, '')

  e.signIn('old')
  const stale = e.api.downloadAuthenticatedFile('/esp/history/export')
  const oldDownload = e.pendingDownloads.shift()
  e.auth.clearSession(); e.signIn('current')
  e.replyDownload(oldDownload, 'wxfile://tmp/old-account.csv')
  await assert.rejects(stale, error => error.stale)
  assert.equal(e.auth.session.username, 'current')
})

test('注册请求不发送旧令牌并拒绝迟到响应覆盖新会话', async () => {
  const e = await setup(); e.signIn('old')
  const registration = e.store.register(' new-user ', 'secret1')
  const req = e.pending.shift()
  assert.equal(req.url, 'http://localhost:8080/api/auth/register')
  assert.equal(req.header.Authorization, undefined)
  assert.equal(req.data.username, 'new-user')
  assert.equal(req.data.password, 'secret1')
  e.reply(req, { token: 'new-token', username: 'new-user' }); await registration
  assert.equal(e.auth.session.username, 'new-user')
  assert.equal(e.auth.session.token, 'new-token')

  const delayed = e.store.register('late-user', 'secret2')
  const oldRequest = e.pending.shift()
  e.auth.clearSession(); e.signIn('current')
  e.reply(oldRequest, { token: 'late-token', username: 'late-user' })
  await assert.rejects(delayed, err => err.stale)
  assert.equal(e.auth.session.username, 'current')
  assert.equal(e.auth.session.token, 'current-token')
  assert.equal(JSON.stringify(e.saved).includes('token'), false)
})

test('实时连接领取短期票据、接收遥测并在页面停止时关闭', async () => {
  const e = await setup(); e.signIn()
  const readings = []; const statuses = []
  const connection = e.realtime.createRealtimeConnection((item) => readings.push(item), (status, message) => statuses.push({ status, message }))
  connection.start()
  assert.equal(e.pending[0].url, 'http://localhost:8080/api/ws/ticket')
  assert.equal(e.pending[0].method, 'POST')
  assert.equal(e.pending[0].header.Authorization, 'Bearer a-token')
  e.reply(e.pending.shift(), { ticket: 'one-time ticket' })
  await flushAsync()
  assert.equal(e.sockets.length, 1, JSON.stringify(statuses))
  assert.equal(e.sockets[0].options.url, 'ws://localhost:8080/ws/sensor?ticket=one-time%20ticket')
  e.sockets[0].handlers.open()
  e.sockets[0].handlers.message({ data: JSON.stringify({ deviceId: 'd', temperature: 20 }) })
  assert.deepEqual(readings.map((item) => item.deviceId), ['d'])
  assert.equal(statuses.at(-1).status, 'online')
  connection.stop()
  assert.equal(e.sockets[0].closed, true)
  e.sockets[0].handlers.close()
  assert.equal(e.timers.length, 0)
})

test('账号切换后丢弃迟到的 WebSocket 票据，不为旧会话建立连接', async () => {
  const e = await setup(); e.signIn()
  const connection = e.realtime.createRealtimeConnection(() => {}, () => {})
  connection.start()
  const ticketRequest = e.pending.shift()
  e.auth.clearSession(); e.signIn('b')
  e.reply(ticketRequest, { ticket: 'stale-ticket' })
  await flushAsync()
  assert.equal(e.sockets.length, 0)
  assert.equal(e.timers.length, 0)
  connection.stop()
})

test('实时连接断开后延迟重连并重新领取一次性票据', async () => {
  const e = await setup(); e.signIn()
  const connection = e.realtime.createRealtimeConnection(() => {}, () => {})
  connection.start()
  e.reply(e.pending.shift(), { ticket: 'first' })
  await flushAsync()
  e.sockets[0].handlers.close()
  const timer = e.timers.at(-1)
  assert.equal(timer.delay, 5000)
  timer.callback()
  assert.equal(e.pending.length, 1)
  e.reply(e.pending.shift(), { ticket: 'second' })
  await flushAsync()
  assert.equal(e.sockets.length, 2)
  assert.match(e.sockets[1].options.url, /ticket=second$/)
  connection.stop()
})
