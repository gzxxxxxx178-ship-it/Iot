<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="我的" subtitle="演示账号" />

    <!-- 用户信息卡片 -->
    <view class="card">
      <view class="row-between">
        <view class="row">
          <view class="avatar"><text class="avatar-text">演</text></view>
          <view class="user-info">
            <text class="user-name">{{ store.user.username || '未登录' }}</text>
            <text class="hint">本地演示账号，不保存密码、不宣称真实认证</text>
          </view>
        </view>
        <StatusTag v-if="store.user.loggedIn" text="演示" kind="green" />
      </view>
    </view>

    <!-- 演示操作 -->
    <view class="card">
      <text class="section-title">演示操作</text>
      <button v-if="!store.user.loggedIn" class="btn-primary action-btn" @tap="onEnterDemo">进入演示</button>
      <button v-else class="btn-secondary action-btn" @tap="onExitDemo">退出演示</button>
      <button class="btn-danger action-btn" @tap="onResetDemo">重置演示数据</button>
    </view>

    <!-- 运行模式与配置说明 -->
    <view class="card">
      <text class="section-title">运行配置</text>
      <view class="cfg-row">
        <text class="cfg-label">运行模式</text>
        <text class="cfg-value">demo（仅演示数据）</text>
      </view>
      <view class="cfg-row">
        <text class="cfg-label">后端地址</text>
        <text class="cfg-value">{{ config.apiBaseUrl || '未配置（未接入后端）' }}</text>
      </view>
      <view class="cfg-row">
        <text class="cfg-label">实时推送</text>
        <text class="cfg-value">{{ config.wsBaseUrl || '未配置（未启用）' }}</text>
      </view>
    </view>

    <!-- 账号登录入口 -->
    <view class="card">
      <text class="section-title">账号</text>
      <button class="btn-secondary action-btn" @tap="goLogin">账号登录演示</button>
    </view>
  </view>
</template>

<script setup>
// 我的页：演示登录态、进入/退出演示、重置演示数据、运行配置展示
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDemoStore, enterDemo, exitDemo, resetDemo } from '../../utils/demo-store.js'
import { APP_CONFIG } from '../../config/index.js'

const store = useDemoStore()
const config = APP_CONFIG

// 进入演示：设置本地演示登录态
function onEnterDemo() {
  enterDemo()
  uni.showToast({ title: '已进入演示', icon: 'none' })
}

// 退出演示：清除本地演示登录态
function onExitDemo() {
  exitDemo()
  uni.showToast({ title: '已退出演示', icon: 'none' })
}

// 重置演示数据：二次确认后恢复初始演示状态
function onResetDemo() {
  uni.showModal({
    title: '重置演示数据',
    content: '确认将全部演示数据恢复为初始状态？',
    success: (res) => {
      if (res.confirm) {
        resetDemo()
        uni.showToast({ title: '已重置', icon: 'none' })
      }
    }
  })
}

// 跳转到账号登录演示页
function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' })
}
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}
.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background-color: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.avatar-text {
  font-size: 40rpx;
  color: #FFFFFF;
  font-weight: 700;
}
.user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.user-name {
  font-size: 32rpx;
  font-weight: 700;
  color: $color-text;
  margin-bottom: 4rpx;
}
.action-btn {
  margin-top: 16rpx;
}
.cfg-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid $color-border;
}
.cfg-row:last-child {
  border-bottom: none;
}
.cfg-label {
  font-size: 26rpx;
  color: $color-text-secondary;
  flex-shrink: 0;
  margin-right: 20rpx;
}
.cfg-value {
  font-size: 26rpx;
  color: $color-text;
  min-width: 0;
  text-align: right;
  word-break: break-all;
}
</style>
