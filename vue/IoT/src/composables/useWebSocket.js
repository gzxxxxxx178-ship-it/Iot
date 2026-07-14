import { ref, onUnmounted } from 'vue'
import { createWebSocketTicket } from '../api/websocket'

// WebSocket 连接管理 composable
// 参数 onMessage: 收到消息时的回调函数
// 返回 status (connecting/online/offline) 和 disconnect 方法
export function useWebSocket(onMessage) {
  const status = ref('connecting')
  let socket = null
  let reconnectTimer = null
  let connecting = false
  let stopped = false

  // 安排一次延迟重连并避免重复计时器
  function scheduleReconnect() {
    clearTimeout(reconnectTimer)
    if (!stopped) {
      reconnectTimer = setTimeout(connect, 5000)
    }
  }

  // 领取一次性票据并建立经过鉴权的WebSocket连接
  async function connect() {
    if (stopped || connecting || socket) return
    connecting = true
    status.value = 'connecting'

    const apiBase = import.meta.env.VITE_API_BASE_URL || window.location.origin
    const wsBase = import.meta.env.VITE_WS_BASE_URL || apiBase
    try {
      const ticketResponse = await createWebSocketTicket()
      if (stopped) return
      const wsUrl = wsBase.replace(/^http/, 'ws')
        + `/ws/sensor?ticket=${encodeURIComponent(ticketResponse.ticket)}`
      socket = new WebSocket(wsUrl)

      // 连接成功，更新状态为online
      socket.onopen = () => {
        status.value = 'online'
      }

      // 收到消息后解析JSON并调用回调
      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          if (onMessage) onMessage(data)
        } catch {
          // 忽略非JSON消息
        }
      }

      // 连接错误，更新状态为offline
      socket.onerror = () => {
        status.value = 'offline'
      }

      // 连接关闭后清理实例并领取新票据自动重连
      socket.onclose = () => {
        socket = null
        status.value = 'offline'
        scheduleReconnect()
      }
    } catch {
      status.value = 'offline'
      scheduleReconnect()
    } finally {
      connecting = false
    }
  }

  // 断开连接并清除重连定时器
  function disconnect() {
    stopped = true
    clearTimeout(reconnectTimer)
    if (socket) {
      socket.close()
      socket = null
    }
  }

  // 自动连接
  connect()

  // 组件卸载时自动断开
  onUnmounted(disconnect)

  return { status, disconnect }
}
