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
    <view v-if="session.token && range === 'custom'" class="card custom-range-card">
      <view class="date-row">
        <text class="date-label">开始</text>
        <picker mode="date" :value="startDate" @change="selectStartDate">
          <view class="date-picker-value">{{ startDate }}</view>
        </picker>
        <picker mode="time" :value="startTime" @change="selectStartTime">
          <view class="date-picker-value">{{ startTime }}</view>
        </picker>
      </view>
      <view class="date-row">
        <text class="date-label">结束</text>
        <picker mode="date" :value="endDate" @change="selectEndDate">
          <view class="date-picker-value">{{ endDate }}</view>
        </picker>
        <picker mode="time" :value="endTime" @change="selectEndTime">
          <view class="date-picker-value">{{ endTime }}</view>
        </picker>
      </view>
      <text v-if="customRangeError" class="error-text">{{ customRangeError }}</text>
      <button class="btn-primary query-range-button" :disabled="history.loading" @tap="applyCustomRange">查询该时间范围</button>
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
      <view v-for="p in visibleList" :key="p.id" class="table-row">
        <view class="row-heading">
          <text class="row-device">{{ p.deviceId || '设备未知' }}</text>
          <text class="td-time">{{ p.time ? formatDateTime(p.time) : '--' }}</text>
        </view>
        <view class="reading-grid">
          <view class="reading-cell"><text class="cell-label">温度</text><text class="cell-value">{{ formatReading(p.temp) }}℃</text></view>
          <view class="reading-cell"><text class="cell-label">湿度</text><text class="cell-value">{{ formatReading(p.hum) }}%</text></view>
          <view class="reading-cell"><text class="cell-label">水位 ADC</text><text class="cell-value">{{ formatReading(p.water) }}</text></view>
          <view class="reading-cell"><text class="cell-label">信号 RSSI</text><text class="cell-value">{{ p.rssi === null ? '--' : formatReading(p.rssi) + ' dBm' }}</text></view>
        </view>
        <view class="row-details">
          <text class="detail-item">联动 {{ p.linkage === null ? '--' : p.linkage ? 'ON' : 'OFF' }}</text>
          <text class="detail-item">发送 {{ formatReading(p.sendCount) }}</text>
          <text class="detail-item">运行 {{ formatReading(p.uptimeMillis) }} ms</text>
          <text class="quality-tag" :class="p.qualityValid === false ? 'quality-invalid' : p.qualityValid === true ? 'quality-valid' : ''">{{ p.qualityValid === false ? '异常' : p.qualityValid === true ? '有效' : '未评估' }}</text>
        </view>
        <text v-if="p.qualityValid === false && p.qualityIssues" class="quality-issues">{{ p.qualityIssues }}</text>
      </view>
      <view v-if="hasMore" class="load-more" @tap="loadMore">
        <text class="load-more-text">{{ history.loading ? '正在加载…' : '加载更多记录' }}</text>
      </view>
      <text v-else class="hint table-end">共 {{ history.totalElements }} 条记录</text>
    </view>
    <EmptyState v-else-if="session.token && !history.error && !history.loading" text="当前设备和时间范围内没有历史记录" />

    <view v-if="session.token" class="export-note">
      <text>可选快捷时间范围或自定义日期时间；导出当前筛选范围内的完整记录，最多 50,000 条。</text>
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
import { buildTrend } from '../../utils/trend.js'

// 提供当前账号的设备筛选选项。
const deviceOptions = computed(() => [{ id: '', label: '全部设备' }, ...live.devices.map((d) => ({ id: d.id, label: d.name }))])
// 根据已选设备定位选择器下标。
const deviceIndex = computed(() => Math.max(0, deviceOptions.value.findIndex((d) => d.id === history.deviceId)))

// 每次进入页面时按当前筛选重新读取真实历史。
onShow(() => { if (session.token) { refreshLive(); refreshHistory() } })

// 从历史页直接进入账号登录。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }

// 使用最新截止时间重新读取当前筛选。
function refreshHistory() {
  if (range.value === 'custom') return applyCustomRange()
  return reloadHistory(history.deviceId, ranges.find((r) => r.key === range.value).hours)
}

// 切换真实设备时按当前快捷或自定义时间范围重新开始分页。
function selectDevice(event) {
  const deviceId = deviceOptions.value[Number(event.detail.value)]?.id || ''
  if (range.value === 'custom') return applyCustomRange(deviceId)
  return reloadHistory(deviceId, ranges.find((item) => item.key === range.value).hours)
}

// 时间范围选项
const ranges = [
  { key: 'day', label: '近24小时', hours: 24 },
  { key: 'week', label: '近7天', hours: 24 * 7 },
  { key: 'month', label: '近30天', hours: 24 * 30 },
  { key: 'custom', label: '自定义', hours: 0 }
]

// 当前时间范围
const range = ref('day')
const exporting = ref(false)
const startDate = ref(dateInput(Date.now() - 24 * 60 * 60 * 1000))
const endDate = ref(dateInput(Date.now()))
const startTime = ref(timeInput(Date.now() - 24 * 60 * 60 * 1000))
const endTime = ref(timeInput(Date.now()))
const customRangeError = ref('')

// 将本地时间转换为日期选择器需要的 YYYY-MM-DD 格式。
function dateInput(timestamp) {
  const date = new Date(timestamp)
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
}

// 将本地时间转换为时间选择器需要的 HH:mm 格式。
function timeInput(timestamp) {
  const date = new Date(timestamp)
  return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0')
}

// 将日期和时间字段解析为本地时间戳，避免 UTC 转换改变筛选边界。
function localTimestamp(dateValue, timeValue, endOfMinute = false) {
  const [year, month, day] = dateValue.split('-').map(Number)
  const [hour, minute] = timeValue.split(':').map(Number)
  return new Date(year, month - 1, day, hour, minute, endOfMinute ? 59 : 0, endOfMinute ? 999 : 0).getTime()
}

// 记录开始日期的本地选择。
function selectStartDate(event) { startDate.value = event.detail.value }

// 记录开始时间的本地选择。
function selectStartTime(event) { startTime.value = event.detail.value }

// 记录结束日期的本地选择。
function selectEndDate(event) { endDate.value = event.detail.value }

// 记录结束时间的本地选择。
function selectEndTime(event) { endTime.value = event.detail.value }

// 校验任意本地日期时间范围后冻结边界并请求第一页。
function applyCustomRange(deviceId = history.deviceId) {
  const start = localTimestamp(startDate.value, startTime.value)
  const end = localTimestamp(endDate.value, endTime.value, true)
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) {
    customRangeError.value = '开始时间不能晚于结束时间'
    return
  }
  customRangeError.value = ''
  return reloadHistory(deviceId, { start, end })
}

// 下载当前筛选范围内的完整CSV，并由微信打开文件分享面板。
async function exportCsv() {
  if (!session.token || !history.end || exporting.value) return
  exporting.value = true
  try {
    const end = history.end
    const start = history.start
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

// 根据当前已加载的真实记录生成温度趋势。
const temperatureTrend = computed(() => buildTrend(history.rows, 'temp', (time) => formatDateTime(time).slice(5, 16)))

// 根据当前已加载的真实记录生成湿度趋势。
const humidityTrend = computed(() => buildTrend(history.rows, 'hum', (time) => formatDateTime(time).slice(5, 16)))

// 是否还有更多数据可加载
const hasMore = computed(() => history.page + 1 < history.totalPages)

// 切换时间范围并重置分页
function setRange(key) {
  range.value = key
  if (key === 'custom') applyCustomRange()
  else refreshHistory()
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
.custom-range-card { margin: 0 $space-page 20rpx; }
.date-row { display: flex; align-items: center; gap: 10rpx; padding: 14rpx 0; }
.date-label { width: 72rpx; flex: none; color: $color-text-secondary; font-size: 23rpx; }
.date-picker-value { flex: 1; min-width: 0; padding: 12rpx 10rpx; border: 1rpx solid $color-border; border-radius: 10rpx; color: $color-text; font-size: 22rpx; text-align: center; }
.query-range-button { margin-top: 16rpx; }
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
.table-row {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $color-border;
}
.row-heading, .row-details { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; }
.row-device { color: $color-text; font-size: 24rpx; font-weight: 600; }
.td-time, .detail-item { color: $color-text-secondary; font-size: 20rpx; }
.reading-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8rpx; }
.reading-cell { display: flex; min-width: 0; flex-direction: column; gap: 6rpx; }
.cell-label { color: $color-text-secondary; font-size: 19rpx; }
.cell-value { overflow-wrap: anywhere; color: $color-text; font-size: 22rpx; font-weight: 600; }
.row-details { flex-wrap: wrap; justify-content: flex-start; }
.quality-tag { margin-left: auto; color: $color-text-secondary; font-size: 20rpx; }
.quality-valid { color: $color-primary; }
.quality-invalid, .quality-issues { color: $color-red; }
.quality-issues { font-size: 20rpx; }
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
