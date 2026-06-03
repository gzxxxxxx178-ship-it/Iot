/**
 * 格式化时间戳或日期数组为 HH:mm:ss
 */
export function formatTime(val) {
  if (!val) return ''
  if (Array.isArray(val)) {
    const d = new Date(val[0], val[1] - 1, val[2], val[3], val[4], val[5])
    return d.toLocaleTimeString([], { hour12: false })
  }
  return new Date(val).toLocaleTimeString([], { hour12: false })
}

/**
 * 格式化时间戳为 YYYY-MM-DD HH:mm:ss
 */
export function formatDateTime(timestamp) {
  if (!timestamp) return ''
  const d = new Date(timestamp)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 保留小数点后一位
 */
export function formatDecimal(val, digits = 1) {
  if (val == null) return '--'
  return Number(val).toFixed(digits)
}
