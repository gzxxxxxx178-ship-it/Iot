<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="研究工作台" subtitle="示例运行指标" />

    <!-- 安全边界说明 -->
    <view class="demo-note">以下均为演示占位指标，不代表冻结实验结论，也不声称田间实测或生产验证。</view>

    <!-- 示例运行列表 -->
    <view v-if="store.researchRuns.length" class="list">
      <view v-for="run in store.researchRuns" :key="run.id" class="run-card">
        <view class="run-top">
          <text class="run-name">{{ run.name }}</text>
          <StatusTag :text="run.status === 'completed' ? '已完成' : '混合结果'" :kind="run.status === 'completed' ? 'green' : 'amber'" />
        </view>
        <text class="hint">{{ formatDateTime(run.time) }}</text>
        <view class="run-metrics">
          <view v-for="m in run.metrics" :key="m.key" class="run-metric">
            <text class="run-metric-key">{{ m.key }}</text>
            <text class="run-metric-value">{{ m.value }}</text>
          </view>
        </view>
      </view>
    </view>
    <EmptyState v-else text="暂无示例运行" />

    <!-- 新增示例运行 -->
    <view class="footer-add">
      <button class="btn-primary" @tap="onAddRun">新增示例运行</button>
    </view>

    <view class="card">
      <text class="section-title">证据问答</text>
      <input v-model="question" class="question-input" placeholder="输入研究问题（演示）" />
      <button class="btn-secondary" @tap="onAsk">查询示例</button>
      <text v-if="answer" class="hint answer">{{ answer }}</text>
    </view>
  </view>
</template>

<script setup>
// 研究工作台页：展示示例运行指标（不复制冻结论文结论），支持新增示例运行
import PageHeader from '../../components/PageHeader.vue'
import { ref } from 'vue'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import { useDemoStore, formatDateTime, addResearchRun } from '../../utils/demo-store.js'

const store = useDemoStore()
const question = ref('')
const answer = ref('')

// 展示固定的证据问答占位结果，不生成来源或学术结论。
function onAsk() {
  if (!question.value.trim()) {
    uni.showToast({ title: '请输入研究问题', icon: 'none' })
    return
  }
  answer.value = '演示回复：尚未连接证据库，当前无法提供文献依据。接入后将展示检索结果、来源与引用片段。'
}

// 新增一条示例运行
function onAddRun() {
  addResearchRun()
  uni.showToast({ title: '已新增示例运行', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}
.run-card {
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 24rpx;
  margin: 0 $space-page 20rpx;
}
.run-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.run-name {
  font-size: 28rpx;
  font-weight: 600;
  color: $color-text;
  min-width: 0;
  margin-right: 16rpx;
  word-break: break-all;
}
.run-metrics {
  margin-top: 20rpx;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}
.run-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.run-metric-key {
  font-size: 22rpx;
  color: $color-text-secondary;
}
.run-metric-value {
  margin-top: 6rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: $color-text;
}
.footer-add {
  margin: 8rpx $space-page 40rpx;
}
.question-input {
  height: 88rpx;
  padding: 0 20rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  background-color: $color-bg;
}
.answer {
  display: block;
  margin-top: 20rpx;
}
</style>
