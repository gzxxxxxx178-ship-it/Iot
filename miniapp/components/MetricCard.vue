<template>
  <!-- 指标卡片：标签 + 数值/单位 + 状态色条 -->
  <view class="metric-card">
    <view class="mc-bar" :style="{ backgroundColor: color }"></view>
    <view class="mc-body">
      <text class="mc-label">{{ label }}</text>
      <view class="mc-value-row">
        <text class="mc-value">{{ value }}</text>
        <text v-if="unit" class="mc-unit">{{ unit }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
// 指标卡片组件：展示单个指标（标签、数值、单位）与左侧状态色条
defineProps({
  // 指标名称
  label: { type: String, required: true },
  // 指标数值
  value: { type: [String, Number], required: true },
  // 数值单位
  unit: { type: String, default: '' },
  // 状态色（默认墨绿）
  color: { type: String, default: '#1F6B4F' }
})
</script>

<style lang="scss" scoped>
.metric-card {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  overflow: hidden;
  padding: 24rpx;
}
.mc-bar {
  width: 8rpx;
  border-radius: 4rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.mc-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.mc-label {
  font-size: 24rpx;
  color: $color-text-secondary;
}
.mc-value-row {
  margin-top: 8rpx;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
.mc-value {
  font-size: 40rpx;
  font-weight: 700;
  color: $color-text;
}
.mc-unit {
  margin-left: 6rpx;
  font-size: 22rpx;
  color: $color-text-secondary;
}
</style>
