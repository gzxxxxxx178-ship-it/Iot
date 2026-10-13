<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="报警管理" :subtitle="'未确认 ' + unconfirmedCount + ' 条'" />

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

    <!-- 报警列表 -->
    <view v-if="filteredAlarms.length" class="list">
      <view v-for="alarm in filteredAlarms" :key="alarm.id" class="alarm-card">
        <view class="alarm-top">
          <view class="alarm-title-wrap">
            <text class="alarm-device">{{ alarm.deviceName }}</text>
            <StatusTag :text="levelText(alarm.level)" :kind="levelKind(alarm.level)" />
          </view>
          <StatusTag :text="alarm.status === 'confirmed' ? '已确认' : '未确认'" :kind="alarm.status === 'confirmed' ? 'gray' : 'red'" />
        </view>
        <text class="alarm-message">{{ alarm.message }}</text>
        <view class="alarm-bottom">
          <text class="hint">{{ formatDateTime(alarm.time) }}</text>
          <view v-if="alarm.status === 'unconfirmed'" class="confirm-btn" @tap="onConfirm(alarm)">
            <text class="confirm-btn-text">确认</text>
          </view>
        </view>
      </view>
    </view>
    <EmptyState v-else text="当前筛选下暂无报警" />
  </view>
</template>

<script setup>
// 报警管理页：状态筛选与报警确认（仅修改演示状态）
import { ref, computed } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import { useDemoStore, formatDateTime, confirmAlarm } from '../../utils/demo-store.js'

const store = useDemoStore()

// 状态筛选选项
const filters = [
  { key: 'all', label: '全部' },
  { key: 'unconfirmed', label: '未确认' },
  { key: 'confirmed', label: '已确认' }
]

// 当前筛选条件
const filter = ref('all')

// 按筛选条件过滤报警列表
const filteredAlarms = computed(() => {
  if (filter.value === 'all') return store.alarms
  return store.alarms.filter((a) => a.status === filter.value)
})

// 未确认报警数量
const unconfirmedCount = computed(() => store.alarms.filter((a) => a.status === 'unconfirmed').length)

// 报警级别文本映射
function levelText(level) {
  const map = { high: '严重', medium: '中等', low: '轻微' }
  return map[level] || '一般'
}

// 报警级别颜色映射
function levelKind(level) {
  const map = { high: 'red', medium: 'amber', low: 'blue' }
  return map[level] || 'gray'
}

// 设置筛选条件
function setFilter(key) {
  filter.value = key
}

// 确认报警：二次确认后修改演示状态
function onConfirm(alarm) {
  uni.showModal({
    title: '确认报警',
    content: '确认将「' + alarm.message + '」标记为已确认？',
    success: (res) => {
      if (res.confirm) {
        confirmAlarm(alarm.id)
        uni.showToast({ title: '已确认', icon: 'none' })
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
.alarm-card {
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 24rpx;
  margin: 0 $space-page 20rpx;
}
.alarm-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.alarm-title-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-width: 0;
}
.alarm-device {
  font-size: 28rpx;
  font-weight: 600;
  color: $color-text;
  margin-right: 12rpx;
}
.alarm-message {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: $color-text-secondary;
  line-height: 1.5;
}
.alarm-bottom {
  margin-top: 20rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.confirm-btn {
  padding: 8rpx 28rpx;
  background-color: $color-primary;
  border-radius: $radius-pill;
}
.confirm-btn-text {
  font-size: 24rpx;
  color: #FFFFFF;
}
</style>
