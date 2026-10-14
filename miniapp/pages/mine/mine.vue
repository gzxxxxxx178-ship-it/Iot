<template>
  <view class="page">
    <PageHeader title="我的" subtitle="账号与连接" />
    <view class="card">
      <text class="section-title">{{ session.username || '未登录' }}</text>
      <text class="hint">{{ session.token ? '已连接账号，令牌仅在内存保存' : '请使用现有系统账号登录' }}</text>
      <button class="btn-primary action" @tap="goLogin">账号登录</button>
      <button v-if="session.token" class="btn-danger action" @tap="logout">退出当前账号</button>
    </view>
    <view class="card">
      <text class="section-title">后端连接</text>
      <text class="hint">当前：{{ config.apiBaseUrl }}</text>
      <input v-model="address" class="field" placeholder="http://localhost:8080" />
      <text v-if="error" class="hint">{{ error }}</text>
      <button class="btn-secondary action" @tap="save">保存后端地址</button>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import { APP_CONFIG as config } from '../../config/index.js'
import { session } from '../../utils/session.js'
import { configure, logout } from '../../utils/live-store.js'
const address = ref(config.apiBaseUrl)
const error = ref('')

// 同步已生效的连接配置。
onShow(() => { address.value = config.apiBaseUrl })

// 校验新地址并清除旧账号认证与数据。
function save() {
  error.value = ''
  try { configure('live', address.value); uni.showToast({ title: '已保存，请重新登录', icon: 'none' }) }
  catch (err) { error.value = err.message }
}

// 打开真实账号登录页面。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>
<style lang="scss" scoped>
.action { margin-top: 24rpx; }
.field { height: 88rpx; margin-top: 24rpx; padding: 0 20rpx; background: $color-bg; border-radius: $radius-card; }
.hint { display: block; overflow-wrap: anywhere; }
</style>
