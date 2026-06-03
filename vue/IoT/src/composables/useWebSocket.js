import { ref, onUnmounted } from 'vue'

export function useWebSocket(onMessage) {
  const status = ref('connecting')
  let socket = null
  let reconnectTimer = null

  function connect() {
    const apiBase = import.meta.env.VITE_API_BASE_URL || window.location.origin
    const wsBase = import.meta.env.VITE_WS_BASE_URL || apiBase
    const wsUrl = wsBase.replace(/^http/, 'ws') + '/ws/sensor'

    socket = new WebSocket(wsUrl)

    socket.onopen = () => {
      status.value = 'online'
    }

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (onMessage) onMessage(data)
      } catch {
        // ignore parse errors
      }
    }

    socket.onerror = () => {
      status.value = 'offline'
    }

    socket.onclose = () => {
      status.value = 'offline'
      reconnectTimer = setTimeout(connect, 5000)
    }
  }

  function disconnect() {
    clearTimeout(reconnectTimer)
    if (socket) {
      socket.close()
      socket = null
    }
  }

  connect()

  onUnmounted(disconnect)

  return { status, disconnect }
}
