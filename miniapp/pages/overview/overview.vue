<template>
  <view class="page">
    <!-- 页面顶部：标题 + 日期 + 演示标记 -->
    <PageHeader title="概览" :subtitle="dateText" />

    <!-- 演示数据说明条 -->
    <view class="demo-note">以下指标与趋势均为演示数据，未接入真实后端。</view>

    <!-- 核心指标 2x2 网格 -->
    <view class="metrics-grid">
      <view class="metrics-col">
        <MetricCard label="平均温度" :value="avgTemp" unit="℃" color="#1F6B4F" />
      </view>
      <view class="metrics-col">
        <MetricCard label="平均湿度" :value="avgHum" unit="%" color="#2563EB" />
      </view>
      <view class="metrics-col">
        <MetricCard label="平均水位" :value="avgWater" unit="cm" color="#D97706" />
      </view>
      <view class="metrics-col">
        <MetricCard label="在线设备" :value="onlineCount" unit="台" color="#1F6B4F" />
      </view>
    </view>

    <!-- 近七日平均温度短趋势（原生柱状图） -->
    <view class="card">
      <text class="section-title">近七日平均温度</text>
      <BarTrend :items="store.trend" color="#1F6B4F" />
    </view>

    <!-- 状态汇总 -->
    <view class="card">
      <text class="section-title">系统状态</text>
      <view class="summary-row">
        <view class="summary-item" @tap="goDevices">
          <text class="summary-value">{{ onlineCount }}</text>
          <text class="summary-label">设备在线</text>
        </view>
        <view class="summary-item" @tap="goAlarm">
          <text class="summary-value" :style="{ color: unconfirmedAlarms > 0 ? '#DC2626' : '#1F2937' }">{{ unconfirmedAlarms }}</text>
          <text class="summary-label">未确认报警</text>
        </view>
        <view class="summary-item" @tap="goAutomation">
          <text class="summary-value">{{ ruleCount }}</text>
          <text class="summary-label">自动化规则</text>
        </view>
      </view>
    </view>

    <!-- 业务入口 -->
    <view class="card">
      <text class="section-title">快捷入口</text>
      <view class="entry-row">
        <view class="entry" @tap="goDevices"><text class="entry-text">设备管理</text></view>
        <view class="entry" @tap="goData"><text class="entry-text">历史数据</text></view>
        <view class="entry" @tap="goAlarm"><text class="entry-text">报警管理</text></view>
        <view class="entry" @tap="goChat"><text class="entry-text">AI 助手</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
// 概览页：展示核心指标、短趋势、状态汇总与业务入口
import { computed } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import MetricCard from '../../components/MetricCard.vue'
import BarTrend from '../../components/BarTrend.vue'
import { useDemoStore, formatDateTime } from '../../utils/demo-store.js'

const store = useDemoStore()

// 顶部日期文本（仅日期部分）
const dateText = formatDateTime(Date.now()).slice(0, 10)

// 计算演示数据点的平均温度
const avgTemp = computed(() => {
  if (!store.dataPoints.length) return '--'
  const sum = store.dataPoints.reduce((acc, p) => acc + p.temp, 0)
  return (sum / store.dataPoints.length).toFixed(1)
})

// 计算演示数据点的平均湿度
const avgHum = computed(() => {
  if (!store.dataPoints.length) return '--'
  const sum = store.dataPoints.reduce((acc, p) => acc + p.hum, 0)
  return Math.round(sum / store.dataPoints.length)
})

// 计算演示数据点的平均水位
const avgWater = computed(() => {
  if (!store.dataPoints.length) return '--'
  const sum = store.dataPoints.reduce((acc, p) => acc + p.water, 0)
  return Math.round(sum / store.dataPoints.length)
})

// 计算在线设备数量
const onlineCount = computed(() => store.devices.filter((d) => d.status === 'online').length)

// 计算未确认报警数量
const unconfirmedAlarms = computed(() => store.alarms.filter((a) => a.status === 'unconfirmed').length)

// 计算自动化规则数量
const ruleCount = computed(() => store.rules.length)

// 跳转到设备 tab 页
function goDevices() {
  uni.switchTab({ url: '/pages/devices/devices' })
}

// 跳转到数据 tab 页
function goData() {
  uni.switchTab({ url: '/pages/data/data' })
}

// 跳转到报警管理页
function goAlarm() {
  uni.navigateTo({ url: '/pages/alarm/alarm' })
}

// 跳转到自动化规则页
function goAutomation() {
  uni.navigateTo({ url: '/pages/automation/automation' })
}

// 跳转到 AI 助手页
function goChat() {
  uni.navigateTo({ url: '/pages/chat/chat' })
}
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
.summary-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}
.summary-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12rpx 0;
}
.summary-value {
  font-size: 36rpx;
  font-weight: 700;
  color: $color-text;
}
.summary-label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $color-text-secondary;
}
.entry-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
}
.entry {
  width: 50%;
  padding: 8rpx;
}
.entry-text {
  display: block;
  text-align: center;
  padding: 24rpx 0;
  background-color: $color-primary-soft;
  color: $color-primary;
  border-radius: $radius-card;
  font-size: 26rpx;
}
</style>
