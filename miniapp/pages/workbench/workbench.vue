<template>
  <view class="page">
    <PageHeader title="工作台" subtitle="业务功能" />
    <view class="card">
      <text class="section-title">当前设备</text>
      <text class="hint">{{ live.devices.length ? live.devices[0].name : '暂无设备数据，请登录并刷新设备列表' }}</text>
    </view>
    <view class="card">
      <view v-for="entry in entries" :key="entry.url" class="entry" @tap="go(entry.url)">
        <text>{{ entry.title }}</text><text class="entry-status" :class="entry.kind">{{ entry.status }} ›</text>
      </view>
    </view>
  </view>
</template>
<script setup>
import PageHeader from '../../components/PageHeader.vue'
import { live } from '../../utils/live-store.js'
const entries = [
  { title: '实时监控', url: '/pages/monitor/monitor', status: '已接入', kind: 'ready' },
  { title: '报警管理', url: '/pages/alarm/alarm', status: '已接入', kind: 'ready' },
  { title: '自动化规则', url: '/pages/automation/automation', status: '已接入', kind: 'ready' },
  { title: 'AI 助手', url: '/pages/chat/chat', status: '已接入', kind: 'ready' },
  { title: '支付', url: '/pages/pay/pay', status: '已接入（沙箱）', kind: 'ready' },
  { title: '数据大屏', url: '/pages/screen/screen', status: '部分迁移', kind: 'partial' },
  { title: '数据看板', url: '/pages/dashboard/dashboard', status: '部分迁移', kind: 'partial' },
  { title: '仿真验证', url: '/pages/simulation/simulation', status: '已接入', kind: 'ready' },
  { title: '研究工作台', url: '/pages/research/research', status: '已接入', kind: 'ready' }
]

// 打开业务入口，未迁移模块不生成本地结果。
function go(url) { uni.navigateTo({ url }) }
</script>
<style lang="scss" scoped>
.entry { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 28rpx 0; border-bottom: 1rpx solid $color-border; }
.entry-status { flex: none; font-size: 22rpx; }
.ready { color: $color-primary; }
.partial { color: $color-amber; }
.pending { color: $color-text-light; }
</style>
