import request from './request'

// 获取仪表盘统计：GET /api/dashboard/stats
export function getDashboardStats() {
  return request.get('/api/dashboard/stats')
}

// 获取设备状态分布：GET /api/dashboard/device-status，用于饼图
export function getDeviceStatusDistribution() {
  return request.get('/api/dashboard/device-status')
}
