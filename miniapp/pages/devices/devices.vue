<template>
  <view class="page">
    <!-- 页面顶部：标题与当前账号设备总数 -->
    <PageHeader title="设备" :subtitle="'共 ' + devices.length + ' 台设备'" />
    <LiveStatus />

    <!-- 状态筛选 -->
    <view class="filter-bar">
      <view
        v-for="f in filters"
        :key="f.key"
        class="filter-item"
        :class="{ active: filter === f.key }"
        @tap="setFilter(f.key)"
      >
        <text class="filter-text">{{ f.label }}</text>
      </view>
    </view>

    <!-- 设备列表 -->
    <view v-if="filteredDevices.length" class="list">
      <view v-for="device in filteredDevices" :key="device.id" class="device-card" @tap="openDetail(device)">
        <view class="dc-top">
          <view class="dc-name-wrap">
            <text class="dc-name">{{ device.name }}</text>
            <text class="dc-type">{{ device.type }}</text>
          </view>
          <StatusTag :text="device.status === 'online' ? '在线' : '离线'" :kind="device.status === 'online' ? 'green' : 'red'" />
        </view>
        <view class="dc-metrics">
          <view class="dc-metric"><text class="dc-metric-value">{{ formatReading(device.temp) }}℃</text><text class="dc-metric-label">温度</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ formatReading(device.hum) }}%</text><text class="dc-metric-label">湿度</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ device.water ?? '--' }}</text><text class="dc-metric-label">水位 ADC</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ device.signal ?? '--' }}dBm</text><text class="dc-metric-label">信号</text></view>
        </view>
        <view class="dc-bottom">
          <text class="hint">最后上报：{{ showTime(device.lastSeen) }}</text>
          <StatusTag v-if="device.controlled" text="运行中" kind="blue" />
        </view>
      </view>
    </view>
    <EmptyState v-else text="当前筛选下暂无设备" />
    <view v-if="live.commands.length" class="card">
      <text class="section-title">控制回执</text>
      <view v-for="receipt in live.commands.slice(0, 10)" :key="receipt.commandId" class="receipt">
        <text>{{ receipt.deviceId }} · {{ receipt.command === 'start' ? '启动' : receipt.command === 'stop' ? '停止' : receipt.command }}</text>
        <text>{{ commandStatus(receipt.status) }}</text>
        <text class="hint">{{ receipt.message }}</text>
      </view>
    </view>

    <!-- 设备详情弹层（含控制确认） -->
    <view v-if="detailDevice" class="mask" @tap="closeDetail">
      <view class="detail-sheet" @tap.stop>
        <view class="ds-header">
          <view>
            <text class="ds-name">{{ detailDevice.name }}</text>
            <text class="ds-type">{{ detailDevice.type }}</text>
          </view>
          <StatusTag :text="detailDevice.status === 'online' ? '在线' : '离线'" :kind="detailDevice.status === 'online' ? 'green' : 'red'" />
        </view>
        <view class="ds-metrics">
          <view class="ds-metric"><text class="ds-metric-value">{{ formatReading(detailDevice.temp) }}℃</text><text class="ds-metric-label">温度</text></view>
          <view class="ds-metric"><text class="ds-metric-value">{{ formatReading(detailDevice.hum) }}%</text><text class="ds-metric-label">湿度</text></view>
          <view class="ds-metric"><text class="ds-metric-value">{{ detailDevice.water ?? '--' }}</text><text class="ds-metric-label">水位 ADC</text></view>
        </view>
        <text class="hint">最后上报：{{ showTime(detailDevice.lastSeen) }}</text>
        <view class="ds-actions">
          <button class="btn-primary ds-btn" :disabled="!canControl" @tap="startDevice">启动</button>
          <button class="btn-danger ds-btn" :disabled="!canControl" @tap="stopDevice">停止</button>
        </view>
        <button class="btn-secondary ds-close" @tap="closeDetail">关闭</button>
      </view>
    </view>
  </view>
</template>

<script setup>
// 设备页：按模式展示设备、人工控制和命令回执。
import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { session } from '../../utils/session.js'
import { live, refreshLive, issueCommand, commandStatus, parseServerTime, formatReading } from '../../utils/live-store.js'
import LiveStatus from '../../components/LiveStatus.vue'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import { formatDateTime } from '../../utils/format.js'

// 仅展示真实账号所属设备。
const devices = computed(() => live.devices)
const sending = ref(false)
// 页面可见时刷新设备和控制回执。
onShow(() => { refreshLive() })

// 缺失上报时间显示占位文本。
function showTime(value) { return value ? formatDateTime(parseServerTime(value)) : '--' }

// 筛选条件列表
const filters = [
  { key: 'all', label: '全部' },
  { key: 'online', label: '在线' },
  { key: 'offline', label: '离线' }
]

// 当前筛选条件
const filter = ref('all')

// 详情弹层中选中的设备
const detailId = ref(null)
// 从当前数据源定位详情，避免刷新后保留旧设备对象。
const detailDevice = computed(() => devices.value.find((item) => item.id === detailId.value) || null)
// 会话切换时关闭详情，旧请求不能解除新会话的发送锁。
watch(() => session.generation, () => { detailId.value = null; sending.value = false }, { flush: 'sync' })
// 离线、禁用、待回执与发送中的设备均不可再次控制。
const canControl = computed(() => {
  const d = detailDevice.value
  return d && d.status === 'online' && !sending.value && session.token && d.enabled !== false && d.lifecycleStatus !== 'ARCHIVED' && !live.commands.some((c) => c.deviceId === d.id && ['PENDING', 'DISPATCHED'].includes(c.status))
})

// 按筛选条件过滤设备列表
const filteredDevices = computed(() => {
  if (filter.value === 'all') return devices.value
  return devices.value.filter((d) => d.status === filter.value)
})

// 设置筛选条件
function setFilter(key) {
  filter.value = key
}

// 打开设备详情弹层
function openDetail(device) {
  detailId.value = device.id
}

// 关闭设备详情弹层
function closeDetail() {
  detailId.value = null
}

// 打开启动命令确认。
function startDevice() {
  confirmControl('start')
}

// 打开停止命令确认。
function stopDevice() {
  confirmControl('stop')
}

// 二次确认后发送命令，保留设备回执的真实执行语义。
function confirmControl(command) {
  const device = detailDevice.value
  if (!device || !canControl.value) return
  const generation = session.generation
  sending.value = true
  uni.showModal({
    title: '设备控制',
    content: '确认向「' + device.name + '」发送' + (command === 'start' ? '启动' : '停止') + '命令？将操作真实设备。',
    // 确认后检查会话是否仍与打开弹窗时一致。
    success: async (res) => {
      try {
        if (!res.confirm || generation !== session.generation) return
        const receipt = await issueCommand(device.id, command)
        uni.showToast({ title: commandStatus(receipt.status), icon: 'none' })
      } catch (err) { if (!err.stale) uni.showToast({ title: err.message, icon: 'none' }) }
      finally { if (generation === session.generation) sending.value = false }
    },
    // 弹窗打开失败时释放操作锁。
    fail: () => { if (generation === session.generation) sending.value = false }
  })
}
</script>

<style lang="scss" scoped>
.receipt { display: flex; flex-direction: column; gap: 8rpx; padding: 20rpx 0; border-bottom: 1rpx solid $color-border; font-size: 26rpx; overflow-wrap: anywhere; }
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
.list {
  padding-bottom: 20rpx;
}
.device-card {
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 24rpx;
  margin: 0 $space-page 20rpx;
}
.dc-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.dc-name-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  min-width: 0;
}
.dc-name {
  font-size: 30rpx;
  font-weight: 600;
  color: $color-text;
}
.dc-type {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: $color-text-secondary;
}
.dc-metrics {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 20rpx;
}
.dc-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.dc-metric-value {
  font-size: 28rpx;
  font-weight: 600;
  color: $color-text;
}
.dc-metric-label {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $color-text-secondary;
}
.dc-bottom {
  margin-top: 20rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.detail-sheet {
  width: 100%;
  background-color: $color-bg;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
  max-height: 85vh;
  overflow-y: auto;
}
.ds-header {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.ds-name {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: $color-text;
}
.ds-type {
  font-size: 24rpx;
  color: $color-text-secondary;
}
.ds-metrics {
  display: flex;
  flex-direction: row;
  justify-content: space-around;
  margin: 32rpx 0;
}
.ds-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ds-metric-value {
  font-size: 32rpx;
  font-weight: 700;
  color: $color-text;
}
.ds-metric-label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $color-text-secondary;
}
.ds-actions {
  display: flex;
  flex-direction: row;
  margin-top: 32rpx;
}
.ds-btn {
  flex: 1;
  margin: 0 8rpx;
}
.ds-close {
  margin-top: 16rpx;
}
</style>
