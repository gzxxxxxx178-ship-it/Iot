<template>
  <view class="page">
    <!-- 页面顶部：标题 + 设备总数 + 演示标记 -->
    <PageHeader title="设备" :subtitle="'共 ' + store.devices.length + ' 台设备'" />

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
          <view class="dc-metric"><text class="dc-metric-value">{{ device.temp }}℃</text><text class="dc-metric-label">温度</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ device.hum }}%</text><text class="dc-metric-label">湿度</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ device.water }}cm</text><text class="dc-metric-label">水位</text></view>
          <view class="dc-metric"><text class="dc-metric-value">{{ device.signal }}dBm</text><text class="dc-metric-label">信号</text></view>
        </view>
        <view class="dc-bottom">
          <text class="hint">最后在线：{{ formatDateTime(device.lastSeen) }}</text>
          <StatusTag v-if="device.controlled" text="运行中" kind="blue" />
        </view>
      </view>
    </view>
    <EmptyState v-else text="当前筛选下暂无设备" />

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
          <view class="ds-metric"><text class="ds-metric-value">{{ detailDevice.temp }}℃</text><text class="ds-metric-label">温度</text></view>
          <view class="ds-metric"><text class="ds-metric-value">{{ detailDevice.hum }}%</text><text class="ds-metric-label">湿度</text></view>
          <view class="ds-metric"><text class="ds-metric-value">{{ detailDevice.water }}cm</text><text class="ds-metric-label">水位</text></view>
        </view>
        <text class="hint">最后在线：{{ formatDateTime(detailDevice.lastSeen) }}</text>
        <view class="ds-actions">
          <button class="btn-primary ds-btn" :disabled="detailDevice.status !== 'online'" @tap="startDevice">启动</button>
          <button class="btn-danger ds-btn" :disabled="detailDevice.status !== 'online'" @tap="stopDevice">停止</button>
        </view>
        <button class="btn-secondary ds-close" @tap="closeDetail">关闭</button>
      </view>
    </view>
  </view>
</template>

<script setup>
// 设备页：状态筛选、设备列表、详情弹层与控制确认（仅修改演示状态）
import { ref, computed } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import { useDemoStore, formatDateTime, controlDevice } from '../../utils/demo-store.js'

const store = useDemoStore()

// 筛选条件列表
const filters = [
  { key: 'all', label: '全部' },
  { key: 'online', label: '在线' },
  { key: 'offline', label: '离线' }
]

// 当前筛选条件
const filter = ref('all')

// 详情弹层中选中的设备
const detailDevice = ref(null)

// 按筛选条件过滤设备列表
const filteredDevices = computed(() => {
  if (filter.value === 'all') return store.devices
  return store.devices.filter((d) => d.status === filter.value)
})

// 设置筛选条件
function setFilter(key) {
  filter.value = key
}

// 打开设备详情弹层
function openDetail(device) {
  detailDevice.value = device
}

// 关闭设备详情弹层
function closeDetail() {
  detailDevice.value = null
}

// 启动设备：确认后修改演示状态并反馈
function startDevice() {
  const device = detailDevice.value
  if (!device || device.status !== 'online') return
  uni.showModal({
    title: '演示控制',
    content: '确认向「' + device.name + '」发送启动命令？（仅修改演示状态）',
    success: (res) => {
      if (res.confirm) {
        if (!controlDevice(device.id, 'start')) return
        detailDevice.value = store.devices.find((d) => d.id === device.id) || null
        uni.showToast({ title: '已启动（演示）', icon: 'none' })
      }
    }
  })
}

// 停止设备：确认后修改演示状态并反馈
function stopDevice() {
  const device = detailDevice.value
  if (!device || device.status !== 'online') return
  uni.showModal({
    title: '演示控制',
    content: '确认向「' + device.name + '」发送停止命令？（仅修改演示状态）',
    success: (res) => {
      if (res.confirm) {
        if (!controlDevice(device.id, 'stop')) return
        detailDevice.value = store.devices.find((d) => d.id === device.id) || null
        uni.showToast({ title: '已停止（演示）', icon: 'none' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
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
