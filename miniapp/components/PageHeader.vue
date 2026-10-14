<template>
  <!-- 页面顶部：标题、上下文与接口状态 -->
  <view class="page-header">
    <view class="ph-left">
      <text class="ph-title">{{ title }}</text>
      <text v-if="subtitle" class="ph-subtitle">{{ subtitle }}</text>
    </view>
    <view class="ph-badge">
      <text class="ph-badge-text">{{ badge }}</text>
    </view>
  </view>
</template>

<script setup>
// 页面顶部组件：展示标题、上下文与当前接口状态。
defineProps({
  // 页面主标题
  title: { type: String, required: true },
  badge: { type: String, default: '真实接口' },
  // 可选副标题（如日期或设备上下文）
  subtitle: { type: String, default: '' }
})
</script>

<style lang="scss" scoped>
.page-header {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx $space-page 20rpx;
}
.ph-left {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.ph-title {
  font-size: 40rpx;
  font-weight: 700;
  color: $color-text;
  line-height: 1.2;
}
.ph-subtitle {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $color-text-secondary;
}
.ph-badge {
  margin-left: 16rpx;
  padding: 6rpx 20rpx;
  background-color: $color-primary-soft;
  border-radius: $radius-pill;
}
.ph-badge-text {
  font-size: 22rpx;
  color: $color-primary;
}
</style>
