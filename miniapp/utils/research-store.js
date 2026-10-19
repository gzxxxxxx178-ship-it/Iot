import { request } from '../api/request.js'

// 获取当前账号最近的研究实验总览。
export function getResearchOverview() {
  return request('/api/research/overview')
}

// 查询当前账号的研究运行列表。
export function listExperiments(limit = 50) {
  return request('/api/research/experiments', { params: { limit } })
}

// 查询单次研究运行的完整指标。
export function getExperimentDetail(id) {
  return request('/api/research/experiments/' + encodeURIComponent(id))
}

// 使用后端内部知识库执行带证据的 RAG 问答。
export function queryResearchEvidence(question, topK = 5) {
  return request('/api/research/rag/query', { method: 'POST', data: { question, topK } })
}
