// 将自动化执行状态转换为与 Web 端一致的可读文字。
export function automationStatusLabel(status) {
  return {
    SUCCESS: '成功',
    DISPATCHED: '等待设备确认',
    ACKNOWLEDGED: '设备已确认',
    TIMED_OUT: '设备超时',
    REJECTED: '设备拒绝',
    FAILED: '失败'
  }[status] || '未知状态'
}

// 按命令真实进度选择成功、等待确认或失败的状态样式。
export function automationStatusClass(status) {
  if (status === 'SUCCESS' || status === 'ACKNOWLEDGED') return 'is-enabled'
  if (status === 'DISPATCHED') return 'is-pending'
  return 'is-failed'
}
