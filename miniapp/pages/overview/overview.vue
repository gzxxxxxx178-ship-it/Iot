<template>
  <view class="page">
    <PageHeader title="概览" subtitle="设备监测" />
    <LiveStatus />
    <view class="metrics-grid">
      <MetricCard label="平均温度" :value="formatReading(live.stats?.avgTemp)" unit="℃" />
      <MetricCard label="平均湿度" :value="formatReading(live.stats?.avgHum)" unit="%" color="#2563EB" />
      <MetricCard label="近20条平均水位" :value="averageWater" unit="ADC" color="#D97706" />
      <MetricCard label="最新信号强度" :value="latestSignal" unit="dBm" color="#7C3AED" />
      <MetricCard label="在线设备" :value="live.stats?.onlineCount ?? '--'" unit="台" />
      <MetricCard label="设备总数" :value="live.stats?.deviceCount ?? '--'" unit="台" />
      <MetricCard label="近20条发送次数合计" :value="recentMessageCount" unit="次" color="#2563EB" />
      <MetricCard label="最新联动状态" :value="latestLinkage" />
    </view>

    <view v-if="session.token && recent.rows.length" class="card trend-card">
      <view class="trend-header">
        <text class="section-title">最近传感器趋势</text>
        <text class="hint">按服务端最近20条记录分组</text>
      </view>
      <text class="trend-title">温度（℃）</text>
      <BarTrend :items="temperatureTrend" color="#1F6B4F" :max-bar-height="140" />
      <text class="trend-title">湿度（%）</text>
      <BarTrend :items="humidityTrend" color="#3484A8" :max-bar-height="140" />
    </view>
    <view v-else-if="session.token && recent.error" class="card">
      <text class="error-text">{{ recent.error }}</text>
      <button class="btn-secondary entry" :disabled="recent.loading" @tap="loadOverview">重试刷新</button>
    </view>
    <EmptyState v-else-if="session.token && !recent.loading" text="暂无可绘制的传感器历史趋势" />

    <view class="card">
      <text class="hint">温湿度均值由后端在线设备最新读数计算；水位、信号和联动取最近传感器记录。无读数时显示 --。</text>
      <button class="btn-secondary entry" @tap="goDevices">查看设备</button>
      <button class="btn-secondary entry" @tap="goMonitor">实时监控</button>
      <button class="btn-secondary entry" @tap="goData">查询历史</button>
    </view>
  </view>
</template>
<script setup>
import { computed, reactive, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { session } from '../../utils/session.js'
import PageHeader from '../../components/PageHeader.vue'
import MetricCard from '../../components/MetricCard.vue'
import LiveStatus from '../../components/LiveStatus.vue'
import BarTrend from '../../components/BarTrend.vue'
import EmptyState from '../../components/EmptyState.vue'
import { live, refreshLive, formatReading, queryRecentHistory } from '../../utils/live-store.js'
import { buildTrend } from '../../utils/trend.js'
import { formatDateTime } from '../../utils/format.js'

const recent = reactive({ rows: [], loading: false, error: '' })

// 处理冷启动登录恢复晚于首页显示的情况，并在退出时清除首页数据。
watch(() => session.token, (token) => {
  if (token) loadOverview()
  else { recent.rows = []; recent.loading = false; recent.error = '' }
})

// 页面可见时刷新真实统计和最近历史，忽略账号切换前的迟到结果。
async function loadOverview() {
  if (!session.token) { recent.rows = []; recent.loading = false; recent.error = ''; return }
  // 页面显示和会话恢复可能同时触发，合并同一轮首页请求。
  if (recent.loading) return
  const generation = session.generation
  recent.loading = true; recent.error = ''
  try {
    const [, rows] = await Promise.all([refreshLive(), queryRecentHistory()])
    if (generation !== session.generation) return
    recent.rows = rows
  } catch (error) {
    if (generation === session.generation && !error.stale) recent.error = error.message || '历史数据读取失败'
  } finally { if (generation === session.generation) recent.loading = false }
}

// 页面可见时从真实后端刷新统计。
onShow(loadOverview)

// 计算最近记录中有效水位读数的平均值，保留零值。
const averageWater = computed(() => averageReading('water'))

// 按字段计算最近历史记录的有效读数均值。
function averageReading(field) {
  const values = recent.rows.map((row) => row[field]).filter((value) => typeof value === 'number' && Number.isFinite(value))
  return values.length ? formatReading(values.reduce((sum, value) => sum + value, 0) / values.length) : '--'
}

// 显示最新传感器的信号强度。
const latestSignal = computed(() => formatReading(recent.rows[0]?.rssi))

// 统计最近20条记录中的发送次数合计。
const recentMessageCount = computed(() => {
  const values = recent.rows.map((row) => row.sendCount).filter((value) => typeof value === 'number' && Number.isFinite(value))
  return values.length ? String(values.reduce((sum, value) => sum + value, 0)) : '--'
})

// 显示最新传感器记录的联动状态。
const latestLinkage = computed(() => recent.rows[0]?.linkage === true ? 'ON' : recent.rows[0]?.linkage === false ? 'OFF' : '--')

// 将最近的20条温度记录汇总成最多8个趋势点。
const temperatureTrend = computed(() => buildTrend(recent.rows, 'temp', (time) => formatDateTime(time).slice(5, 16)))

// 将最近的20条湿度记录汇总成最多8个趋势点。
const humidityTrend = computed(() => buildTrend(recent.rows, 'hum', (time) => formatDateTime(time).slice(5, 16)))

// 打开真实设备列表。
function goDevices() { uni.switchTab({ url: '/pages/devices/devices' }) }

// 打开真实WebSocket实时监控页。
function goMonitor() { uni.navigateTo({ url: '/pages/monitor/monitor' }) }

// 打开真实历史查询。
function goData() { uni.switchTab({ url: '/pages/data/data' }) }
</script>
<style lang="scss" scoped>
.metrics-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; margin: 0 $space-page $space-page; }
.entry { margin-top: 20rpx; }
</style>
