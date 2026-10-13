<template>
  <view class="page">
    <!-- 页面顶部：标题 + 日期 + 演示标记 -->
    <PageHeader title="工作台" :subtitle="dateText" />

    <!-- 当前设备上下文 -->
    <view class="card">
      <view class="row-between">
        <view>
          <text class="ctx-label">当前设备上下文</text>
          <view class="ctx-name-row">
            <text class="ctx-name">{{ contextDevice.name }}</text>
            <StatusTag :text="contextDevice.status === 'online' ? '在线' : '离线'" :kind="contextDevice.status === 'online' ? 'green' : 'gray'" />
          </view>
        </view>
        <text class="ctx-metric">{{ contextDevice.temp }}℃</text>
      </view>
    </view>

    <!-- 功能入口网格 -->
    <view class="card">
      <text class="section-title">业务功能</text>
      <view class="grid">
        <view v-for="item in entries" :key="item.url" class="grid-item" @tap="go(item.url)">
          <view class="grid-dot" :style="{ backgroundColor: item.color }"></view>
          <text class="grid-title">{{ item.title }}</text>
          <text class="grid-desc">{{ item.desc }}</text>
        </view>
      </view>
    </view>

    <!-- 待办提示 -->
    <view class="card">
      <text class="section-title">待办概览</text>
      <view class="todo-row">
        <text class="todo-text">未确认报警</text>
        <text class="todo-count" :style="{ color: unconfirmed > 0 ? '#DC2626' : '#1F2937' }">{{ unconfirmed }}</text>
      </view>
      <view class="todo-row">
        <text class="todo-text">已启用自动化规则</text>
        <text class="todo-count">{{ enabledRules }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
// 工作台页：设备上下文 + 业务功能入口 + 待办概览
import { computed } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDemoStore, formatDateTime } from '../../utils/demo-store.js'

const store = useDemoStore()

// 读取当前演示设备，保持上下文与设备列表一致。
const contextDevice = computed(() => store.devices[0])

// 顶部日期文本
const dateText = formatDateTime(Date.now()).slice(0, 10)

// 业务功能入口列表（对应原 Web 业务页面）
const entries = [
  { url: '/pages/alarm/alarm', title: '报警管理', desc: '筛选与确认', color: '#DC2626' },
  { url: '/pages/automation/automation', title: '自动化规则', desc: '条件到动作', color: '#1F6B4F' },
  { url: '/pages/chat/chat', title: 'AI 助手', desc: '本地固定回复', color: '#2563EB' },
  { url: '/pages/pay/pay', title: '支付演示', desc: '模拟支付', color: '#D97706' },
  { url: '/pages/dashboard/dashboard', title: '数据看板', desc: '大屏手机布局', color: '#1F6B4F' },
  { url: '/pages/simulation/simulation', title: '仿真验证', desc: '运行与重置', color: '#2563EB' },
  { url: '/pages/research/research', title: '研究工作台', desc: '示例运行指标', color: '#1F6B4F' }
]

// 未确认报警数量
const unconfirmed = computed(() => store.alarms.filter((a) => a.status === 'unconfirmed').length)

// 已启用自动化规则数量
const enabledRules = computed(() => store.rules.filter((r) => r.enabled).length)

// 跳转到指定非 tab 页面
function go(url) {
  uni.navigateTo({ url })
}
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}
.ctx-label {
  font-size: 22rpx;
  color: $color-text-secondary;
}
.ctx-name-row {
  margin-top: 8rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.ctx-name {
  font-size: 32rpx;
  font-weight: 700;
  color: $color-text;
  margin-right: 16rpx;
}
.ctx-metric {
  font-size: 40rpx;
  font-weight: 700;
  color: $color-primary;
}
.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
}
.grid-item {
  width: 33.33%;
  padding: 8rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.grid-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  margin-bottom: 12rpx;
}
.grid-title {
  font-size: 26rpx;
  font-weight: 600;
  color: $color-text;
}
.grid-desc {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $color-text-secondary;
  text-align: center;
}
.todo-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid $color-border;
}
.todo-row:last-child {
  border-bottom: none;
}
.todo-text {
  font-size: 26rpx;
  color: $color-text;
}
.todo-count {
  font-size: 32rpx;
  font-weight: 700;
}
</style>
