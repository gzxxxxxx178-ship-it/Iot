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

watch(qrCode, async (val) => {
  if (val) {
    await nextTick()
    QRCode.toCanvas(qrCanvas.value, val, { width: 200, margin: 2 })
  }
})

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
      // 轮询静默处理
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="pay-page">
    <h2 class="page-title">支付测试</h2>
    <p class="page-desc">支付宝沙箱环境 — 扫码支付演示</p>

    <el-card class="pay-card" v-if="payStatus !== 'SUCCESS'">
      <el-form label-width="80px">
        <el-form-item label="金额(元)">
          <el-input-number v-model="amount" :min="0.01" :step="0.01" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="subject" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="doCreateOrder">
            生成支付二维码
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="qrCode" class="qrcode-section">
        <el-divider />
        <p class="qrcode-tip">请使用 <strong>支付宝沙箱版 App</strong> 扫描二维码</p>
        <canvas ref="qrCanvas" class="qrcode-canvas"></canvas>
        <div class="order-info">
          <span>订单号: {{ outTradeNo }}</span>
          <span class="order-status">等待支付中...</span>
        </div>
      </div>
    </el-card>

    <el-card class="pay-card success-card" v-else>
      <el-result icon="success" title="支付成功" sub-title="感谢您的支付">
        <template #extra>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="订单号">{{ outTradeNo }}</el-descriptions-item>
            <el-descriptions-item label="支付金额">{{ paidAmount }} 元</el-descriptions-item>
            <el-descriptions-item label="交易流水号">{{ tradeNo }}</el-descriptions-item>
            <el-descriptions-item label="支付状态">
              <el-tag type="success">已支付</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>
      <div style="text-align: center; margin-top: 1rem;">
        <el-button type="primary" @click="payStatus = ''; qrCode = ''; outTradeNo = ''; tradeNo = ''">
          继续支付
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.pay-page {
  max-width: 520px;
  margin: 0 auto;
}

.page-title {
  margin-bottom: 0.25rem;
}

.page-desc {
  color: var(--text-secondary);
  font-size: 0.85rem;
  margin-bottom: 1.5rem;
}

.pay-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}

.qrcode-section {
  text-align: center;
}

.qrcode-tip {
  margin-bottom: 1rem;
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.qrcode-canvas {
  display: block;
  margin: 0 auto;
  background: #fff;
  padding: 12px;
  border-radius: 8px;
}

.order-info {
  margin-top: 1rem;
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.order-status {
  color: var(--color-yellow);
}

.success-card :deep(.el-result__title) {
  color: var(--text-primary);
}

.success-card :deep(.el-result__subtitle) {
  color: var(--text-secondary);
}
</style>
