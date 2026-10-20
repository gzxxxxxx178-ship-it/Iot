<template>
  <view class="page">
    <PageHeader title="我的" subtitle="账号与连接" />
    <view class="card">
      <text class="section-title">{{ session.username || '未登录' }}</text>
      <text class="hint">{{ session.token ? '可查看该账号拥有或获准访问的设备与历史记录' : '登录后查看账号可访问的设备和历史记录' }}</text>
      <button class="btn-primary action" @tap="goLogin">账号登录</button>
      <button v-if="session.token" class="btn-danger action" @tap="signOut">退出当前账号</button>
    </view>
    <view class="card">
      <text class="section-title">服务器地址</text>
      <text class="hint">当前：{{ config.apiBaseUrl }}</text>
      <text class="hint address-help">修改地址后需要重新登录。</text>
      <input v-model="address" class="field" placeholder="如 http://localhost:8080" confirm-type="done" />
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

// 退出当前账号并提示刷新会话是否已在服务端撤销。
async function signOut() {
  const revoked = await logout()
  if (!revoked) uni.showToast({ title: '本机已退出；服务端撤销失败，旧凭据将在30天内失效', icon: 'none', duration: 3500 })
}
</script>
<style lang="scss" scoped>
.action { margin-top: 24rpx; }
.field { height: 88rpx; margin-top: 24rpx; padding: 0 20rpx; background: $color-bg; border-radius: $radius-card; }
.hint { display: block; overflow-wrap: anywhere; }
.address-help { margin-top: 8rpx; }
</style>
