<template>
  <view class="page">
    <!-- 页面顶部：真实历史数据 -->
    <PageHeader title="数据" subtitle="历史传感数据" />
    <view class="card">
      <text v-if="!session.token" class="hint">请先到“我的”登录账号</text>
      <template v-else>
        <picker :range="deviceOptions" range-key="label" :value="deviceIndex" @change="selectDevice">
          <view class="device-picker">设备：{{ deviceOptions[deviceIndex]?.label || '全部设备' }} ▾</view>
        </picker>
        <text v-if="history.loading" class="hint">正在加载…</text>
        <text v-if="history.error" class="hint">{{ history.error }}</text>
        <button class="btn-secondary" :disabled="history.loading" @tap="refreshHistory">刷新历史</button>
        <button v-if="history.error" class="btn-secondary" :disabled="history.loading" @tap="loadHistory">重试本页</button>
      </template>
    </view>

    <!-- 时间范围筛选 -->
    <view class="filter-bar">
      <view
        v-for="r in ranges"
        :key="r.key"
        class="filter-item"
        :class="{ active: range === r.key }"
        @tap="setRange(r.key)"
      >
        <text class="filter-text">{{ r.label }}</text>
      </view>
    </view>

    <!-- 数据列表（分页/加载更多） -->
    <view v-if="visibleList.length" class="card table-card">
      <view class="table-head">
        <text class="th th-time">时间</text>
        <text class="th">温度</text>
        <text class="th">湿度</text>
        <text class="th">水位 ADC</text>
      </view>
      <view v-for="p in visibleList" :key="p.id" class="table-row">
        <text class="td td-time">{{ p.time ? formatDateTime(p.time).slice(5, 16) : '--' }}</text>
        <text class="td">{{ formatReading(p.temp) }}℃</text>
        <text class="td">{{ formatReading(p.hum) }}%</text>
        <text class="td">{{ p.water ?? '--' }}</text>
      </view>
      <view v-if="hasMore" class="load-more" @tap="loadMore">
        <text class="load-more-text">加载更多</text>
      </view>
      <text v-else class="hint table-end">共 {{ history.totalElements }} 条</text>
    </view>
    <EmptyState v-else text="该时间范围内暂无数据" />

    <!-- 导出（明确未接入） -->
    <view class="card">
      <text class="section-title">数据导出</text>
      <text class="hint">CSV 导出尚未接入后端，点击仅作功能占位提示。</text>
      <button class="btn-secondary export-btn" @tap="onExport">导出 CSV（未接入）</button>
    </view>
  </view>
</template>

<script setup>
// 数据页：时间范围筛选、分页加载更多、导出占位（明确未接入）
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { session } from '../../utils/session.js'
import { live, refreshLive, formatReading } from '../../utils/live-store.js'
import { history, reloadHistory, loadHistory } from '../../utils/history-store.js'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import { formatDateTime } from '../../utils/format.js'

// 提供当前账号的设备筛选选项。
const deviceOptions = computed(() => [{ id: '', label: '全部设备' }, ...live.devices.map((d) => ({ id: d.id, label: d.name }))])
// 根据已选设备定位选择器下标。
const deviceIndex = computed(() => Math.max(0, deviceOptions.value.findIndex((d) => d.id === history.deviceId)))

// 每次进入页面时按当前筛选重新读取真实历史。
onShow(() => { if (session.token) { refreshLive(); refreshHistory() } })

// 使用最新截止时间重新读取当前筛选。
function refreshHistory() { return reloadHistory(history.deviceId, ranges.find((r) => r.key === range.value).hours) }

// 切换真实设备时重新开始分页。
function selectDevice(event) { reloadHistory(deviceOptions.value[Number(event.detail.value)]?.id || '', history.hours) }

// 时间范围选项
const ranges = [
  { key: 'day', label: '近24小时', hours: 24 },
  { key: 'week', label: '近7天', hours: 24 * 7 },
  { key: 'month', label: '近30天', hours: 24 * 30 }
]

// 当前时间范围
const range = ref('day')

// 当前页可见数据点
const visibleList = computed(() => history.rows)

// 是否还有更多数据可加载
const hasMore = computed(() => history.page + 1 < history.totalPages)

// 切换时间范围并重置分页
function setRange(key) {
  range.value = key
  refreshHistory()
}

// 加载更多：增加可见条数
function loadMore() {
  loadHistory()
}

// 导出占位：提示功能未接入
function onExport() {
  uni.showToast({ title: '导出未接入后端', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.device-picker { padding: 20rpx 0; font-size: 28rpx; overflow-wrap: anywhere; }
.page {
  padding-bottom: 40rpx;
}
.filter-bar {
  display: flex;
  flex-direction: row;
  margin: 0 $space-page 20rpx;
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-pill;
  padding: 6rpx;
}
.filter-item {
  flex: 1;
  text-align: center;
  padding: 12rpx 0;
  border-radius: $radius-pill;
}
.filter-item.active {
  background-color: $color-primary;
}
.filter-text {
  font-size: 26rpx;
  color: $color-text-secondary;
}
.filter-item.active .filter-text {
  color: #FFFFFF;
}
.table-card {
  padding: 0 24rpx 24rpx;
}
.table-head,
.table-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $color-border;
}
.table-head {
  border-bottom: 2rpx solid $color-border;
}
.th,
.td {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  color: $color-text-secondary;
}
.td {
  color: $color-text;
}
.th-time,
.td-time {
  flex: 2;
  text-align: left;
}
.load-more {
  margin-top: 20rpx;
  text-align: center;
  padding: 16rpx 0;
  background-color: $color-primary-soft;
  border-radius: $radius-card;
}
.load-more-text {
  font-size: 26rpx;
  color: $color-primary;
}
.table-end {
  display: block;
  text-align: center;
  margin-top: 20rpx;
}
.export-btn {
  margin-top: 20rpx;
}
</style>
