import { request } from '../api/request.js'
import { APP_CONFIG } from '../config/index.js'
import { session } from './session.js'

const RECONNECT_DELAY_MS = 5000

// 创建经过短期票据鉴权的实时传感器连接。
export function createRealtimeConnection(onReading, onStatus) {
  let active = false
  let connecting = false
  let generation = null
  let socketTask = null
  let reconnectTimer = null

  // 仅向仍属于当前账号的页面报告连接状态。
  function reportStatus(status, message = '') {
    if (active && generation === session.generation && onStatus) onStatus(status, message)
  }

  // 安排单次重连，避免关闭和错误事件重复创建定时器。
  function scheduleReconnect() {
    if (!active || generation !== session.generation || reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, RECONNECT_DELAY_MS)
  }

  // 领取一次性票据并连接后端 WebSocket。
  async function connect() {
    if (!active || connecting || socketTask || generation !== session.generation) return
    if (!session.token) { reportStatus('offline', '请先登录账号'); return }
    connecting = true
    reportStatus('connecting')
    try {
      const ticketData = await request('/api/ws/ticket', { method: 'POST' })
      if (!active || generation !== session.generation) return
      if (!ticketData?.ticket) throw new Error('后端未返回 WebSocket 连接票据')
      const socketUrl = APP_CONFIG.apiBaseUrl.replace(/^http/, 'ws')
        + '/ws/sensor?ticket=' + encodeURIComponent(ticketData.ticket)
      const task = uni.connectSocket({ url: socketUrl, complete: () => {} })
      if (!task) throw new Error('微信未能创建 WebSocket 连接')
      socketTask = task

      // 连接成功后更新页面状态。
      task.onOpen(() => {
        if (!active || generation !== session.generation || socketTask !== task) return
        reportStatus('online')
      })

      // 解析实时遥测消息并丢弃无效或过期会话数据。
      task.onMessage((event) => {
        if (!active || generation !== session.generation || socketTask !== task || !onReading) return
        try {
          const payload = JSON.parse(event.data)
          if (Array.isArray(payload)) payload.forEach((item) => { if (item && typeof item === 'object') onReading(item) })
          else if (payload && typeof payload === 'object') onReading(payload)
        } catch { /* 忽略非 JSON 消息 */ }
      })

      // 连接错误时提示离线并等待重连。
      task.onError((event) => {
        if (socketTask !== task) return
        socketTask = null
        reportStatus('offline', event?.errMsg || '实时连接中断，正在重连')
        try { task.close({ code: 1000, reason: 'reconnect after socket error' }) } catch { /* 等待下次票据重连 */ }
        scheduleReconnect()
      })

      // 连接关闭后清理旧实例并自动重新领取票据。
      task.onClose(() => {
        if (socketTask !== task) return
        socketTask = null
        reportStatus('offline', '连接已断开，正在重连')
        scheduleReconnect()
      })
    } catch (error) {
      if (active && generation === session.generation && !error?.stale) {
        reportStatus('offline', error.message || '实时连接失败，正在重连')
        scheduleReconnect()
      }
    } finally {
      connecting = false
    }
  }

  // 启动连接并绑定当前账号会话代次。
  function start() {
    if (active) return
    active = true
    generation = session.generation
    connect()
  }

  // 停止重连并关闭当前页面持有的连接。
  function stop() {
    active = false
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = null
    const task = socketTask
    socketTask = null
    if (task) task.close({ code: 1000, reason: 'page hidden' })
  }

  return { start, stop }
}
