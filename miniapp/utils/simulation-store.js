import { request } from '../api/request.js'

// 查询指定仿真设备的最新遥测记录。
export function getLatestSimulationTelemetry(deviceId) {
  return request('/api/simulation/telemetry/latest', { params: { deviceId } })
}

// 查询指定仿真设备最近的遥测记录。
export function getSimulationTelemetryHistory(deviceId, limit = 100) {
  return request('/api/simulation/telemetry/history', { params: { deviceId, limit } })
}

// 查询当前账号的仿真规则列表。
export function listSimulationRules() {
  return request('/api/simulation/rules')
}

// 创建仿真规则并交由后端校验滞回、防抖和动作参数。
export function createSimulationRule(payload) {
  return request('/api/simulation/rules', { method: 'POST', data: payload })
}

// 更新当前账号拥有的仿真规则。
export function updateSimulationRule(id, payload) {
  return request('/api/simulation/rules/' + encodeURIComponent(id), { method: 'PUT', data: payload })
}

// 删除当前账号拥有的仿真规则。
export function deleteSimulationRule(id) {
  return request('/api/simulation/rules/' + encodeURIComponent(id), { method: 'DELETE' })
}

// 查询指定仿真设备的报警记录。
export function listSimulationAlarms(deviceId) {
  return request('/api/simulation/alarms', { params: { deviceId } })
}

// 确认当前账号拥有且仍处于活跃状态的仿真报警。
export function acknowledgeSimulationAlarm(id) {
  return request('/api/simulation/alarms/' + encodeURIComponent(id) + '/acknowledge', { method: 'POST' })
}

// 查询指定仿真设备等待 MATLAB 轮询的命令记录，不提交命令或反馈。
export function listPendingSimulationCommands(deviceId) {
  return request('/api/simulation/commands/pending', { params: { deviceId } })
}
