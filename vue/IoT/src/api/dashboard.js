import request from './request'

export function getDashboardStats() {
  return request.get('/api/dashboard/stats').then((res) => res.data)
}

export function getDeviceStatusDistribution() {
  return request.get('/api/dashboard/device-status').then((res) => res.data)
}
