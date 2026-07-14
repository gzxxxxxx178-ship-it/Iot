import request from './request'

// 获取报警规则列表：GET /api/alarm/rules
export function getAlarmRules() {
  return request.get('/api/alarm/rules')
}

// 保存报警规则：POST /api/alarm/rules
export function saveAlarmRule(data) {
  return request.post('/api/alarm/rules', data)
}

// 删除报警规则：DELETE /api/alarm/rules/:id
export function deleteAlarmRule(id) {
  return request.delete(`/api/alarm/rules/${id}`)
}

// 获取报警记录：GET /api/alarm/records，支持时间范围过滤
export function getAlarmRecords(params) {
  return request.get('/api/alarm/records', { params })
}
