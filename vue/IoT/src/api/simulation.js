import request from './request'

// 获取指定仿真设备的最新遥测数据
export function getLatestTelemetry(deviceId) {
  return request.get('/api/simulation/telemetry/latest', {
    params: { deviceId },
  })
}

// 获取指定仿真设备的遥测历史记录，limit 控制返回条数
export function getTelemetryHistory(deviceId, limit = 100) {
  return request.get('/api/simulation/telemetry/history', {
    params: { deviceId, limit },
  })
}

// 获取仿真规则列表
export function getSimulationRules() {
  return request.get('/api/simulation/rules')
}

// 创建新的仿真规则
export function createSimulationRule(data) {
  return request.post('/api/simulation/rules', data)
}

// 更新指定仿真规则
export function updateSimulationRule(id, data) {
  return request.put(`/api/simulation/rules/${encodeURIComponent(id)}`, data)
}

// 删除指定仿真规则
export function deleteSimulationRule(id) {
  return request.delete(`/api/simulation/rules/${encodeURIComponent(id)}`)
}

// 获取指定仿真设备的报警列表，可按状态筛选
export function getSimulationAlarms(deviceId, status) {
  return request.get('/api/simulation/alarms', {
    params: { deviceId, ...(status ? { status } : {}) },
  })
}

// 确认指定报警
export function acknowledgeSimulationAlarm(id) {
  return request.post(`/api/simulation/alarms/${encodeURIComponent(id)}/acknowledge`)
}

// 获取指定仿真设备的待执行命令列表
export function getPendingCommands(deviceId) {
  return request.get('/api/simulation/commands/pending', {
    params: { deviceId },
  })
}
