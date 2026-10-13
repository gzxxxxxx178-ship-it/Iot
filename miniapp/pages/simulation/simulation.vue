<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="仿真验证" subtitle="演示运行" />

    <!-- 仿真状态卡片 -->
    <view class="card">
      <view class="row-between">
        <text class="sim-status">{{ statusText }}</text>
        <StatusTag :text="statusTagText" :kind="statusKind" />
      </view>
      <view class="progress-track">
        <view class="progress-fill" :style="{ width: store.simulation.progress + '%' }"></view>
      </view>
      <text class="hint">进度 {{ store.simulation.progress }}%</text>

      <view class="sim-actions">
        <button class="btn-primary sim-btn" :disabled="store.simulation.status === 'running'" @tap="onRun">运行</button>
        <button class="btn-secondary sim-btn" @tap="onReset">重置</button>
      </view>
    </view>

    <!-- 仿真结果 -->
    <view v-if="store.simulation.result" class="card">
      <text class="section-title">运行结果</text>
      <view class="result-row"><text class="result-label">稳态误差</text><text class="result-value">{{ store.simulation.result.steadyError }}</text></view>
      <view class="result-row"><text class="result-label">调节时间</text><text class="result-value">{{ store.simulation.result.settleTime }}</text></view>
      <view class="result-row"><text class="result-label">超调量</text><text class="result-value">{{ store.simulation.result.overshoot }}</text></view>
      <text class="hint result-note">{{ store.simulation.result.conclusion }}</text>
    </view>

    <!-- 说明 -->
    <view class="demo-note">仿真为演示运行：仅生成占位指标，不连接 MATLAB 或真实控制闭环。</view>
  </view>
</template>

<script setup>
// 仿真验证页：运行/重置仿真，展示进度与演示结果反馈
import { computed, onUnmounted } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDemoStore, simulationStart, simulationAdvance, simulationReset } from '../../utils/demo-store.js'

const store = useDemoStore()

// 仿真定时器句柄
let timer = null

// 仿真状态文本
const statusText = computed(() => {
  const map = { idle: '等待运行', running: '运行中', done: '已完成' }
  return map[store.simulation.status] || '等待运行'
})

// 状态标记文本
const statusTagText = computed(() => {
  const map = { idle: '空闲', running: '运行中', done: '完成' }
  return map[store.simulation.status] || '空闲'
})

// 状态标记颜色
const statusKind = computed(() => {
  const map = { idle: 'gray', running: 'blue', done: 'green' }
  return map[store.simulation.status] || 'gray'
})

// 停止并清理仿真定时器
function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// 运行仿真：启动状态并定时推进进度
function onRun() {
  if (store.simulation.status === 'running') return
  simulationStart()
  startTimer()
}

// 继续推进当前演示运行，避免重复定时器。
function startTimer() {
  stopTimer()
  // 推进进度；完成后释放定时器。
  timer = setInterval(() => {
    simulationAdvance(5)
    if (store.simulation.status === 'done') {
      stopTimer()
      uni.showToast({ title: '仿真完成', icon: 'none' })
    }
  }, 200)
}

// 页面重新可见时恢复被隐藏中断的演示运行。
onShow(() => {
  if (store.simulation.status === 'running') startTimer()
})

// 页面隐藏时暂停演示计时，保留进度供下次恢复。
onHide(stopTimer)

// 页面退出时清理演示定时器。
onUnload(stopTimer)

// 重置仿真：停止定时器并恢复空闲
function onReset() {
  stopTimer()
  simulationReset()
}

// 组件卸载时清理定时器
onUnmounted(stopTimer)
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}
.sim-status {
  font-size: 30rpx;
  font-weight: 600;
  color: $color-text;
}
.progress-track {
  margin-top: 24rpx;
  height: 16rpx;
  background-color: $color-bg;
  border-radius: 8rpx;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background-color: $color-primary;
  border-radius: 8rpx;
}
.sim-actions {
  display: flex;
  flex-direction: row;
  margin-top: 32rpx;
}
.sim-btn {
  flex: 1;
  margin: 0 8rpx;
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
}
.result-value {
  font-size: 26rpx;
  color: $color-text;
}
.result-note {
  display: block;
  margin-top: 16rpx;
}
</style>
