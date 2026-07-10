import request from './request'

// 设备控制：POST /api/device/control，发送 start/stop 命令
export function controlDevice(command) {
  return request.post('/api/device/control', { command }).then((res) => res.data)
}

// 获取最近传感器数据：GET /esp/history，返回最近 20 条记录
export function getHistoryData() {
  return request.get('/esp/history').then((res) => res.data)
}

// 获取设备列表：GET /esp/devices，返回所有设备最新读数汇总
export function getDeviceList() {
  return request.get('/esp/devices').then((res) => res.data)
}

// 按时间范围查询传感器历史：GET /esp/history/range?start=xxx&end=xxx
export function getSensorHistory(params) {
  return request.get('/esp/history/range', { params }).then((res) => res.data)
}
