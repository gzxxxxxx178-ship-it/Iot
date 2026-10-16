<template>
  <view class="page">
    <PageHeader title="数据看板" :subtitle="live.updatedAt ? '最近刷新 ' + live.updatedAt : '当前设备状态与最新读数'" />

    <view v-if="!session.token" class="card">
      <EmptyState text="请先登录账号查看数据看板" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>
    <template v-else>
      <view v-if="live.error" class="card error-card">
        <text class="error-text">{{ live.error }}</text>
        <button class="btn-secondary retry-button" :disabled="live.loading" @tap="refreshLive">{{ live.loading ? '正在刷新…' : '重试' }}</button>
      </view>
      <view v-if="live.loading && !live.stats" class="card loading-card"><text class="hint">正在加载设备统计…</text></view>

      <view v-if="live.stats" class="metrics-grid">
        <MetricCard label="平均温度" :value="formatReading(live.stats.avgTemp)" unit="℃" />
        <MetricCard label="平均湿度" :value="formatReading(live.stats.avgHum)" unit="%" color="#2563EB" />
        <MetricCard label="在线设备" :value="live.stats.onlineCount" unit="台" />
        <MetricCard label="设备总数" :value="live.stats.deviceCount" unit="台" color="#D97706" />
      </view>
      <view v-if="live.stats" class="card data-note">
        <text class="hint">平均温湿度来自后端在线设备的最新有效读数；无有效读数显示 --。</text>
      </view>

      <view v-if="live.stats" class="card section-card">
        <view class="section-heading">
          <text class="section-title">设备状态分布</text>
          <button class="btn-secondary small-button" :disabled="distributionLoading" @tap="loadDistribution">刷新</button>
        </view>
        <text v-if="distributionError" class="error-text">{{ distributionError }}</text>
        <text v-else-if="distributionLoading && !distributionLoaded" class="hint">正在加载状态分布…</text>
        <BarTrend v-else-if="distribution.length" :items="distribution" color="#1F6B4F" />
        <EmptyState v-else text="暂无设备状态数据" />
        <text class="hint chart-note">柱状图展示设备数量，不代表传感器变化趋势。</text>
      </view>

      <view v-if="live.stats" class="section-heading device-heading">
        <text class="section-title">设备最新读数</text>
        <text class="hint">{{ live.devices.length }} 台</text>
      </view>
      <view v-if="live.stats && live.devices.length" class="device-list">
        <view v-for="device in live.devices" :key="device.id" class="card device-card">
          <view class="device-top">
            <view class="device-name-group">
              <text class="device-name">{{ device.name }}</text>
              <text class="hint">{{ device.id }} · 最近上报 {{ formatDateTime(parseServerTime(device.lastSeen)) }}</text>
            </view>
            <text class="status" :class="device.status === 'online' ? 'online' : 'offline'">{{ device.status === 'online' ? '在线' : '离线' }}</text>
          </view>
          <view class="readings-grid">
            <view class="reading"><text class="reading-value">{{ formatReading(device.temp) }}℃</text><text class="reading-label">温度</text></view>
            <view class="reading"><text class="reading-value">{{ formatReading(device.hum) }}%</text><text class="reading-label">湿度</text></view>
            <view class="reading"><text class="reading-value">{{ device.water ?? '--' }}</text><text class="reading-label">水位 ADC</text></view>
            <view class="reading"><text class="reading-value">{{ device.signal === null ? '--' : formatReading(device.signal) + ' dBm' }}</text><text class="reading-label">信号强度</text></view>
          </view>
        </view>
      </view>
      <EmptyState v-else-if="live.stats" text="当前账号没有可访问设备" />
    </template>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import MetricCard from '../../components/MetricCard.vue'
import BarTrend from '../../components/BarTrend.vue'
import EmptyState from '../../components/EmptyState.vue'
import { request } from '../../api/request.js'
import { session } from '../../utils/session.js'
import { live, refreshLive, formatReading, parseServerTime } from '../../utils/live-store.js'
import { formatDateTime } from '../../utils/format.js'

let distributionRequestId = 0
const distribution = ref([])
const distributionLoading = ref(false)
const distributionLoaded = ref(false)
const distributionError = ref('')

// 账号切换时清除旧设备的分布数据，避免短暂展示其他账号统计。
watch(() => session.generation, () => {
  distributionRequestId += 1
  distribution.value = []
  distributionLoaded.value = false
  distributionLoading.value = false
  distributionError.value = ''
})

// 登录状态变为有效时立即加载当前账号数据。
watch(() => session.token, (token) => {
  if (token) { refreshLive(); loadDistribution() }
})

// 页面显示时刷新账号统计、设备读数和状态分布。
onShow(() => { refreshLive(); loadDistribution() })

// 获取设备在线和离线数量分布，不将其误标为传感器趋势。
async function loadDistribution() {
  if (!session.token) return
  const requestId = ++distributionRequestId
  distributionLoading.value = true
  distributionError.value = ''
  try {
    const result = await request('/api/dashboard/device-status')
    if (requestId !== distributionRequestId) return
    distribution.value = Array.isArray(result) ? result.map((item) => ({ label: item.name, value: item.value })) : []
    distributionLoaded.value = true
  } catch (err) {
    if (requestId !== distributionRequestId || err?.stale) return
    distribution.value = []
    distributionError.value = err.message || '设备状态分布加载失败'
  } finally {
    if (requestId === distributionRequestId) distributionLoading.value = false
  }
}

// 打开账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style lang="scss" scoped>
.metrics-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; margin: 0 0 16rpx; }
.data-note { margin-bottom: 18rpx; }
.section-card { margin-bottom: 18rpx; }
.section-heading { display: flex; align-items: center; justify-content: space-between; padding-bottom: 18rpx; }
.device-heading { padding: 4rpx 0 14rpx; }
.small-button { min-width: 100rpx; height: 56rpx; line-height: 56rpx; margin: 0; padding: 0 16rpx; font-size: 22rpx; }
.chart-note { display: block; margin-top: 12rpx; }
.device-list { display: flex; flex-direction: column; gap: 14rpx; }
.device-card { margin-bottom: 0; }
.device-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12rpx; }
.device-name-group { display: flex; min-width: 0; flex-direction: column; gap: 6rpx; }
.device-name { color: $color-text; font-size: 28rpx; font-weight: 600; }
.status { flex: none; font-size: 23rpx; }
.online { color: $color-primary; }
.offline { color: $color-red; }
.readings-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8rpx; margin-top: 22rpx; }
.reading { display: flex; min-width: 0; flex-direction: column; gap: 6rpx; }
.reading-value { overflow: hidden; color: $color-text; font-size: 24rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.reading-label { color: $color-text-secondary; font-size: 20rpx; }
.error-card { display: flex; flex-direction: column; align-items: flex-start; gap: 14rpx; }
.error-text { color: $color-red; font-size: 24rpx; }
.retry-button { align-self: flex-end; }
.loading-card { padding: 30rpx; }
.action-button { width: 100%; margin-top: 20rpx; }
</style>
