import request from './request'

// 设备控制：向指定设备发送start/stop/read/status命令
export function controlDevice(command, deviceId = 'device001') {
  return request.post('/api/device/control', { command, deviceId })
}

// 查询当前用户最近的设备控制命令和设备确认状态
export function getRecentDeviceCommands() {
  return request.get('/api/device/commands')
}

// 获取最近传感器数据：GET /esp/history，返回最近 20 条记录
export function getHistoryData() {
  return request.get('/esp/history')
}

// 获取设备档案与最新状态列表
export function getDeviceList(params = {}) {
  return request.get('/api/devices', { params })
}

// 查询设备所有者配置的共享账号名单
export function getDeviceShares(deviceId) {
  return request.get(`/api/devices/${encodeURIComponent(deviceId)}/shares`)
}

// 授权指定账号查看、读取历史并控制设备
export function addDeviceShare(deviceId, username) {
  return request.post(`/api/devices/${encodeURIComponent(deviceId)}/shares`, { username })
}

// 撤销指定账号的设备共享权限
export function removeDeviceShare(deviceId, username) {
  return request.delete(`/api/devices/${encodeURIComponent(deviceId)}/shares/${encodeURIComponent(username)}`)
}

// 注册新的设备档案
export function createDevice(data) {
  return request.post('/api/devices', data)
}

// 更新指定设备的可编辑档案
export function updateDevice(deviceId, data) {
  return request.put(`/api/devices/${encodeURIComponent(deviceId)}`, data)
}

// 将指定设备软删除为归档状态
export function archiveDevice(deviceId) {
  return request.delete(`/api/devices/${encodeURIComponent(deviceId)}`)
}

// 恢复已经归档的设备档案
export function restoreDevice(deviceId) {
  return request.post(`/api/devices/${encodeURIComponent(deviceId)}/restore`)
}

// 按时间范围查询传感器历史：GET /esp/history/range?start=xxx&end=xxx
export function getSensorHistory(params) {
  return request.get('/esp/history/range', { params })
}

// 分页查询传感器历史：GET /esp/history/page?start=xxx&end=xxx&page=0&size=20
export function getSensorHistoryPage(params) {
  return request.get('/esp/history/page', { params })
}

// 导出筛选条件下的完整传感器历史CSV文件
export function exportSensorHistoryCsv(params) {
  return request.get('/esp/history/export', { params, responseType: 'blob' })
}
