<template>
  <!-- 状态标记：小号胶囊标签，按颜色区分状态 -->
  <view class="status-tag" :style="{ backgroundColor: bg, color: fg }">
    <text class="status-tag-text">{{ text }}</text>
  </view>
</template>

<script setup>
// 状态标记组件：用颜色区分在线/离线、已确认/未确认、启用/停用等状态
import { computed } from 'vue'

const props = defineProps({
  // 状态文本
  text: { type: String, required: true },
  // 状态键：green / blue / amber / red / gray
  kind: { type: String, default: 'gray' }
})

// 颜色映射表：按状态键返回前景/背景色
const palette = {
  green: { fg: '#1F6B4F', bg: '#E7F0EC' },
  blue: { fg: '#2563EB', bg: '#E8EFFB' },
  amber: { fg: '#D97706', bg: '#FCF1E3' },
  red: { fg: '#DC2626', bg: '#FBEAEA' },
  gray: { fg: '#6B7280', bg: '#F3F4F6' }
}

// 计算当前状态对应的背景色
const bg = computed(() => (palette[props.kind] ? palette[props.kind].bg : palette.gray.bg))

// 计算当前状态对应的前景色
const fg = computed(() => (palette[props.kind] ? palette[props.kind].fg : palette.gray.fg))
</script>

<style lang="scss" scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border-radius: $radius-pill;
  flex-shrink: 0;
}
.status-tag-text {
  font-size: 22rpx;
  line-height: 1.4;
}
</style>
