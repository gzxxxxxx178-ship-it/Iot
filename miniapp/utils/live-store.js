import { reactive } from 'vue'
import { request, downloadAuthenticatedFile, staleError } from '../api/request.js'
import { session, clearSession, acceptSession, onSessionReset, setSessionRefreshHandler, getStoredRefreshToken, ensureSession } from './session.js'
import { APP_CONFIG, saveConfig } from '../config/index.js'

export const live = reactive({ stats: null, devices: [], commands: [], loading: false, error: '', updatedAt: '' })
let refreshId = 0

// 保留缺测值，避免把空读数转换为零。
export function reading(value) { return typeof value === 'number' && Number.isFinite(value) ? value : null }

// 展示温湿度时限制小数位，保持缺测占位且不修改原始读数。
export function formatReading(value) { return reading(value) === null ? '--' : String(Number(value.toFixed(1))) }

// 映射设备档案，水位始终保持原始 ADC。
export function mapDevice(d) {
  return { id: d.deviceId, name: d.deviceName || d.deviceId, type: d.deviceType || '传感器', location: d.location || '', description: d.description || '', status: d.status, enabled: d.enabled, lifecycleStatus: d.lifecycleStatus, ownedByCurrentUser: d.ownedByCurrentUser !== false, temp: reading(d.temperature), hum: reading(d.humidity), water: reading(d.water), signal: reading(d.rssi), controlled: d.linkage === true, lastSeen: d.lastSeen || null }
}

// 解析后端本地接收时间，兼容小程序日期解析。
export function parseServerTime(value) {
  const parts = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/)
  return parts ? new Date(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]), Number(parts[4]), Number(parts[5]), Number(parts[6])).getTime() : null
}

// 映射历史字段，时间使用服务端接收时间并保留实时监控所需指标。
export function mapHistory(row) { return { id: row.id, deviceId: row.deviceId, time: parseServerTime(row.serverReceivedTime), temp: reading(row.temperature), hum: reading(row.humidity), water: reading(row.water), rssi: reading(row.rssi), linkage: typeof row.linkage === 'boolean' ? row.linkage : null, sendCount: reading(row.sendCount), uptimeMillis: reading(row.uptimeMillis), qualityValid: typeof row.qualityValid === 'boolean' ? row.qualityValid : null, qualityIssues: row.qualityIssues || '' } }

// 格式化后端要求的本地日期时间，不附带 UTC 标记。
export function localDateTime(time) {
  const date = new Date(time)
  // 补齐两位日期字段。
  const pad = (n) => String(n).padStart(2, '0')
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds())
}

// 清空真实数据并作废正在进行的刷新。
function resetLive() { refreshId += 1; Object.assign(live, { stats: null, devices: [], commands: [], loading: false, error: '', updatedAt: '' }) }
onSessionReset(resetLive)

// 注册只使用匿名刷新接口的会话轮换处理器。
setSessionRefreshHandler((refreshToken) => request('/api/auth/refresh', {
  method: 'POST', anonymous: true, data: { refreshToken }
}))

// 保存连接配置，成功后清理旧会话。
export function configure(mode, address) { saveConfig(mode, address); clearSession() }

// 尝试撤销当前刷新会话，并无论网络结果如何都清除本机账号状态。
export async function logout() {
  const refreshToken = getStoredRefreshToken()
  let revoked = Boolean(refreshToken)
  try {
    if (refreshToken) await request('/api/auth/logout', { method: 'POST', anonymous: true, data: { refreshToken } })
  } catch { revoked = false }
  finally { clearSession() }
  return revoked
}

// 登录后仅在当前进程内存中保存认证状态。
export async function login(username, password) {
  clearSession()
  const generation = session.generation
  const data = await request('/api/auth/login', { method: 'POST', anonymous: true, data: { username: username.trim(), password, rememberMe: true } })
  if (!data?.refreshToken) throw new Error('后端尚未启用记住登录，请先更新 Java 后端')
  if (!acceptSession(generation, data)) throw staleError()
}

// 注册新账号并在当前进程内接受后端签发的会话令牌。
export async function register(username, password) {
  clearSession()
  const generation = session.generation
  const data = await request('/api/auth/register', { method: 'POST', anonymous: true, data: { username: username.trim(), password, rememberMe: true } })
  if (!data?.refreshToken) throw new Error('后端尚未启用记住登录，请先更新 Java 后端')
  if (!acceptSession(generation, data)) throw staleError()
}

// 使用微信返回的服务端令牌建立小程序登录会话。
async function acceptWechatSession(path, payload) {
  const generation = session.generation
  const data = await request(path, { method: 'POST', anonymous: true, data: payload })
  if (!data?.refreshToken) throw new Error('微信登录响应缺少刷新凭据，请检查后端配置')
  if (!acceptSession(generation, data)) throw staleError()
}

// 登录已绑定当前微信身份的系统账号。
export function loginWithWechat(code) {
  return acceptWechatSession('/api/auth/wechat/login', { code })
}

// 验证现有账号密码并绑定当前微信身份。
export function linkWechatAccount(code, username, password) {
  return acceptWechatSession('/api/auth/wechat/link', { code, username: username.trim(), password })
}

// 注册系统账号并绑定当前微信身份。
export function registerWechatAccount(code, username, password) {
  return acceptWechatSession('/api/auth/wechat/register', { code, username: username.trim(), password })
}

// 小程序启动时尝试从本机刷新凭据恢复登录状态。
export function restoreSession() { return ensureSession().catch(() => false) }

// 刷新同一会话的概览、设备与回执；错误时清除旧展示。
export async function refreshLive() {
  if (APP_CONFIG.mode !== 'live' || !session.token) return
  const id = ++refreshId
  live.loading = true; live.error = ''
  try {
    const [stats, devices, commands] = await Promise.all([request('/api/dashboard/stats'), request('/api/devices'), request('/api/device/commands')])
    if (id !== refreshId) return
    if (!stats || !Array.isArray(devices) || !Array.isArray(commands)) throw new Error('后端数据格式不正确')
    live.stats = stats; live.devices = devices.map(mapDevice); live.commands = commands
    live.updatedAt = new Date().toLocaleTimeString()
  } catch (error) {
    if (id !== refreshId || error.stale) return
    live.stats = null; live.devices = []; live.commands = []; live.error = error.message
  } finally { if (id === refreshId) live.loading = false }
}

// 下发指令并返回审计回执，不乐观修改设备运行状态。
export async function issueCommand(deviceId, command) {
  const generation = session.generation
  const device = live.devices.find((item) => item.id === deviceId)
  if (!device || device.status !== 'online' || device.enabled === false || device.lifecycleStatus === 'ARCHIVED') throw new Error('该设备当前不可控制')
  if (!['start', 'stop', 'read', 'status'].includes(command)) throw new Error('控制指令无效')
  const receipt = await request('/api/device/control', { method: 'POST', data: { deviceId, command } })
  if (generation !== session.generation) throw staleError()
  if (!receipt?.commandId) throw new Error('命令回执不完整，请刷新确认，勿重复下发')
  live.commands = [receipt, ...live.commands.filter((item) => item.commandId !== receipt.commandId)].slice(0, 100)
  return receipt
}

// 读取固定时间范围内的历史分页。
export async function queryHistory({ deviceId, start, end, page, size = 20 }) {
  const result = await request('/esp/history/page', { data: { ...(deviceId ? { deviceId } : {}), start: localDateTime(start), end: localDateTime(end), page, size } })
  if (!result || !Array.isArray(result.content) || !Number.isInteger(result.totalPages) || !Number.isInteger(result.page)) throw new Error('历史分页响应不完整')
  return { ...result, content: result.content.map(mapHistory) }
}

// 读取后端最近20条账号可访问的历史记录，供总览指标和短趋势使用。
export async function queryRecentHistory() {
  const result = await request('/esp/history')
  if (!Array.isArray(result)) throw new Error('最近历史记录响应无效')
  return result.map(mapHistory)
}

// 按当前设备和冻结的历史时间范围下载服务端生成的完整CSV。
export function downloadHistoryCsv({ deviceId, start, end }) {
  return downloadAuthenticatedFile('/esp/history/export', {
    params: { ...(deviceId ? { deviceId } : {}), start: localDateTime(start), end: localDateTime(end) }
  })
}

// 查询当前账号可管理的设备档案，并按需包含已归档设备。
export async function listDeviceProfiles(includeArchived = false) {
  const result = await request('/api/devices', { params: { includeArchived } })
  if (!Array.isArray(result)) throw new Error('设备档案响应无效')
  return result.map(mapDevice)
}

// 注册设备档案，不创建或修改设备的MQTT凭据。
export async function createDeviceProfile(payload) {
  const result = await request('/api/devices', { method: 'POST', data: payload })
  if (!result?.deviceId) throw new Error('设备档案响应不完整')
  return mapDevice(result)
}

// 更新指定设备的名称、类型、位置、备注和启用状态。
export async function updateDeviceProfile(deviceId, payload) {
  const result = await request('/api/devices/' + encodeURIComponent(deviceId), { method: 'PUT', data: payload })
  if (!result?.deviceId) throw new Error('设备档案响应不完整')
  return mapDevice(result)
}

// 归档设备并保留历史数据。
export function archiveDeviceProfile(deviceId) {
  return request('/api/devices/' + encodeURIComponent(deviceId), { method: 'DELETE' })
}

// 恢复已归档的设备档案。
export async function restoreDeviceProfile(deviceId) {
  const result = await request('/api/devices/' + encodeURIComponent(deviceId) + '/restore', { method: 'POST' })
  if (!result?.deviceId) throw new Error('设备档案响应不完整')
  return mapDevice(result)
}

// 查询设备所有者维护的共享账号名单。
export function listDeviceShares(deviceId) {
  return request('/api/devices/' + encodeURIComponent(deviceId) + '/shares')
}

// 向已注册账号授予设备查看、历史查询和控制权限。
export function addDeviceShare(deviceId, username) {
  return request('/api/devices/' + encodeURIComponent(deviceId) + '/shares', { method: 'POST', data: { username } })
}

// 撤销指定账号的设备访问权限。
export function removeDeviceShare(deviceId, username) {
  return request('/api/devices/' + encodeURIComponent(deviceId) + '/shares/' + encodeURIComponent(username), { method: 'DELETE' })
}

// 翻译命令状态，保留下发与设备确认的区别。
export function commandStatus(status) { return ({ PENDING: '待下发', DISPATCHED: '已下发，等待设备确认', ACKNOWLEDGED: '设备已确认', REJECTED: '设备拒绝执行', FAILED: '下发失败', TIMED_OUT: '设备确认超时' })[status] || status || '未知状态' }
