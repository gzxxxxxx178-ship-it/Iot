import { ref, onUnmounted } from 'vue'

// WebSocket 连接管理 composable
// 参数 onMessage: 收到消息时的回调函数
// 返回 status (connecting/online/offline) 和 disconnect 方法
export function useWebSocket(onMessage) {
  const status = ref('connecting')
  let socket = null
  let reconnectTimer = null

  // 建立 WebSocket 连接，自动附加 token 作为查询参数
  function connect() {
    const apiBase = import.meta.env.VITE_API_BASE_URL || window.location.origin
    const wsBase = import.meta.env.VITE_WS_BASE_URL || apiBase
    const wsUrl = wsBase.replace(/^http/, 'ws') + '/ws/sensor'

    socket = new WebSocket(wsUrl)

    // 连接成功，更新状态为 online
    socket.onopen = () => {
      status.value = 'online'
    }

    // 收到消息后解析 JSON 并调用回调
    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (onMessage) onMessage(data)
      } catch {
        // 忽略非 JSON 消息
      }
    }

    // 连接错误，更新状态为 offline
    socket.onerror = () => {
      status.value = 'offline'
    }

    // 连接关闭后 5 秒自动重连
    socket.onclose = () => {
      status.value = 'offline'
      reconnectTimer = setTimeout(connect, 5000)
    }
  }

  // 断开连接并清除重连定时器
  function disconnect() {
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
