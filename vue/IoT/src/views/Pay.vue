<script setup>
import { ref, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { createOrder, queryOrder } from '../api/pay'

const amount = ref(0.01)
const subject = ref('智慧农业IoT-支付测试')
const loading = ref(false)
const outTradeNo = ref('')
const qrCode = ref('')
const payStatus = ref('')
const tradeNo = ref('')
const paidAmount = ref('')
const qrCanvas = ref(null)
let pollTimer = null

// 监听 qrCode 变化，渲染二维码到 canvas
watch(qrCode, async (val) => {
  if (val) {
    await nextTick()
    QRCode.toCanvas(qrCanvas.value, val, { width: 200, margin: 2 })
  }
})

// 创建支付订单 → 获取二维码 → 启动轮询
async function doCreateOrder() {
  loading.value = true
  payStatus.value = ''
  qrCode.value = ''
  try {
    const res = await createOrder({ amount: String(amount.value), subject: subject.value })
    outTradeNo.value = res.outTradeNo
    qrCode.value = res.qrCode
    startPolling()
  } catch (e) {
    const msg = e.response?.data?.message || '创建订单失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

// 每 3 秒轮询订单状态，支付成功则停止
function startPolling() {
  pollTimer = setInterval(async () => {
    try {
      const res = await queryOrder(outTradeNo.value)
      if (res.status === 'SUCCESS') {
        payStatus.value = 'SUCCESS'
        tradeNo.value = res.tradeNo || ''
        paidAmount.value = res.amount || ''
        stopPolling()
      }
    } catch (e) {
      // 轮询静默处理错误
    }
  }, 3000)
}

// 停止轮询
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 组件卸载时清理定时器
onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="pay-page">
    <h2 class="page-title">支付测试</h2>
    <p class="page-desc">支付宝沙箱环境 — 扫码支付演示</p>

    <!-- 下单表单 -->
    <el-card class="pay-card" v-if="payStatus !== 'SUCCESS'">
      <el-form label-width="80px">
        <el-form-item label="金额(元)">
          <el-input-number v-model="amount" :min="0.01" :step="0.01" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="subject" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="doCreateOrder">生成支付二维码</el-button>
        </el-form-item>
      </el-form>

      <!-- 二维码展示区 -->
      <div v-if="qrCode" class="qr-section">
        <p class="qr-tip">请使用支付宝沙箱版 App 扫码支付</p>
        <canvas ref="qrCanvas" class="qr-canvas" />
        <p class="order-no">订单号: {{ outTradeNo }}</p>
        <p class="polling-tip">等待支付中...</p>
      </div>
    </el-card>

    <!-- 支付成功 -->
    <el-result v-else icon="success" title="支付成功" :sub-title="`金额: ¥${paidAmount} | 流水号: ${tradeNo}`">
      <template #extra>
        <el-button type="primary" @click="payStatus = ''; qrCode = ''; tradeNo = ''; paidAmount = ''">再次支付</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.pay-page { max-width: 500px; margin: 0 auto; }
.page-title { font-size: 1.5rem; margin: 0 0 0.25rem; }
.page-desc { color: var(--text-secondary); margin: 0 0 1.5rem; font-size: 0.85rem; }
.pay-card { background: rgba(30, 41, 59, 0.5) !important; border-color: rgba(255, 255, 255, 0.06) !important; }

.qr-section { text-align: center; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border-color); }
.qr-tip { color: var(--text-secondary); font-size: 0.85rem; margin-bottom: 0.5rem; }
.qr-canvas { background: white; border-radius: 8px; padding: 8px; }
.order-no { font-size: 0.8rem; color: var(--text-muted); margin-top: 0.5rem; word-break: break-all; }
.polling-tip { font-size: 0.8rem; color: var(--color-yellow); }
</style>
