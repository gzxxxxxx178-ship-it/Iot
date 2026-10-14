<template>
  <view class="page">
    <PageHeader title="工作台" subtitle="业务功能" />
    <view class="card">
      <text class="section-title">当前设备</text>
      <text class="hint">{{ live.devices.length ? live.devices[0].name : '暂无设备数据，请登录并刷新设备列表' }}</text>
    </view>
    <view class="card">
      <view v-for="entry in entries" :key="entry.url" class="entry" @tap="go(entry.url)">
        <text>{{ entry.title }}</text><text class="hint">待接入 ›</text>
      </view>
    </view>
  </view>
</template>
<script setup>
import PageHeader from '../../components/PageHeader.vue'
import { live } from '../../utils/live-store.js'
const entries = [
  { title: '报警管理', url: '/pages/alarm/alarm' },
  { title: '自动化规则', url: '/pages/automation/automation' },
  { title: 'AI 助手', url: '/pages/chat/chat' },
  { title: '支付', url: '/pages/pay/pay' },
  { title: '数据看板', url: '/pages/dashboard/dashboard' },
  { title: '仿真验证', url: '/pages/simulation/simulation' },
  { title: '研究工作台', url: '/pages/research/research' }
]

// 打开业务入口，未迁移模块不生成本地结果。
function go(url) { uni.navigateTo({ url }) }
</script>
<style lang="scss" scoped>
.entry { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 0; border-bottom: 1rpx solid $color-border; }
</style>
