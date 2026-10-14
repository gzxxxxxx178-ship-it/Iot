<template>
  <view class="page">
    <PageHeader title="概览" subtitle="设备监测" />
    <LiveStatus />
    <view class="metrics-grid">
      <MetricCard label="平均温度" :value="formatReading(live.stats?.avgTemp)" unit="℃" />
      <MetricCard label="平均湿度" :value="formatReading(live.stats?.avgHum)" unit="%" color="#2563EB" />
      <MetricCard label="在线设备" :value="live.stats?.onlineCount ?? '--'" unit="台" />
      <MetricCard label="设备总数" :value="live.stats?.deviceCount ?? '--'" unit="台" />
    </view>
    <view class="card">
      <text class="hint">温湿度为后端在线设备最新读数。无读数时显示 --。</text>
      <button class="btn-secondary entry" @tap="goDevices">查看设备</button>
      <button class="btn-secondary entry" @tap="goData">查询历史</button>
    </view>
  </view>
</template>
<script setup>
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import MetricCard from '../../components/MetricCard.vue'
import LiveStatus from '../../components/LiveStatus.vue'
import { live, refreshLive, formatReading } from '../../utils/live-store.js'

// 页面可见时从真实后端刷新统计。
onShow(() => { refreshLive() })

// 打开真实设备列表。
function goDevices() { uni.switchTab({ url: '/pages/devices/devices' }) }

// 打开真实历史查询。
function goData() { uni.switchTab({ url: '/pages/data/data' }) }
</script>
<style lang="scss" scoped>
.metrics-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; margin: 0 $space-page $space-page; }
.entry { margin-top: 20rpx; }
</style>
