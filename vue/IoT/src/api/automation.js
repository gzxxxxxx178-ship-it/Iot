import request from './request'

// 获取自动化规则列表
export function getAutomationRules() {
  return request.get('/api/automation/rules')
}

// 创建自动化规则
export function createAutomationRule(data) {
  return request.post('/api/automation/rules', data)
}

// 更新指定自动化规则
export function updateAutomationRule(id, data) {
  return request.put(`/api/automation/rules/${id}`, data)
}

// 删除指定自动化规则
export function deleteAutomationRule(id) {
  return request.delete(`/api/automation/rules/${id}`)
}

// 获取最近一百条自动化执行记录
export function getAutomationExecutions() {
  return request.get('/api/automation/executions')
}
