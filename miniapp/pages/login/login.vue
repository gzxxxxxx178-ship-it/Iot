<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="账号登录" :subtitle="config.mode === 'live' ? '使用现有系统账号' : '本地演示'" :badge="config.mode === 'live' ? '真实接口' : '演示'" />

    <!-- 登录表单（仅演示，不保存密码） -->
    <view class="card">
      <text class="form-label">用户名</text>
      <input v-model="username" class="form-input" :disabled="busy" placeholder="用户名" placeholder-class="ph" />

      <text class="form-label">密码</text>
      <input v-model="password" class="form-input" :disabled="busy" password placeholder="密码不保存" placeholder-class="ph" />

      <text v-if="error" class="hint">{{ error }}</text>
      <button v-if="config.mode === 'live'" class="btn-primary form-btn" :loading="busy" :disabled="busy" @tap="onLogin">登录</button>
      <button class="btn-secondary form-btn" :disabled="busy" @tap="onEnterDemo">进入演示</button>

      <text class="hint form-note">{{ config.mode === 'live' ? '令牌仅保存在内存中，重新启动后需要登录。' : '真实登录请先在“我的”配置后端地址与模式。' }}</text>
    </view>
  </view>
</template>

<script setup>
// 账号登录页：支持真实账号和独立的演示入口，不持久化密码。
import { ref } from 'vue'
import { onHide } from '@dcloudio/uni-app'
import { APP_CONFIG as config } from '../../config/index.js'
import { login, configure, logout } from '../../utils/live-store.js'
import PageHeader from '../../components/PageHeader.vue'
import { enterDemo } from '../../utils/demo-store.js'

// 演示用户名（不持久化）
const username = ref('')

// 演示密码（不持久化、不保存）
const password = ref('')
const busy = ref(false)
const error = ref('')

// 登录现有账号，提交后立即清空输入中的密码。
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

// 离开页面时清理未提交的密码。
onHide(() => { password.value = ''; if (busy.value) logout() })

// 进入演示：写入本地演示登录态并切换到概览 tab
function onEnterDemo() {
  password.value = ''
  configure('demo', config.apiBaseUrl)
  enterDemo()
  uni.switchTab({ url: '/pages/overview/overview' })
}
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}
.form-label {
  display: block;
  font-size: 26rpx;
  color: $color-text;
  margin-bottom: 8rpx;
}
.form-input {
  height: 88rpx;
  background-color: $color-bg;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: $color-text;
  margin-bottom: 24rpx;
}
.ph {
  color: $color-text-light;
}
.form-btn {
  margin-top: 8rpx;
}
.form-note {
  display: block;
  margin-top: 20rpx;
}
</style>
