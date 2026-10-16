<template>
  <view class="page chat-page">
    <PageHeader title="AI 助手" subtitle="智慧农业问题咨询" />

    <view v-if="!session.token" class="card login-card">
      <EmptyState text="登录后使用 AI 助手并保存会话记录" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>
    <template v-else>
      <view class="toolbar">
        <text class="hint">对话由后端 AI 服务生成</text>
        <button class="btn-secondary clear-button" :disabled="historyLoading || sending || !hasConversation" @tap="confirmClear">清除历史</button>
      </view>
      <view v-if="historyError" class="card error-card">
        <text class="error-text">{{ historyError }}</text>
        <button class="btn-secondary retry-button" @tap="loadHistory">重试</button>
      </view>
      <view v-if="historyLoading" class="card loading-card"><text class="hint">正在加载会话…</text></view>

      <scroll-view v-if="!historyLoading" class="conversation card" scroll-y :scroll-into-view="scrollTarget" scroll-with-animation>
        <view v-for="(message, index) in messages" :id="'message-' + index" :key="message.id" class="message-row" :class="message.role">
          <view class="message-bubble">
            <text class="message-role">{{ message.role === 'user' ? '我' : 'AI 助手' }}</text>
            <text class="message-content">{{ message.content }}</text>
          </view>
        </view>
        <view v-if="sending" id="sending-indicator" class="message-row assistant">
          <view class="message-bubble"><text class="message-role">AI 助手</text><text class="message-content hint">正在生成回复…</text></view>
        </view>
      </scroll-view>

      <view v-if="sendError" class="send-error">
        <text class="error-text">{{ sendError }}</text>
        <text class="retry-text" @tap="retryLastMessage">重试发送</text>
      </view>
      <view class="composer card">
        <textarea v-model="draft" class="composer-input" maxlength="8000" auto-height :disabled="sending" placeholder="输入农业物联网相关问题…" />
        <view class="composer-footer">
          <text class="hint">{{ draft.length }}/8000</text>
          <button class="btn-primary send-button" :disabled="sending || !draft.trim()" @tap="handleSend">{{ sending ? '生成中…' : '发送' }}</button>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import { request } from '../../api/request.js'
import { session } from '../../utils/session.js'

const welcomeMessage = '你好，我是智慧农业 AI 助手。你可以询问传感器读数、设备状态或农业物联网问题。请结合现场条件核实建议后再执行。'
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const historyLoading = ref(false)
const historyError = ref('')
const sendError = ref('')
const scrollTarget = ref('')
const sessionId = ref('')
const hasConversation = computed(() => messages.value.some((message) => message.role === 'user'))

// 页面显示时加载当前账号的远程聊天记录。
onShow(() => { if (session.token) loadHistory() })

// 会话身份变化时清空旧账号消息，避免跨账号展示。
watch(() => session.generation, () => {
  messages.value = []
  draft.value = ''
  sessionId.value = ''
  historyError.value = ''
  sendError.value = ''
  sending.value = false
  historyLoading.value = false
  if (session.token) loadHistory()
})

// 登录成功时为当前账号加载独立会话。
watch(() => session.token, (token) => { if (token) loadHistory() })

// 获取账号专属的本地会话标识并创建新标识（仅保存会话ID，不保存认证信息）。
function getOrCreateSessionId() {
  const key = 'miniapp.chat.session.' + session.username
  let id = uni.getStorageSync(key)
  if (typeof id !== 'string' || !id) {
    id = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 14)
    uni.setStorageSync(key, id)
  }
  return id
}

// 加载当前账号当前会话的真实聊天历史，并区分空会话与请求失败。
async function loadHistory() {
  if (!session.token) return
  const generation = session.generation
  historyLoading.value = true
  historyError.value = ''
  sendError.value = ''
  try {
    sessionId.value = getOrCreateSessionId()
    const result = await request('/api/chat/history', { params: { sessionId: sessionId.value } })
    messages.value = Array.isArray(result) && result.length
      ? result.map((item, index) => ({ id: String(item.id || index), role: item.role, content: item.content }))
      : [{ id: 'welcome', role: 'assistant', content: welcomeMessage }]
    scrollToLatest()
  } catch (err) {
    if (!err?.stale) historyError.value = err.message || '聊天记录加载失败'
  } finally {
    if (generation === session.generation) historyLoading.value = false
  }
}

// 发送新消息并将最近对话交给后端 AI 服务。
async function handleSend() {
  const content = draft.value.trim()
  if (!content || sending.value || !session.token) return
  if (content.length > 8000) return uni.showToast({ title: '消息不能超过8000个字符', icon: 'none' })
  if (!sessionId.value) sessionId.value = getOrCreateSessionId()
  messages.value.push({ id: 'user-' + Date.now(), role: 'user', content })
  draft.value = ''
  sendError.value = ''
  scrollToLatest()
  await sendConversation()
}

// 发送当前最近50条有效消息并追加真实 AI 回复。
async function sendConversation() {
  if (sending.value) return
  const generation = session.generation
  const conversation = messages.value
    .filter((message) => ['user', 'assistant'].includes(message.role) && message.content)
    .slice(-50)
    .map((message) => ({ role: message.role, content: message.content }))
  if (!conversation.length || conversation[conversation.length - 1].role !== 'user') return
  sending.value = true
  sendError.value = ''
  try {
    const reply = await request('/api/chat', { method: 'POST', data: { sessionId: sessionId.value, messages: conversation } })
    if (!reply || reply.role !== 'assistant' || typeof reply.content !== 'string') throw new Error('AI 服务返回内容不完整')
    messages.value.push({ id: 'assistant-' + Date.now(), role: reply.role, content: reply.content })
    scrollToLatest()
  } catch (err) {
    if (!err?.stale) sendError.value = err.message || '消息发送失败'
  } finally {
    if (generation === session.generation) sending.value = false
  }
}

// 重发最后一条未获得回复的用户消息。
function retryLastMessage() { sendConversation() }

// 请求滚动到最近一条会话消息。
function scrollToLatest() {
  const index = messages.value.length - 1
  if (index >= 0) scrollTarget.value = 'message-' + index
}

// 清空前确认并删除当前账号会话在后端保存的消息。
function confirmClear() {
  uni.showModal({ title: '清除聊天记录', content: '将删除当前会话保存在服务器上的聊天记录，是否继续？', confirmText: '清除', success: (result) => { if (result.confirm) clearHistory() } })
}

// 删除当前会话的远程历史并重置欢迎状态。
async function clearHistory() {
  if (!session.token || !sessionId.value) return
  const generation = session.generation
  historyLoading.value = true
  try {
    await request('/api/chat/history', { method: 'DELETE', params: { sessionId: sessionId.value } })
    messages.value = [{ id: 'welcome', role: 'assistant', content: welcomeMessage }]
    sendError.value = ''
    uni.showToast({ title: '聊天记录已清除', icon: 'success' })
  } catch (err) {
    if (!err?.stale) uni.showToast({ title: err.message || '清除聊天记录失败', icon: 'none' })
  } finally {
    if (generation === session.generation) historyLoading.value = false
  }
}

// 打开账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style lang="scss" scoped>
.chat-page { display: flex; min-height: calc(100vh - 100rpx); flex-direction: column; }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-bottom: 14rpx; }
.clear-button { min-width: 132rpx; height: 56rpx; line-height: 56rpx; margin: 0; padding: 0 14rpx; font-size: 22rpx; }
.conversation { box-sizing: border-box; height: 54vh; min-height: 360rpx; padding: 20rpx; overflow: hidden; }
.message-row { display: flex; width: 100%; margin-bottom: 18rpx; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }
.message-bubble { display: flex; max-width: 88%; flex-direction: column; gap: 8rpx; padding: 16rpx 18rpx; border: 1rpx solid $color-border; border-radius: 16rpx; background: #fff; }
.message-row.user .message-bubble { border-color: $color-primary-soft; background: $color-primary-soft; }
.message-role { color: $color-text-secondary; font-size: 20rpx; }
.message-content { color: $color-text; font-size: 25rpx; line-height: 1.55; white-space: pre-wrap; word-break: break-word; }
.composer { margin-top: 14rpx; padding: 16rpx; }
.composer-input { box-sizing: border-box; width: 100%; min-height: 84rpx; max-height: 240rpx; color: $color-text; font-size: 26rpx; line-height: 1.5; }
.composer-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 12rpx; padding-top: 12rpx; border-top: 1rpx solid $color-border; }
.send-button { min-width: 138rpx; height: 64rpx; line-height: 64rpx; margin: 0; padding: 0 20rpx; font-size: 24rpx; }
.send-error { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-top: 12rpx; padding: 14rpx 16rpx; border-radius: 12rpx; background: $color-red-soft; }
.error-text { color: $color-red; font-size: 22rpx; }
.retry-text { flex: none; color: $color-primary; font-size: 23rpx; }
.error-card { display: flex; flex-direction: column; align-items: flex-start; gap: 14rpx; }
.retry-button { align-self: flex-end; }
.loading-card { padding: 28rpx; }
.login-card { margin-top: 12rpx; }
.action-button { width: 100%; margin-top: 20rpx; }
</style>
