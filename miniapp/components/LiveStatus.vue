<template>
  <view v-if="config.mode === 'live'" class="card">
    <template v-if="!session.token">
      <text class="hint">登录后同步账号可访问的设备状态、最新读数和控制回执。</text>
      <button class="btn-secondary" @tap="goLogin">前往登录</button>
    </template>
    <template v-else>
      <text class="hint connection-state">{{ live.loading ? '正在同步设备和读数…' : live.error ? '同步失败：' + live.error : '最近同步：' + (live.updatedAt || '尚未同步') }}</text>
      <button class="btn-secondary" :disabled="live.loading" @tap="refreshLive">{{ live.error ? '重试同步' : '刷新数据' }}</button>
    </template>
  </view>
</template>
<script setup>
import { APP_CONFIG as config } from '../config/index.js'
import { session } from '../utils/session.js'
import { live, refreshLive } from '../utils/live-store.js'

// 从连接状态卡片直接进入账号登录。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>
<style lang="scss" scoped>
.connection-state { display: block; overflow-wrap: anywhere; }
</style>
