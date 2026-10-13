<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="支付演示" subtitle="无真实扣款" />

    <!-- 模拟支付表单 -->
    <view class="card">
      <text class="form-label">金额（元）</text>
      <input v-model="amount" class="form-input" type="digit" placeholder="例如：1.00" placeholder-class="ph" />

      <text class="form-label">商品说明</text>
      <input v-model="subject" class="form-input" placeholder="例如：演示服务" placeholder-class="ph" />

      <button class="btn-primary form-btn" @tap="onPay">发起模拟支付</button>
      <text class="hint form-note">本页为支付演示：仅生成模拟结果，无真实扣款，也不发起任何支付请求。</text>
    </view>

    <!-- 最近一次结果 -->
    <view v-if="lastPay" class="card result-card">
      <text class="section-title">最近结果</text>
      <view class="result-row"><text class="result-label">状态</text><StatusTag text="支付成功（模拟）" kind="green" /></view>
      <view class="result-row"><text class="result-label">订单号</text><text class="result-value">{{ lastPay.outTradeNo }}</text></view>
      <view class="result-row"><text class="result-label">金额</text><text class="result-value">{{ lastPay.amount }} 元</text></view>
      <view class="result-row"><text class="result-label">商品</text><text class="result-value">{{ lastPay.subject }}</text></view>
    </view>

    <!-- 支付记录 -->
    <view v-if="store.payments.length" class="card">
      <text class="section-title">支付记录</text>
      <view v-for="p in store.payments" :key="p.id" class="pay-row">
        <text class="pay-subject">{{ p.subject }}</text>
        <text class="pay-amount">{{ p.amount }} 元</text>
      </view>
    </view>
  </view>
</template>

<script setup>
// 支付演示页：模拟支付结果（无真实扣款、不发起支付请求）
import { ref, computed } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDemoStore, createPayment } from '../../utils/demo-store.js'

const store = useDemoStore()

// 模拟金额输入
const amount = ref('')

// 模拟商品说明输入
const subject = ref('')

// 最近一笔模拟支付记录
const lastPay = computed(() => (store.payments.length ? store.payments[0] : null))

// 发起模拟支付：校验金额后生成模拟成功结果
function onPay() {
  const num = Number(amount.value)
  if (!Number.isFinite(num) || num < 0.01 || num > 999999.99) {
    uni.showToast({ title: '请输入有效金额', icon: 'none' })
    return
  }
  createPayment(num.toFixed(2), subject.value.trim() || '演示服务')
  uni.showToast({ title: '模拟支付成功', icon: 'none' })
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
.result-card {
  margin-top: 0;
}
.result-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 0;
  border-bottom: 1rpx solid $color-border;
}
.result-row:last-child {
  border-bottom: none;
}
.result-label {
  font-size: 26rpx;
  color: $color-text-secondary;
  flex-shrink: 0;
  margin-right: 20rpx;
}
.result-value {
  font-size: 26rpx;
  color: $color-text;
  min-width: 0;
  text-align: right;
  word-break: break-all;
}
.pay-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid $color-border;
}
.pay-row:last-child {
  border-bottom: none;
}
.pay-subject {
  font-size: 26rpx;
  color: $color-text;
  min-width: 0;
  flex: 1;
  word-break: break-all;
  margin-right: 20rpx;
}
.pay-amount {
  font-size: 28rpx;
  font-weight: 600;
  color: $color-primary;
}
</style>
