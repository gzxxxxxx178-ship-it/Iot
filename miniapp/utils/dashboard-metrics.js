import { buildTrend } from './trend.js'

// 汇总服务端最近历史，生成与 Web 仪表盘一致的辅助指标和趋势。
export function summarizeRecentRows(rows, labelForTime) {
  const recent = Array.isArray(rows) ? rows : []
  const average = (field) => {
    const values = recent.map((row) => row[field]).filter((value) => typeof value === 'number' && Number.isFinite(value))
    return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
  }
  const messageValues = recent.map((row) => row.sendCount).filter((value) => typeof value === 'number' && Number.isFinite(value))
  const latest = recent[0]
  return {
    avgWater: average('water'),
    latestRssi: latest?.rssi ?? null,
    latestLinkage: latest?.linkage === true ? 'ON' : latest?.linkage === false ? 'OFF' : '--',
    recentMessages: recent.length ? messageValues.reduce((sum, value) => sum + value, 0) : null,
    temperatureTrend: buildTrend(recent, 'temp', labelForTime, 8),
    humidityTrend: buildTrend(recent, 'hum', labelForTime, 8)
  }
}
