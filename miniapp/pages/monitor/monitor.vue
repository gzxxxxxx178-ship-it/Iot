<template>
  <view class="page">
    <PageHeader title="实时监控" subtitle="WebSocket 实时传感器数据" />

    <view v-if="!session.token" class="card">
      <EmptyState text="请先登录账号查看实时监控" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>
    <template v-else>
      <view class="card connection-card">
        <view class="connection-copy">
          <view class="status-line">
            <view class="status-dot" :class="'status-' + connectionStatus"></view>
            <text class="section-title">{{ connectionLabel }}</text>
          </view>
          <text class="hint">{{ connectionMessage || (latestPoint ? '最近数据 ' + latestPoint.timeText : '等待设备上报新数据') }}</text>
        </view>
        <button class="btn-secondary small-button" :disabled="connectionStatus === 'connecting'" @tap="reconnect">{{ connectionStatus === 'connecting' ? '连接中…' : '重新连接' }}</button>
      </view>

      <view class="metrics-grid">
        <MetricCard label="实时温度" :value="formatReading(latestPoint?.temperature)" unit="℃" />
        <MetricCard label="实时湿度" :value="formatReading(latestPoint?.humidity)" unit="%" color="#2563EB" />
        <MetricCard label="水位原始值" :value="formatReading(latestPoint?.water)" unit="ADC" color="#D97706" />
        <MetricCard label="信号强度" :value="signalText" unit="" color="#7C3AED" />
      </view>

      <view class="card data-summary">
        <view class="summary-item"><text class="summary-label">实时数据点</text><text class="summary-value">{{ points.length }}</text></view>
        <view class="summary-item"><text class="summary-label">发送次数</text><text class="summary-value">{{ latestPoint?.sendCount ?? '--' }}</text></view>
        <view class="summary-item"><text class="summary-label">联动状态</text><text class="summary-value" :class="latestPoint?.linkage ? 'is-on' : ''">{{ linkageText }}</text></view>
      </view>

      <view class="section-heading">
        <text class="section-title">温湿度趋势</text>
        <button class="btn-secondary small-button" :disabled="historyLoading" @tap="loadRecentHistory">{{ historyLoading ? '更新中…' : '刷新历史' }}</button>
      </view>
      <view v-if="historyError" class="card error-card">
        <text class="error-text">{{ historyError }}</text>
        <button class="btn-secondary small-button retry-button" @tap="loadRecentHistory">重试</button>
      </view>
      <view v-if="historyLoading && !points.length" class="card loading-card"><text class="hint">正在加载最近24小时数据…</text></view>
      <view v-if="temperatureItems.length" class="card chart-card">
        <text class="chart-title">温度（℃）</text>
        <BarTrend :items="temperatureItems" color="#1F6B4F" :max-bar-height="160" />
      </view>
      <view v-if="humidityItems.length" class="card chart-card">
        <text class="chart-title">湿度（%）</text>
        <BarTrend :items="humidityItems" color="#2563EB" :max-bar-height="160" />
      </view>
      <view v-if="!historyLoading && !points.length" class="card">
        <EmptyState text="暂无历史或实时传感器数据" />
      </view>
      <view v-if="live.stats" class="card device-summary">
        <text class="hint">当前账号可访问 {{ live.stats.deviceCount }} 台设备，在线 {{ live.stats.onlineCount }} 台。实时推送仅在设备产生新上报时更新。</text>
        <button class="btn-secondary action-button" @tap="goDevices">查看设备与控制回执</button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import MetricCard from '../../components/MetricCard.vue'
import BarTrend from '../../components/BarTrend.vue'
import EmptyState from '../../components/EmptyState.vue'
import { live, queryHistory, refreshLive, formatReading, parseServerTime } from '../../utils/live-store.js'
import { session } from '../../utils/session.js'
import { createRealtimeConnection } from '../../utils/realtime.js'

const points = ref([])
const recentRealtime = ref([])
const latestPoint = ref(null)
const connectionStatus = ref('offline')
const connectionMessage = ref('')
const historyLoading = ref(false)
const historyError = ref('')
let connection = null
let pageVisible = false
let historyRequestId = 0

const connectionLabel = computed(() => ({ online: '实时连接已建立', connecting: '正在连接实时数据', offline: '实时连接已断开' })[connectionStatus.value] || '实时连接状态未知')
const signalText = computed(() => latestPoint.value?.rssi === null || latestPoint.value?.rssi === undefined ? '--' : latestPoint.value.rssi + ' dBm')
const linkageText = computed(() => latestPoint.value?.linkage === true ? '已联动' : latestPoint.value?.linkage === false ? '未联动' : '--')
const temperatureItems = computed(() => chartItems('temperature'))
const humidityItems = computed(() => chartItems('humidity'))

// 页面显示时加载历史基线、刷新设备统计并建立实时连接。
onShow(() => {
  pageVisible = true
  if (!session.token) return
  refreshLive()
  loadRecentHistory()
  startRealtime()
})

// 页面隐藏时关闭连接，避免后台页面继续占用连接和重连任务。
onHide(() => {
  pageVisible = false
  stopRealtime()
})

// 账号切换时清除旧账号遥测，并仅为当前页面和会话重建连接。
watch(() => session.generation, () => {
  stopRealtime()
  historyRequestId += 1
  points.value = []
  recentRealtime.value = []
  latestPoint.value = null
  historyError.value = ''
  if (pageVisible && session.token) {
    refreshLive()
    loadRecentHistory()
    startRealtime()
  } else {
    stopRealtime()
  }
})

// 将传感器消息追加到最近60个趋势点并更新实时指标。
function addReading(item) {
  const time = parseServerTime(item.serverReceivedTime) || Date.now()
  const point = {
    time,
    timeText: new Date(time).toLocaleTimeString('zh-CN', { hour12: false }),
    temperature: numeric(item.temperature),
    humidity: numeric(item.humidity),
    water: numeric(item.water),
    rssi: numeric(item.rssi),
    linkage: typeof item.linkage === 'boolean' ? item.linkage : null,
    sendCount: numeric(item.sendCount)
  }
  recentRealtime.value = [...recentRealtime.value, point].slice(-60)
  points.value = [...points.value, point].slice(-60)
  latestPoint.value = point
}

// 从最近24小时历史数据初始化趋势，并拒绝切换账号后的迟到响应。
async function loadRecentHistory() {
  if (!session.token) return
  const requestId = ++historyRequestId
  const generation = session.generation
  const end = Date.now()
  historyLoading.value = true
  historyError.value = ''
  try {
    const result = await queryHistory({ deviceId: '', start: end - 24 * 60 * 60 * 1000, end, page: 0, size: 60 })
    if (requestId !== historyRequestId || generation !== session.generation) return
    const baseline = result.content.map(mapHistoryPoint).filter(Boolean)
    points.value = [...baseline, ...recentRealtime.value].sort((a, b) => a.time - b.time).slice(-60)
    latestPoint.value = points.value[points.value.length - 1] || null
  } catch (error) {
    if (requestId === historyRequestId && !error?.stale) historyError.value = error.message || '历史数据加载失败'
  } finally {
    if (requestId === historyRequestId) historyLoading.value = false
  }
}

// 将历史接口字段映射为页面趋势点。
function mapHistoryPoint(item) {
  const time = item.time || parseServerTime(item.serverReceivedTime)
  if (!time) return null
  return {
    time,
    timeText: new Date(time).toLocaleTimeString('zh-CN', { hour12: false }),
    temperature: numeric(item.temp ?? item.temperature),
    humidity: numeric(item.hum ?? item.humidity),
    water: numeric(item.water),
    rssi: numeric(item.rssi),
    linkage: typeof item.linkage === 'boolean' ? item.linkage : null,
    sendCount: numeric(item.sendCount)
  }
}

// 为单项趋势图提供最近8个有效读数及其时间标签。
function chartItems(metric) {
  return points.value.filter((point) => point[metric] !== null).slice(-8).map((point) => ({ label: point.timeText.slice(0, 5), value: point[metric] }))
}

// 将空值和非数值读数统一视为缺测。
function numeric(value) { return typeof value === 'number' && Number.isFinite(value) ? value : null }

// 创建实时连接并把连接状态和遥测数据绑定到当前页面。
function startRealtime() {
  if (!session.token || !pageVisible || connection) return
  connection = createRealtimeConnection(addReading, (status, message) => {
    connectionStatus.value = status
    connectionMessage.value = message
  })
  connectionStatus.value = 'connecting'
  connectionMessage.value = ''
  connection.start()
}

// 关闭当前实时连接并清空页面连接句柄。
function stopRealtime() {
  if (connection) connection.stop()
  connection = null
  connectionStatus.value = 'offline'
  connectionMessage.value = ''
}

// 由用户主动重新领取票据并建立实时连接。
function reconnect() {
  stopRealtime()
  startRealtime()
}

// 打开账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }

// 打开设备列表及人工控制入口。
function goDevices() { uni.switchTab({ url: '/pages/devices/devices' }) }
</script>

<style lang="scss" scoped>
.connection-card { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.connection-copy { display: flex; min-width: 0; flex-direction: column; gap: 8rpx; }
.status-line { display: flex; align-items: center; gap: 12rpx; }
.status-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: $color-text-light; }
.status-online { background: $color-primary; }
.status-connecting { background: $color-amber; }
.status-offline { background: $color-red; }
.small-button { flex: none; min-width: 140rpx; height: 60rpx; line-height: 60rpx; margin: 0; padding: 0 16rpx; font-size: 22rpx; }
.metrics-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; margin: 16rpx 0; }
.data-summary { display: flex; justify-content: space-between; gap: 12rpx; }
.summary-item { display: flex; min-width: 0; flex-direction: column; gap: 8rpx; }
.summary-label { color: $color-text-secondary; font-size: 21rpx; }
.summary-value { color: $color-text; font-size: 26rpx; font-weight: 600; }
.is-on { color: $color-primary; }
.section-heading { display: flex; align-items: center; justify-content: space-between; padding: 18rpx 0 14rpx; }
.chart-card { margin-bottom: 14rpx; overflow: hidden; }
.chart-title { display: block; margin-bottom: 12rpx; color: $color-text-secondary; font-size: 23rpx; }
.error-card { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.error-text { color: $color-red; font-size: 23rpx; }
.loading-card { padding: 30rpx; }
.retry-button { flex: none; }
.action-button { width: 100%; margin-top: 16rpx; }
.device-summary { margin-top: 16rpx; }
</style>
