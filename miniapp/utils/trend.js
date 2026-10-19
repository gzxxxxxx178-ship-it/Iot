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
