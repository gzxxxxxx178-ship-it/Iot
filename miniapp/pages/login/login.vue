<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="账号登录" subtitle="演示模式" />

    <!-- 登录表单（仅演示，不保存密码） -->
    <view class="card">
      <text class="form-label">用户名</text>
      <input v-model="username" class="form-input" placeholder="演示：任意用户名" placeholder-class="ph" />

      <text class="form-label">密码</text>
      <input v-model="password" class="form-input" password placeholder="演示：密码不保存" placeholder-class="ph" />

      <button class="btn-primary form-btn" @tap="onEnterDemo">进入演示</button>

      <text class="hint form-note">本页为演示登录：不保存密码、不宣称真实认证，也不发起任何网络请求。</text>
    </view>
  </view>
</template>

<script setup>
// 账号登录页：演示登录（不保存密码、不发起请求、不宣称真实认证）
import { ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { enterDemo } from '../../utils/demo-store.js'

// 演示用户名（不持久化）
const username = ref('')

// 演示密码（不持久化、不保存）
const password = ref('')

// 进入演示：写入本地演示登录态并切换到概览 tab
function onEnterDemo() {
  password.value = ''
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
