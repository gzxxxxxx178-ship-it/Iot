<template>
  <view v-if="config.mode === 'live'" class="card">
    <text v-if="!session.token" class="hint">请先到“我的”登录账号</text>
    <template v-else>
      <text class="hint">{{ live.loading ? '正在加载…' : live.error || ('最近更新：' + (live.updatedAt || '尚未加载')) }}</text>
      <button class="btn-secondary" :disabled="live.loading" @tap="refreshLive">刷新数据</button>
    </template>
  </view>
</template>
<script setup>
import { APP_CONFIG as config } from '../config/index.js'
import { session } from '../utils/session.js'
import { live, refreshLive } from '../utils/live-store.js'
</script>
