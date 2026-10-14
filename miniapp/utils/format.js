// 将时间戳格式化为本地日期时间，缺测显示占位。
export function formatDateTime(time) {
  if (!time) return '--'
  const date = new Date(time)
  if (!Number.isFinite(date.getTime())) return '--'
  // 补齐两位日期字段。
  const pad = (n) => String(n).padStart(2, '0')
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds())
}
