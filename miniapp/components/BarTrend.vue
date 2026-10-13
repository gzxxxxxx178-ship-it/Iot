<template>
  <!-- 简单原生柱状趋势图：用 view 高度比例绘制，无图表库依赖 -->
  <view class="bar-trend">
    <view v-for="(item, index) in items" :key="index" class="bt-col">
      <view class="bt-bar-area">
        <view class="bt-bar" :style="{ height: barHeight(item.value), backgroundColor: color }"></view>
      </view>
      <text class="bt-label">{{ item.label }}</text>
      <text class="bt-value">{{ item.value }}</text>
    </view>
  </view>
</template>

<script setup>
// 原生柱状趋势图组件：根据数据最大值计算每根柱子的高度比例
import { computed } from 'vue'

const props = defineProps({
  // 趋势数据 [{ label, value }]
  items: { type: Array, default: () => [] },
  // 柱状图颜色（默认墨绿）
  color: { type: String, default: '#1F6B4F' },
  // 柱子最大高度（单位 rpx）
  maxBarHeight: { type: Number, default: 180 }
})

// 计算数据中的最大值（至少为 1，避免除零）
const maxValue = computed(() => {
  const values = props.items.map((i) => Number(i.value) || 0)
  return Math.max(1, ...values)
})

// 根据单个数值返回柱子的高度（rpx）
function barHeight(value) {
  const ratio = Math.max(0, Math.min(1, (Number(value) || 0) / maxValue.value))
  return Math.round(props.maxBarHeight * ratio) + 'rpx'
}
</script>

<style lang="scss" scoped>
.bar-trend {
  display: flex;
  flex-direction: row;
  align-items: flex-end;
  justify-content: space-between;
  width: 100%;
  min-height: 260rpx;
}
.bt-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
}
.bt-bar-area {
  height: 200rpx;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
}
.bt-bar {
  width: 32rpx;
  border-radius: 6rpx 6rpx 0 0;
  min-height: 4rpx;
}
.bt-label {
  margin-top: 12rpx;
  font-size: 20rpx;
  color: $color-text-secondary;
  white-space: nowrap;
}
.bt-value {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $color-text;
}
</style>
