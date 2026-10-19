<template>
  <view class="screen-page">
    <view class="screen-header">
      <view class="title-group">
        <text class="screen-title">智慧农业数据大屏</text>
        <text class="screen-subtitle">{{ currentTime || '实时监测' }} · {{ connectionLabel }}</text>
      </view>
      <button class="exit-button" @tap="exitScreen">退出</button>
    </view>

    <view v-if="!session.token" class="screen-card login-card">
      <text class="empty-title">登录后查看实时数据大屏</text>
      <button class="screen-action" @tap="goLogin">前往登录</button>
    </view>

    <template v-else>
      <view v-if="loadError" class="screen-card error-card">
        <text>{{ loadError }}</text>
        <button class="screen-action" :disabled="loading" @tap="loadScreenData">{{ loading ? '刷新中…' : '重新加载' }}</button>
      </view>

      <view class="stats-grid">
        <view class="screen-card stat-card"><text class="stat-label">设备总数</text><text class="stat-value">{{ live.stats?.deviceCount ?? '--' }}</text></view>
        <view class="screen-card stat-card"><text class="stat-label">在线设备</text><text class="stat-value online-value">{{ live.stats?.onlineCount ?? '--' }}</text></view>
        <view class="screen-card stat-card"><text class="stat-label">离线设备</text><text class="stat-value offline-value">{{ offlineCount }}</text></view>
        <view class="screen-card stat-card"><text class="stat-label">温度均值</text><text class="stat-value">{{ formatReading(live.stats?.avgTemp) }}<text class="stat-unit">℃</text></text></view>
        <view class="screen-card stat-card"><text class="stat-label">湿度均值</text><text class="stat-value">{{ formatReading(live.stats?.avgHum) }}<text class="stat-unit">%</text></text></view>
      </view>

      <view class="screen-card live-card">
        <view class="card-heading"><text class="card-title">实时传感器状态</text><text class="data-time">{{ latestPoint ? formatTime(latestPoint.time) : '等待新数据' }}</text></view>
        <view class="sensor-grid">
          <view class="sensor-item"><text class="sensor-label">水位</text><text class="sensor-value">{{ formatReading(latestPoint?.water) }}<text class="stat-unit"> ADC</text></text></view>
          <view class="sensor-item"><text class="sensor-label">信号</text><text class="sensor-value">{{ latestPoint?.rssi === null || latestPoint?.rssi === undefined ? '--' : formatReading(latestPoint.rssi) }}<text class="stat-unit"> dBm</text></text></view>
          <view class="sensor-item"><text class="sensor-label">联动</text><text class="sensor-value" :class="latestPoint?.linkage ? 'online-value' : ''">{{ linkageLabel }}</text></view>
          <view class="sensor-item"><text class="sensor-label">发送次数</text><text class="sensor-value">{{ formatReading(latestPoint?.sendCount) }}</text></view>
        </view>
        <text class="data-note">实时连接只在设备产生新上报时更新；传感器缺测显示 --。</text>
      </view>

      <view class="screen-card distribution-card">
        <view class="card-heading"><text class="card-title">设备状态分布</text><button class="screen-refresh" :disabled="loading" @tap="loadScreenData">刷新</button></view>
        <text v-if="distributionError" class="inline-error">{{ distributionError }}</text>
        <BarTrend v-else-if="distribution.length" :items="distribution" color="#4BA77A" :max-bar-height="130" />
        <text v-else class="empty-note">暂无设备状态数据</text>
      </view>

      <view class="screen-card chart-card">
        <view class="card-heading"><text class="card-title">温度趋势（最近24小时）</text><text class="chart-count">{{ trendRows.length }} 条</text></view>
        <BarTrend v-if="temperatureTrend.length" :items="temperatureTrend" color="#55B88B" :max-bar-height="160" />
        <text v-else class="empty-note">暂无温度趋势数据</text>
      </view>

      <view class="screen-card chart-card">
        <view class="card-heading"><text class="card-title">湿度趋势（最近24小时）</text><text class="chart-count">{{ trendRows.length }} 条</text></view>
        <BarTrend v-if="humidityTrend.length" :items="humidityTrend" color="#56A6C5" :max-bar-height="160" />
        <text v-else class="empty-note">暂无湿度趋势数据</text>
      </view>

      <view v-if="live.error" class="screen-card inline-error">{{ live.error }}</view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import BarTrend from '../../components/BarTrend.vue'
import { session } from '../../utils/session.js'
import { live, refreshLive, queryHistory, formatReading, parseServerTime } from '../../utils/live-store.js'
import { request } from '../../api/request.js'
import { createRealtimeConnection } from '../../utils/realtime.js'
import { buildTrend } from '../../utils/trend.js'

const historyRows = ref([])
const realtimeRows = ref([])
const latestPoint = ref(null)
const distribution = ref([])
const distributionError = ref('')
const loadError = ref('')
const loading = ref(false)
const currentTime = ref('')
const connectionStatus = ref('offline')
let connection = null
let pageVisible = false
let clockTimer = null
let loadRequestId = 0

const offlineCount = computed(() => live.stats ? Math.max(0, Number(live.stats.deviceCount) - Number(live.stats.onlineCount)) : '--')
const connectionLabel = computed(() => ({ online: '实时连接已建立', connecting: '实时连接中', offline: '实时连接已断开' })[connectionStatus.value] || '实时状态未知')
const linkageLabel = computed(() => latestPoint.value?.linkage === true ? 'ON' : latestPoint.value?.linkage === false ? 'OFF' : '--')
const trendRows = computed(() => [...realtimeRows.value.slice().reverse(), ...historyRows.value].slice(0, 100))
const temperatureTrend = computed(() => buildTrend(trendRows.value, 'temp', (time) => formatTime(time).slice(-5), 12))
const humidityTrend = computed(() => buildTrend(trendRows.value, 'hum', (time) => formatTime(time).slice(-5), 12))

// 页面显示时刷新统计、历史趋势并建立实时 WebSocket 连接。
onShow(startScreen)

// 页面隐藏时停止时钟、历史请求更新和实时连接。
onHide(stopScreen)

// 账号切换时清空旧账号数据并为新会话重新加载。
watch(() => session.generation, () => {
  stopScreen()
  historyRows.value = []; realtimeRows.value = []; latestPoint.value = null
  distribution.value = []; distributionError.value = ''; loadError.value = ''
  if (session.token) startScreen()
})

// 启动大屏数据刷新、每秒时钟和实时传感器连接。
function startScreen() {
  pageVisible = true
  if (!session.token) return
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  refreshLive()
  loadScreenData()
  startRealtime()
}

// 停止当前页面持有的轮询和实时连接。
function stopScreen() {
  pageVisible = false
  clearInterval(clockTimer); clockTimer = null
  loadRequestId += 1
  loading.value = false
  if (connection) connection.stop()
  connection = null; connectionStatus.value = 'offline'
}

// 并行加载24小时历史和设备状态分布，隔离旧会话及迟到响应。
async function loadScreenData() {
  if (!session.token) return
  const requestId = ++loadRequestId
  const generation = session.generation
  const end = Date.now()
  loading.value = true; loadError.value = ''; distributionError.value = ''
  const results = await Promise.allSettled([
    queryHistory({ deviceId: '', start: end - 24 * 60 * 60 * 1000, end, page: 0, size: 100 }),
    request('/api/dashboard/device-status')
  ])
  if (requestId !== loadRequestId || generation !== session.generation) return
  const failures = []
  if (results[0].status === 'fulfilled') {
    const baseline = Array.isArray(results[0].value.content) ? results[0].value.content : []
    historyRows.value = baseline
    latestPoint.value = realtimeRows.value.at(-1) || baseline[0] || null
  } else if (!results[0].reason?.stale) failures.push(results[0].reason?.message || '历史趋势读取失败')
  if (results[1].status === 'fulfilled' && Array.isArray(results[1].value)) {
    distribution.value = results[1].value.map((item) => ({ label: item.name, value: Number(item.value) || 0 }))
  } else if (results[1].status === 'rejected' && !results[1].reason?.stale) {
    distributionError.value = results[1].reason?.message || '设备状态分布读取失败'
  }
  loadError.value = failures.join('；')
  loading.value = false
}

// 建立当前账号的短期票据 WebSocket 连接并接收实时遥测。
function startRealtime() {
  if (!session.token || !pageVisible || connection) return
  connection = createRealtimeConnection(addRealtimePoint, (status) => { connectionStatus.value = status })
  connection.start()
}

// 将收到的传感器上报追加到最近趋势并更新顶部实时状态。
function addRealtimePoint(item) {
  const time = parseServerTime(item.serverReceivedTime) || Date.now()
  const point = {
    id: String(item.deviceId || '') + '-' + String(item.serverReceivedTime || time), time,
    temp: numeric(item.temperature), hum: numeric(item.humidity), water: numeric(item.water),
    rssi: numeric(item.rssi), linkage: typeof item.linkage === 'boolean' ? item.linkage : null,
    sendCount: numeric(item.sendCount)
  }
  realtimeRows.value = [...realtimeRows.value, point].slice(-60)
  latestPoint.value = point
}

// 每秒刷新大屏显示的本地时间。
function updateClock() { currentTime.value = new Date().toLocaleString('zh-CN', { hour12: false }) }

// 格式化历史时间和实时数据时间。
function formatTime(value) {
  if (!value) return '--'
  const date = typeof value === 'number' ? new Date(value) : new Date(value)
  return Number.isNaN(date.getTime()) ? '--' : date.toLocaleTimeString('zh-CN', { hour12: false })
}

// 将缺失或非有限实时值转换为空值。
function numeric(value) { return typeof value === 'number' && Number.isFinite(value) ? value : null }

// 退出数据大屏并回到概览页。
function exitScreen() { uni.switchTab({ url: '/pages/overview/overview' }) }

// 打开账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style lang="scss" scoped>
.screen-page { min-height: 100vh; padding: calc(env(safe-area-inset-top) + 20rpx) 22rpx 48rpx; box-sizing: border-box; background: #101820; color: #e8eef2; }
.screen-header { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 8rpx 4rpx 22rpx; border-bottom: 1rpx solid rgba(255,255,255,.12); }
.title-group { display: flex; min-width: 0; flex-direction: column; gap: 8rpx; }
.screen-title { color: #f4f7f8; font-size: 31rpx; font-weight: 700; letter-spacing: 1rpx; }
.screen-subtitle { color: #9aabb4; font-size: 20rpx; }
.exit-button { flex: none; margin: 0; padding: 0 20rpx; height: 54rpx; line-height: 54rpx; border: 1rpx solid rgba(255,255,255,.2); border-radius: 28rpx; background: transparent; color: #d4e0e5; font-size: 21rpx; }
.stats-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; margin: 16rpx 0; }
.screen-card { box-sizing: border-box; border: 1rpx solid rgba(255,255,255,.08); border-radius: 15rpx; background: #18242d; color: #e8eef2; }
.stat-card { display: flex; min-height: 120rpx; flex-direction: column; justify-content: space-between; padding: 17rpx 20rpx; }
.stat-label, .sensor-label { color: #a4b3bb; font-size: 20rpx; }
.stat-value { color: #f2f6f7; font-size: 38rpx; font-weight: 700; }
.stat-unit { color: #a4b3bb; font-size: 19rpx; font-weight: 400; }
.online-value { color: #64cf98; }
.offline-value { color: #e9a16f; }
.live-card, .distribution-card, .chart-card { margin-top: 14rpx; padding: 20rpx; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; margin-bottom: 16rpx; }
.card-title { color: #edf3f5; font-size: 24rpx; font-weight: 600; }
.data-time, .chart-count { color: #98a9b1; font-size: 19rpx; }
.sensor-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14rpx; }
.sensor-item { display: flex; min-width: 0; flex-direction: column; gap: 8rpx; padding: 14rpx; border-radius: 10rpx; background: #1d2c36; }
.sensor-value { overflow-wrap: anywhere; color: #f3f7f8; font-size: 30rpx; font-weight: 600; }
.data-note { display: block; margin-top: 14rpx; color: #8fa1aa; font-size: 19rpx; line-height: 1.5; }
.screen-refresh { flex: none; height: 46rpx; line-height: 46rpx; margin: 0; padding: 0 16rpx; border-radius: 24rpx; background: #263b45; color: #dce7eb; font-size: 20rpx; }
.empty-note { display: block; padding: 22rpx 0; color: #91a1a9; font-size: 21rpx; text-align: center; }
.error-card, .login-card { padding: 24rpx; color: #f0ae8e; font-size: 23rpx; line-height: 1.5; }
.inline-error { color: #efa18e; font-size: 21rpx; }
.screen-action { display: block; margin: 18rpx 0 0 auto; padding: 0 22rpx; height: 54rpx; line-height: 54rpx; border-radius: 28rpx; background: #2e7556; color: #fff; font-size: 21rpx; }
.empty-title { color: #e8eef2; font-size: 24rpx; }
</style>
