import request from './request'

export function getAlarmRules() {
  return request.get('/api/alarm/rules').then((res) => res.data)
}

export function saveAlarmRule(data) {
  return request.post('/api/alarm/rules', data)
}

export function deleteAlarmRule(id) {
  return request.delete(`/api/alarm/rules/${id}`)
}

export function getAlarmRecords(params) {
  return request.get('/api/alarm/records', { params }).then((res) => res.data)
}
