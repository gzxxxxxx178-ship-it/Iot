// 将最新历史记录按时间顺序分组并计算单项指标的均值，限制图表点数。
export function buildTrend(rows, field, labelForTime, limit = 8) {
  const points = (Array.isArray(rows) ? rows : [])
    .filter((row) => row[field] !== null && row[field] !== undefined && row[field] !== ''
      && Number.isFinite(Number(row[field])) && row.time)
    .slice()
    .reverse()
  if (!points.length) return []
  const groupCount = Math.min(Math.max(1, limit), points.length)
  return Array.from({ length: groupCount }, (_, index) => {
    const start = Math.floor(index * points.length / groupCount)
    const end = Math.floor((index + 1) * points.length / groupCount)
    const group = points.slice(start, end)
    const average = group.reduce((sum, row) => sum + Number(row[field]), 0) / group.length
    const middleTime = group[Math.floor(group.length / 2)].time
    return { label: labelForTime(middleTime), value: Math.round(average * 10) / 10 }
  })
}

// 合并历史与实时遥测，去重后按最新到最旧排序，匹配 buildTrend 的输入顺序。
export function mergeTrendRows(historyRows, realtimeRows, limit = 100) {
  const byDeviceAndTime = new Map()
  for (const row of [...(Array.isArray(historyRows) ? historyRows : []), ...(Array.isArray(realtimeRows) ? realtimeRows : [])]) {
    if (!row || row.time === null || row.time === undefined || row.time === '' || !Number.isFinite(Number(row.time))) continue
    const key = [row.deviceId || '', row.time, row.temp, row.hum, row.water, row.rssi, row.linkage, row.sendCount].join('\u0000')
    byDeviceAndTime.set(key, row)
  }
  const maximum = Number.isFinite(Number(limit)) ? Math.max(0, Math.floor(Number(limit))) : 100
  const ordered = [...byDeviceAndTime.values()].sort((left, right) => Number(right.time) - Number(left.time))
  return maximum === 0 ? [] : ordered.slice(0, maximum)
}
