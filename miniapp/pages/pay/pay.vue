<template>
  <view class="page pay-page">
    <PageHeader title="支付测试" subtitle="支付宝沙箱环境 · 扫码支付" badge="沙箱" />
    <view class="warning-card"><text>仅用于本地沙箱联调。确认当前后端已配置沙箱账户后再创建订单。</text></view>

    <view v-if="!session.token" class="card">
      <EmptyState text="请先登录账号创建支付测试订单" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>
    <template v-else>
      <view v-if="orderStatus === 'SUCCESS'" class="card success-card">
        <view class="success-mark">✓</view>
        <text class="success-title">支付成功</text>
        <text class="success-detail">金额：¥{{ paidAmount || amount }}</text>
        <text class="success-detail">支付宝流水号：{{ tradeNo || '--' }}</text>
        <button class="btn-primary action-button" @tap="resetOrder">再次支付</button>
      </view>

      <view v-else class="card order-form">
        <text class="section-title">创建沙箱订单</text>
        <text class="field-label">金额（元，0.01–100000，最多两位小数）</text>
        <input class="text-field" type="digit" v-model="amount" maxlength="12" placeholder="0.01" />
        <text class="field-label">商品描述（最多128字）</text>
        <input class="text-field" v-model="subject" maxlength="128" placeholder="智慧农业IoT-支付测试" />
        <button class="btn-primary action-button" :disabled="creating" @tap="createOrder">{{ creating ? '正在创建订单…' : orderStatus === 'PENDING' ? '重新创建订单' : '生成支付二维码' }}</button>
        <text v-if="createError" class="error-text">{{ createError }}</text>
      </view>

      <view v-if="outTradeNo && orderStatus !== 'SUCCESS'" class="card qr-card">
        <text class="section-title">请使用支付宝沙箱版 App 扫码</text>
        <view class="qr-frame">
          <canvas canvas-id="payment-qr" id="payment-qr" class="qr-canvas" />
        </view>
        <text class="hint order-number">订单号：{{ outTradeNo }}</text>
        <view class="payment-status" :class="orderStatus === 'PENDING' ? 'pending' : 'finished'">{{ paymentStatusLabel }}</view>
        <text v-if="queryError" class="error-text">{{ queryError }}</text>
        <view class="qr-actions">
          <button class="btn-secondary small-button" :disabled="querying" @tap="queryOrder">{{ querying ? '查询中…' : '立即查询状态' }}</button>
          <button class="btn-secondary small-button" @tap="copyQrContent">复制二维码内容</button>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { getCurrentInstance, nextTick, ref, watch } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import { session } from '../../utils/session.js'
import { createPaymentOrder, queryPaymentOrder } from '../../utils/pay-store.js'
import { createQrMatrix } from '../../utils/qr-code.js'

const amount = ref('0.01')
const subject = ref('智慧农业IoT-支付测试')
const creating = ref(false)
const querying = ref(false)
const outTradeNo = ref('')
const qrContent = ref('')
const orderStatus = ref('')
const tradeNo = ref('')
const paidAmount = ref('')
const createError = ref('')
const queryError = ref('')
let pollTimer = null
let pollInFlight = false
let pageVisible = false
let queryRequestId = 0
const paymentStatusLabel = ref('')

// 页面显示时恢复未结束订单的沙箱状态查询。
onShow(() => {
  pageVisible = true
  if (outTradeNo.value && orderStatus.value === 'PENDING') startPolling()
})

// 页面隐藏时停止后台轮询。
onHide(() => { pageVisible = false; stopPolling() })

// 账号切换后清除旧账号订单信息并停止轮询。
watch(() => session.generation, () => {
  stopPolling(); clearOrderState()
})

// 创建支付宝沙箱订单、绘制二维码并开始查询支付状态。
async function createOrder() {
  const numericAmount = Number(amount.value)
  if (!/^(?:\d+)(?:\.\d{1,2})?$/.test(amount.value) || !Number.isFinite(numericAmount) || numericAmount < 0.01 || numericAmount > 100000) {
    createError.value = '金额必须在0.01至100000元之间且最多两位小数'; return
  }
  if (!subject.value.trim() || subject.value.length > 128) { createError.value = '商品描述不能为空且不能超过128字'; return }
  creating.value = true; createError.value = ''; queryError.value = ''
  stopPolling(); clearOrderState()
  const generation = session.generation
  try {
    const result = await createPaymentOrder(numericAmount.toFixed(2), subject.value.trim())
    if (generation !== session.generation) return
    if (!result?.outTradeNo || !result?.qrCode) throw new Error('后端未返回有效订单号或二维码内容')
    outTradeNo.value = result.outTradeNo
    qrContent.value = result.qrCode
    orderStatus.value = 'PENDING'
    paymentStatusLabel.value = '等待支付中…'
    await nextTick()
    drawQrCode(result.qrCode)
    startPolling()
  } catch (error) {
    if (!error?.stale && generation === session.generation) createError.value = error.message || '创建沙箱订单失败'
  } finally { creating.value = false }
}

// 将支付宝返回的支付字符串绘制到小程序原生 Canvas。
function drawQrCode(value) {
  try {
    const matrix = createQrMatrix(value)
    const context = uni.createCanvasContext('payment-qr', getCurrentInstance()?.proxy)
    const canvasSize = 240
    const quietZone = 4
    const cellSize = canvasSize / (matrix.size + quietZone * 2)
    context.setFillStyle('#ffffff'); context.fillRect(0, 0, canvasSize, canvasSize)
    context.setFillStyle('#111827')
    for (let row = 0; row < matrix.size; row += 1) {
      for (let column = 0; column < matrix.size; column += 1) {
        if (!matrix.data[row * matrix.size + column]) continue
        const x = Math.floor((column + quietZone) * cellSize)
        const y = Math.floor((row + quietZone) * cellSize)
        const right = Math.ceil((column + quietZone + 1) * cellSize)
        const bottom = Math.ceil((row + quietZone + 1) * cellSize)
        context.fillRect(x, y, right - x, bottom - y)
      }
    }
    context.draw(false)
  } catch (error) {
    createError.value = '订单已创建，但二维码生成失败：' + (error.message || '二维码内容无效')
  }
}

// 按每3秒一次的频率查询订单状态并在支付完成后停止轮询。
function startPolling() {
  stopPolling()
  if (!pageVisible || !outTradeNo.value || orderStatus.value !== 'PENDING') return
  queryOrder()
  pollTimer = setInterval(queryOrder, 3000)
}

// 停止支付状态轮询计时器。
function stopPolling(invalidateRequest = true) {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
  if (invalidateRequest) { queryRequestId += 1; pollInFlight = false; querying.value = false }
}

// 查询当前账号订单状态并更新成功或终止状态。
async function queryOrder() {
  if (!pageVisible || !outTradeNo.value || pollInFlight) return
  pollInFlight = true; querying.value = true; queryError.value = ''
  const requestId = ++queryRequestId
  const order = outTradeNo.value; const generation = session.generation
  try {
    const result = await queryPaymentOrder(order)
    if (requestId !== queryRequestId || generation !== session.generation || order !== outTradeNo.value) return
    orderStatus.value = result?.status || 'UNKNOWN'
    if (orderStatus.value === 'SUCCESS') {
      tradeNo.value = result.tradeNo || ''; paidAmount.value = result.amount || ''
      paymentStatusLabel.value = '支付成功'; stopPolling(false)
    } else if (orderStatus.value !== 'PENDING') {
      paymentStatusLabel.value = orderStatus.value === 'UNKNOWN' ? '订单状态未知' : '订单状态：' + orderStatus.value
      stopPolling(false)
    } else paymentStatusLabel.value = '等待支付中…'
  } catch (error) {
    if (requestId === queryRequestId && !error?.stale && generation === session.generation && order === outTradeNo.value) queryError.value = error.message || '支付状态查询失败，可手动重试'
  } finally {
    if (requestId === queryRequestId) { pollInFlight = false; querying.value = false }
  }
}

// 将支付宝二维码字符串复制到剪贴板，供测试时备用。
function copyQrContent() {
  if (!qrContent.value) return
  uni.setClipboardData({ data: qrContent.value, success: () => uni.showToast({ title: '二维码内容已复制', icon: 'success' }) })
}

// 清除当前订单在页面内的展示状态。
function clearOrderState() {
  outTradeNo.value = ''; qrContent.value = ''; orderStatus.value = ''; tradeNo.value = ''; paidAmount.value = ''
  paymentStatusLabel.value = ''; createError.value = ''; queryError.value = ''
}

// 重置订单表单以开始下一笔沙箱支付。
function resetOrder() { stopPolling(); clearOrderState() }

// 跳转到账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style lang="scss" scoped>
.pay-page { padding-bottom: 48rpx; }
.warning-card { margin: 16rpx 0; padding: 20rpx 24rpx; border-radius: 14rpx; background: #fff7e8; color: #875b16; font-size: 24rpx; line-height: 1.6; }
.order-form { padding: 26rpx; }
.field-label { display: block; margin: 22rpx 0 10rpx; color: $color-text-secondary; font-size: 23rpx; }
.action-button { width: 100%; margin-top: 24rpx; }
.error-text { display: block; margin-top: 14rpx; color: $color-red; font-size: 23rpx; line-height: 1.5; }
.qr-card { display: flex; flex-direction: column; align-items: center; margin-top: 18rpx; text-align: center; }
.qr-frame { margin: 22rpx 0 14rpx; padding: 18rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 2rpx 12rpx rgba(15, 23, 42, .1); }
.qr-canvas { display: block; width: 480rpx; height: 480rpx; }
.order-number { max-width: 100%; overflow-wrap: anywhere; }
.payment-status { margin-top: 16rpx; font-size: 26rpx; font-weight: 600; }
.pending { color: $color-amber; }
.finished { color: $color-text-secondary; }
.qr-actions { display: flex; width: 100%; gap: 12rpx; margin-top: 18rpx; }
.small-button { flex: 1; margin: 0; padding: 0 10rpx; font-size: 22rpx; }
.success-card { display: flex; flex-direction: column; align-items: center; padding: 42rpx 28rpx; }
.success-mark { display: flex; align-items: center; justify-content: center; width: 90rpx; height: 90rpx; border-radius: 50%; background: #e8f5ed; color: $color-primary; font-size: 54rpx; }
.success-title { margin-top: 22rpx; color: $color-text; font-size: 34rpx; font-weight: 600; }
.success-detail { margin-top: 12rpx; color: $color-text-secondary; font-size: 23rpx; overflow-wrap: anywhere; }
</style>
