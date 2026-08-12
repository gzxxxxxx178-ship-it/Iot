import request from './request'

/**
 * 上传实验运行及其指标（幂等）
 * @param {Object} data - 实验运行请求体 {runKey, experimentType, title, ..., metrics}
 * @returns {Promise} 实验运行响应
 */
export function uploadExperiment(data) {
  return request.post('/api/research/experiments', data)
}

/**
 * 查询当前用户的实验运行列表
 * @param {Object} params - {type?, limit?} 类型筛选和条数限制
 * @returns {Promise<Array>} 实验运行摘要列表
 */
export function listExperiments(params) {
  return request.get('/api/research/experiments', { params })
}

/**
 * 获取指定实验的详情（含完整指标列表）
 * @param {number} id - 实验运行ID
 * @returns {Promise<Object>} 实验运行详情
 */
export function getExperimentDetail(id) {
  return request.get(`/api/research/experiments/${encodeURIComponent(id)}`)
}

/**
 * 获取研究总览（最新MPC/RL/RAG运行关键指标）
 * @returns {Promise<Object>} 总览数据 {latestMpc, latestRl, latestRag}
 */
export function getResearchOverview() {
  return request.get('/api/research/overview')
}

/**
 * RAG证据问答查询
 * @param {Object} data - {question, topK?} 问题和可选topK
 * @returns {Promise<Object>} 回答/拒答响应
 */
export function queryRag(data) {
  return request.post('/api/research/rag/query', data)
}
