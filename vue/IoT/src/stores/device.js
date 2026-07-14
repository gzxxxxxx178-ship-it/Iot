import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getDeviceList, getHistoryData, getSensorHistory } from '../api/device'
import { getDashboardStats } from '../api/dashboard'

/**
 * 设备与传感器数据状态管理
 *
 * Dashboard、Monitor、Screen 三个页面共享同一份设备数据和传感器历史，
 * 避免每个页面各自发起重复请求。WebSocket 实时数据通过 addDataPoint() 追加。
 */
export const useDeviceStore = defineStore('device', () => {
  // ==================== State ====================

  /** 设备列表（含最新读数） */
  const devices = ref([])

  /** 仪表盘统计数据 */
  const stats = ref({ deviceCount: 0, onlineCount: 0, avgTemp: '--', avgHum: '--' })

  /** 传感器历史数据点（最多 60 条） */
  const dataPoints = ref([])

  /** 最新一条传感器数据 */
  const latest = ref(null)

  /** 数据是否已加载 */
  const loaded = ref(false)

  /** 加载状态 */
  const loading = ref(false)

  // ==================== Getters ====================

  /** 在线设备数（由后端依据最后上报时间计算） */
  const onlineCount = computed(() => stats.value.onlineCount || 0)

  /** 时间标签序列（图表用） */
  const timeLabels = computed(() => dataPoints.value.map((p) => p.time))

  /** 温度序列（图表用） */
  const tempSeries = computed(() => dataPoints.value.map((p) => p.temperature))

  /** 湿度序列（图表用） */
  const humSeries = computed(() => dataPoints.value.map((p) => p.humidity))

  // ==================== Actions ====================

  // 加载设备列表
  async function fetchDevices() {
    try {
      devices.value = await getDeviceList()
    } catch {}
  }

  // 加载仪表盘统计
  async function fetchStats() {
    try {
      stats.value = await getDashboardStats()
    } catch {}
  }

  // 加载传感器历史数据
  async function fetchHistory() {
    loading.value = true
    try {
      const data = await getHistoryData()
      dataPoints.value = data.reverse().map((item) => ({
        time: formatTime(item.serverReceivedTime || item.timestamp),
        temperature: item.temperature,
        humidity: item.humidity,
        water: item.water,
        rssi: item.rssi,
        linkage: item.linkage,
        sendCount: item.sendCount,
      }))
      if (data.length) {
        latest.value = data[data.length - 1]
      }
      loaded.value = true
    } catch {
    } finally {
      loading.value = false
    }
  }

  // 按时间范围查询
  async function fetchHistoryByRange(start, end) {
    try {
      const data = await getSensorHistory({ start, end })
      return data
    } catch {
      return []
    }
  }

  // WebSocket 实时追加一条数据点，保持窗口不超过 60 条
  function addDataPoint(item) {
    const point = {
      time: formatTime(item.serverReceivedTime || item.timestamp),
      temperature: item.temperature,
      humidity: item.humidity,
      water: item.water,
      rssi: item.rssi,
      linkage: item.linkage,
      sendCount: item.sendCount,
    }
    dataPoints.value.push(point)
    latest.value = item
    if (dataPoints.value.length > 60) {
      dataPoints.value.shift()
    }
  }

  return {
    devices, stats, dataPoints, latest, loaded, loading,
    onlineCount, timeLabels, tempSeries, humSeries,
    fetchDevices, fetchStats, fetchHistory, fetchHistoryByRange, addDataPoint,
  }
})

// 工具函数：格式化时间为 HH:mm:ss（和 utils/format.js 保持一致，避免循环依赖）
function formatTime(val) {
  if (!val) return '--:--:--'
  const d = val instanceof Date ? val : new Date(val)
  if (isNaN(d.getTime())) return '--:--:--'
  return d.toTimeString().slice(0, 8)
}
