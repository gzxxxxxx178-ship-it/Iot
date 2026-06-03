import request from './request'

export function controlDevice(command) {
  return request.post('/api/device/control', { command })
}

export function getHistoryData() {
  return request.get('/esp/history').then((res) => res.data)
}

export function getDeviceList() {
  return request.get('/esp/devices').then((res) => res.data)
}

export function getSensorHistory(params) {
  return request.get('/esp/history/range', { params }).then((res) => res.data)
}
