import { reactive } from 'vue'
import { queryHistory } from './live-store.js'
import { session, onSessionReset } from './session.js'

export const history = reactive({ rows: [], page: -1, totalPages: 0, totalElements: 0, loading: false, error: '', deviceId: '', hours: 24, start: 0, end: 0 })
let requestId = 0

// 清除历史数据并使尚未完成的分页请求失效。
export function resetHistory() {
  requestId += 1
  Object.assign(history, { rows: [], page: -1, totalPages: 0, totalElements: 0, loading: false, error: '', deviceId: '', hours: 24, start: 0, end: 0 })
}
onSessionReset(resetHistory)

// 更换筛选条件或刷新时，冻结本次查询的截止时间。
export async function reloadHistory(deviceId = history.deviceId, range = history.hours) {
  requestId += 1
  const end = typeof range === 'object' && range ? Number(range.end) : Date.now()
  const start = typeof range === 'object' && range ? Number(range.start) : end - Number(range) * 3600000
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) throw new Error('历史时间范围无效')
  Object.assign(history, { rows: [], page: -1, totalPages: 0, totalElements: 0, loading: false, error: '', deviceId, hours: (end - start) / 3600000, start, end })
  await loadHistory()
}

// 顺序追加历史分页，筛选或会话变化时忽略迟到的结果。
export async function loadHistory() {
  if (!session.token || history.loading || (history.page >= 0 && history.page + 1 >= history.totalPages)) return
  const id = ++requestId
  const generation = session.generation
  const page = history.page + 1
  history.loading = true; history.error = ''
  try {
    const result = await queryHistory({ deviceId: history.deviceId, start: history.start, end: history.end, page })
    if (id !== requestId || generation !== session.generation) return
    if (result.page !== page || result.totalPages < 0 || !Number.isInteger(result.totalElements) || result.totalElements < 0) throw new Error('历史分页响应无效')
    history.rows = [...history.rows, ...result.content]
    history.page = page; history.totalPages = result.totalPages; history.totalElements = result.totalElements
  } catch (err) {
    if (id === requestId && !err.stale) history.error = err.message
  } finally { if (id === requestId) history.loading = false }
}
