<template>
  <view class="page">
    <!-- 页面顶部：账号可访问的历史传感数据 -->
    <PageHeader title="数据" subtitle="按设备和时间范围查看历史读数" />
    <view class="card">
      <template v-if="!session.token">
        <text class="section-title">登录后查看历史数据</text>
        <text class="hint">使用现有系统账号登录，即可查看账号拥有或获准访问的设备记录。</text>
        <button class="btn-primary login-btn" @tap="goLogin">前往登录</button>
      </template>
      <template v-else>
        <picker :range="deviceOptions" range-key="label" :value="deviceIndex" @change="selectDevice">
          <view class="device-picker"><text class="picker-label">当前设备</text><text class="picker-value">{{ deviceOptions[deviceIndex]?.label || '全部设备' }}　⌄</text></view>
        </picker>
        <text v-if="history.loading && !visibleList.length" class="hint state-text">正在读取所选时间范围的数据…</text>
        <text v-if="history.error" class="error-text">{{ history.error }}</text>
        <view class="history-actions">
          <button class="btn-secondary" :disabled="history.loading" @tap="refreshHistory">刷新记录</button>
          <button v-if="history.error" class="btn-secondary" :disabled="history.loading" @tap="loadHistory">重试查询</button>
          <button class="btn-secondary" :disabled="history.loading || exporting" @tap="exportCsv">
            {{ exporting ? '正在准备 CSV…' : '导出完整 CSV' }}
          </button>
        </view>
      </template>
    </view>

    <!-- 时间范围筛选 -->
    <view v-if="session.token" class="filter-bar">
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

    <!-- 按已加载的历史记录分桶计算趋势，避免把当前页误报为完整时间范围。 -->
    <view v-if="session.token && visibleList.length" class="card trend-card">
      <text class="section-title">已加载记录趋势</text>
      <text class="hint">基于当前已加载的 {{ visibleList.length }} 条记录；加载更多后趋势会扩展。</text>
      <text class="trend-title">温度（℃）</text>
      <BarTrend :items="temperatureTrend" color="#1F6B4F" :max-bar-height="150" />
      <text class="trend-title">湿度（%）</text>
      <BarTrend :items="humidityTrend" color="#3484A8" :max-bar-height="150" />
    </view>

    <!-- 数据列表（分页/加载更多） -->
    <view v-if="session.token && visibleList.length" class="card table-card">
      <view class="table-head">
        <text class="th th-time">时间</text>
        <text class="th">温度</text>
        <text class="th">湿度</text>
        <text class="th">水位原始值</text>
      </view>
      <view v-for="p in visibleList" :key="p.id" class="table-row">
        <text class="td td-time">{{ p.time ? formatDateTime(p.time).slice(5, 16) : '--' }}</text>
        <text class="td">{{ formatReading(p.temp) }}℃</text>
        <text class="td">{{ formatReading(p.hum) }}%</text>
        <text class="td">{{ p.water ?? '--' }}</text>
      </view>
      <view v-if="hasMore" class="load-more" @tap="loadMore">
        <text class="load-more-text">{{ history.loading ? '正在加载…' : '加载更多记录' }}</text>
      </view>
      <text v-else class="hint table-end">共 {{ history.totalElements }} 条记录</text>
    </view>
    <EmptyState v-else-if="session.token && !history.error && !history.loading" text="当前设备和时间范围内没有历史记录" />

    <view v-if="session.token" class="export-note">
      <text>导出当前设备和时间范围内的完整记录，最多 50,000 条；微信会打开文件分享面板。</text>
    </view>
  </view>
</template>

<script setup>
// 数据页：设备与时间范围筛选、分页浏览真实历史记录。
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { session } from '../../utils/session.js'
import { live, refreshLive, formatReading, downloadHistoryCsv } from '../../utils/live-store.js'
import { history, reloadHistory, loadHistory } from '../../utils/history-store.js'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import BarTrend from '../../components/BarTrend.vue'
import { formatDateTime } from '../../utils/format.js'

// 提供当前账号的设备筛选选项。
const deviceOptions = computed(() => [{ id: '', label: '全部设备' }, ...live.devices.map((d) => ({ id: d.id, label: d.name }))])
// 根据已选设备定位选择器下标。
const deviceIndex = computed(() => Math.max(0, deviceOptions.value.findIndex((d) => d.id === history.deviceId)))

// 每次进入页面时按当前筛选重新读取真实历史。
onShow(() => { if (session.token) { refreshLive(); refreshHistory() } })

// 从历史页直接进入账号登录。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }

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
const exporting = ref(false)

// 下载当前筛选范围内的完整CSV，并由微信打开文件分享面板。
async function exportCsv() {
  if (!session.token || !history.end || exporting.value) return
  exporting.value = true
  try {
    const end = history.end
    const start = end - history.hours * 3600000
    const { tempFilePath } = await downloadHistoryCsv({ deviceId: history.deviceId, start, end })
    const date = formatDateTime(end).slice(0, 10)
    await shareCsvFile(tempFilePath, `sensor-history-${date}.csv`)
    uni.showToast({ title: 'CSV 分享面板已打开', icon: 'success' })
  } catch (error) {
    const message = error?.errMsg || error?.message || 'CSV 导出失败'
    uni.showToast({ title: /cancel/i.test(message) ? '已取消分享' : message.slice(0, 20), icon: 'none' })
  } finally {
    exporting.value = false
  }
}

// 调用微信原生文件分享接口，开发工具不支持时提示改用真机调试。
function shareCsvFile(filePath, fileName) {
  if (typeof wx === 'undefined' || typeof wx.shareFileMessage !== 'function') {
    return Promise.reject(new Error('当前开发工具不支持分享文件，请在真机微信中测试'))
  }
  return new Promise((resolve, reject) => {
    wx.shareFileMessage({ filePath, fileName, success: resolve, fail: reject })
  })
}

// 当前页可见数据点
const visibleList = computed(() => history.rows)

// 将已加载记录按时间顺序分为最多8组，分别计算温度或湿度均值。
function buildTrend(field) {
  const points = history.rows
    .filter((row) => row[field] !== null && row[field] !== undefined && row[field] !== ''
      && Number.isFinite(Number(row[field])) && row.time)
    .slice()
    .reverse()
  if (!points.length) return []
  const groupCount = Math.min(8, points.length)
  return Array.from({ length: groupCount }, (_, index) => {
    const start = Math.floor(index * points.length / groupCount)
    const end = Math.floor((index + 1) * points.length / groupCount)
    const group = points.slice(start, end)
    const average = group.reduce((sum, row) => sum + Number(row[field]), 0) / group.length
    const middleTime = group[Math.floor(group.length / 2)].time
    return { label: formatDateTime(middleTime).slice(5, 16), value: Math.round(average * 10) / 10 }
  })
}

// 根据当前已加载的真实记录生成温度趋势。
const temperatureTrend = computed(() => buildTrend('temp'))

// 根据当前已加载的真实记录生成湿度趋势。
const humidityTrend = computed(() => buildTrend('hum'))

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
</script>

<style lang="scss" scoped>
.device-picker { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 12rpx 0 20rpx; font-size: 28rpx; overflow-wrap: anywhere; }
.picker-label { color: $color-text-secondary; }
.picker-value { color: $color-text; font-weight: 600; text-align: right; }
.section-title { display: block; margin-bottom: 12rpx; }
.login-btn { margin-top: 24rpx; }
.state-text { display: block; margin-top: 8rpx; }
.error-text { display: block; margin-top: 8rpx; color: $color-red; font-size: 24rpx; overflow-wrap: anywhere; }
.history-actions { display: flex; flex-direction: column; gap: 12rpx; margin-top: 16rpx; }
.history-actions button { margin: 0; }
.trend-card { overflow: hidden; }
.trend-card .hint { display: block; margin-top: -4rpx; }
.trend-title { display: block; margin: 24rpx 0 4rpx; color: $color-text-secondary; font-size: 24rpx; }
.export-note { margin: 0 $space-page $space-page; padding: 4rpx 8rpx; color: $color-text-light; font-size: 22rpx; text-align: center; }
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
</style>
