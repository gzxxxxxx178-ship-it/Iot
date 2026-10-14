<template>
  <view class="page">
    <PageHeader title="账号登录" subtitle="使用现有系统账号" />
    <view class="card">
      <text class="hint">后端：{{ config.apiBaseUrl }}</text>
      <text class="label">用户名</text>
      <input v-model="username" class="field" :disabled="busy" placeholder="用户名" />
      <text class="label">密码</text>
      <input v-model="password" class="field" :disabled="busy" password placeholder="密码不保存" />
      <text v-if="error" class="hint">{{ error }}</text>
      <button class="btn-primary submit" :loading="busy" :disabled="busy" @tap="onLogin">登录</button>
      <text class="hint">令牌仅保存在内存中，重启后重新登录。</text>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onHide } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import { APP_CONFIG as config } from '../../config/index.js'
import { login, logout } from '../../utils/live-store.js'
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

// 提交真实账号并清空密码输入，失败时保留可操作提示。
async function onLogin() {
  if (busy.value) return
  if (!username.value.trim() || !password.value) { error.value = '请输入用户名和密码'; return }
  busy.value = true; error.value = ''
  const submitted = password.value
  password.value = ''
  try {
    await login(username.value, submitted)
    busy.value = false
    uni.switchTab({ url: '/pages/overview/overview' })
  } catch (err) { if (!err.stale) error.value = err.message }
  finally { busy.value = false }
}

// 离开时清理密码，并作废未完成的登录。
onHide(() => { password.value = ''; if (busy.value) logout() })
</script>
<style lang="scss" scoped>
.label { display: block; margin: 24rpx 0 8rpx; }
.field { height: 88rpx; padding: 0 20rpx; border: 1rpx solid $color-border; border-radius: $radius-card; background: $color-bg; }
.submit { margin: 28rpx 0 20rpx; }
.hint { overflow-wrap: anywhere; }
</style>
