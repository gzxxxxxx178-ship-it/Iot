<template>
  <!-- 空状态：简洁占位，不使用 emoji 或外部图标 -->
  <view class="empty-state">
    <view class="empty-circle">
      <view class="empty-line"></view>
    </view>
    <text class="empty-text">{{ text }}</text>
  </view>
</template>

<script setup>
// 空状态组件：用于列表无数据时展示占位
defineProps({
  // 空状态提示文案
  text: { type: String, default: '暂无数据' }
})
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0;
}
.empty-circle {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  border: 1rpx solid $color-border;
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-line {
  width: 36rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background-color: $color-text-light;
}
.empty-text {
  margin-top: 20rpx;
  font-size: 26rpx;
  color: $color-text-secondary;
}
</style>
