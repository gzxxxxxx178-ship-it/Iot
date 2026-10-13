<template>
  <view class="chat-page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="AI 助手" subtitle="本地固定回复" />

    <!-- 消息列表 -->
    <scroll-view class="msg-list" scroll-y :scroll-into-view="lastId">
      <view v-for="(msg, index) in store.chatMessages" :key="index" :id="'msg-' + index" class="msg-row" :class="msg.role === 'user' ? 'msg-right' : 'msg-left'">
        <view class="bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-assistant'">
          <text class="bubble-text">{{ msg.text }}</text>
        </view>
      </view>
      <view class="msg-end" />
    </scroll-view>

    <!-- 输入栏 -->
    <view class="input-bar">
      <input v-model="inputText" class="input-box" confirm-type="send" placeholder="输入问题（演示：温度/水位/报警）" placeholder-class="ph" @confirm="onSend" />
      <view class="send-btn" @tap="onSend"><text class="send-btn-text">发送</text></view>
    </view>
  </view>
</template>

<script setup>
// AI 助手页：本地固定回复的演示问答，不调用任何后端/LLM
import { ref, computed } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { useDemoStore, sendChat } from '../../utils/demo-store.js'

const store = useDemoStore()

// 输入框文本
const inputText = ref('')

// 最后一条消息的锚点 id（用于自动滚动到底部）
const lastId = computed(() => 'msg-' + (store.chatMessages.length - 1))

// 发送消息：调用本地固定回复并清空输入框
function onSend() {
  const text = inputText.value.trim()
  if (!text) return
  sendChat(text)
  inputText.value = ''
}
</script>

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--window-top, 0px));
}
.msg-list {
  flex: 1;
  min-height: 0;
  padding: 0 $space-page;
}
.msg-row {
  display: flex;
  flex-direction: row;
  margin-bottom: 20rpx;
}
.msg-right {
  justify-content: flex-end;
}
.msg-left {
  justify-content: flex-start;
}
.bubble {
  max-width: 76%;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  border: 1rpx solid $color-border;
}
.bubble-user {
  background-color: $color-primary;
  border-color: $color-primary;
}
.bubble-assistant {
  background-color: $color-card;
}
.bubble-text {
  font-size: 28rpx;
  line-height: 1.5;
  color: $color-text;
  word-break: break-all;
}
.bubble-user .bubble-text {
  color: #FFFFFF;
}
.msg-end {
  height: 20rpx;
}
.input-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 16rpx $space-page;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: $color-card;
  border-top: 1rpx solid $color-border;
}
.input-box {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  background-color: $color-bg;
  border: 1rpx solid $color-border;
  border-radius: $radius-pill;
  padding: 0 28rpx;
  font-size: 28rpx;
  color: $color-text;
  margin-right: 16rpx;
}
.ph {
  color: $color-text-light;
}
.send-btn {
  padding: 16rpx 36rpx;
  background-color: $color-primary;
  border-radius: $radius-pill;
}
.send-btn-text {
  font-size: 28rpx;
  color: #FFFFFF;
}
</style>
