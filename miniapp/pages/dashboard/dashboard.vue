<template>
  <view class="page">
    <!-- 页面顶部：标题 + 实时时钟 + 演示标记 -->
    <PageHeader title="数据看板" :subtitle="clockText" />

    <!-- 核心大指标 2x2 -->
    <view class="metrics-grid">
      <view class="metrics-col"><MetricCard label="平均温度" :value="avgTemp" unit="℃" color="#1F6B4F" /></view>
      <view class="metrics-col"><MetricCard label="平均湿度" :value="avgHum" unit="%" color="#2563EB" /></view>
      <view class="metrics-col"><MetricCard label="在线设备" :value="onlineCount" unit="台" color="#1F6B4F" /></view>
      <view class="metrics-col"><MetricCard label="未确认报警" :value="unconfirmed" unit="条" color="#DC2626" /></view>
    </view>

    <!-- 设备状态分布 -->
    <view class="card">
      <text class="section-title">设备状态分布</text>
      <view class="dist-row">
        <view class="dist-item">
          <text class="dist-value" :style="{ color: '#1F6B4F' }">{{ onlineCount }}</text>
          <text class="dist-label">在线</text>
        </view>
        <view class="dist-item">
          <text class="dist-value" :style="{ color: '#DC2626' }">{{ offlineCount }}</text>
          <text class="dist-label">离线</text>
        </view>
        <view class="dist-item">
          <text class="dist-value" :style="{ color: '#2563EB' }">{{ store.devices.length }}</text>
          <text class="dist-label">总数</text>
        </view>
      </view>
    </view>

    <!-- 实时趋势 -->
    <view class="card">
      <text class="section-title">近七日平均温度趋势</text>
      <BarTrend :items="store.trend" color="#1F6B4F" />
    </view>
  </view>
</template>

<script setup>
// 数据看板页：现有大屏的手机布局（实时时钟 + 指标 + 状态分布 + 趋势）
import { ref, computed, onUnmounted } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import MetricCard from '../../components/MetricCard.vue'
import BarTrend from '../../components/BarTrend.vue'
import { useDemoStore } from '../../utils/demo-store.js'

const store = useDemoStore()

// 实时时钟文本（每秒刷新）
const clockText = ref('')

// 定时器句柄
let timer = null

// 刷新时钟文本
function updateClock() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  clockText.value = d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
}

// 启动时钟定时器
function startClock() {
  stopClock()
  updateClock()
  timer = setInterval(updateClock, 1000)
}

// 停止并清理时钟定时器
function stopClock() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// 组件卸载时清理定时器
onUnmounted(stopClock)

// 页面可见时更新并启动时钟。
onShow(startClock)

// 页面隐藏时暂停时钟，避免后台定时器。
onHide(stopClock)

// 页面退出时清理时钟。
onUnload(stopClock)

// 平均温度
const avgTemp = computed(() => {
  if (!store.dataPoints.length) return '--'
  const sum = store.dataPoints.reduce((acc, p) => acc + p.temp, 0)
  return (sum / store.dataPoints.length).toFixed(1)
})

// 平均湿度
const avgHum = computed(() => {
  if (!store.dataPoints.length) return '--'
  const sum = store.dataPoints.reduce((acc, p) => acc + p.hum, 0)
  return Math.round(sum / store.dataPoints.length)
})

// 在线设备数量
const onlineCount = computed(() => store.devices.filter((d) => d.status === 'online').length)

// 离线设备数量
const offlineCount = computed(() => store.devices.filter((d) => d.status === 'offline').length)

// 未确认报警数量
const unconfirmed = computed(() => store.alarms.filter((a) => a.status === 'unconfirmed').length)
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}
.metrics-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin: 0 ($space-page - 8rpx) ($space-page - 8rpx);
}
.metrics-col {
  width: 50%;
  padding: 8rpx;
}
.dist-row {
  display: flex;
  flex-direction: row;
  justify-content: space-around;
}
.dist-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.dist-value {
  font-size: 40rpx;
  font-weight: 700;
}
.dist-label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $color-text-secondary;
}
</style>
