<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { sendMessage, getHistory, clearHistory as clearRemoteHistory } from '../api/chat'
import { ChatDotRound, Delete } from '@element-plus/icons-vue'

const defaultMessages = [
  {
    role: 'assistant',
    content:
      '你好！我是智慧农业 AI 助手。\n\n我可以帮你：\n- 分析传感器数据趋势\n- 建议灌溉和温控策略\n- 解读设备状态\n- 解答农业 IoT 相关问题\n\n请随时提问！',
  },
]

const messages = ref([...defaultMessages])

onMounted(async () => {
  try {
    const history = await getHistory()
    if (history && history.length) {
      messages.value = history.map((m) => ({ role: m.role, content: m.content }))
    }
  } catch {}
})

async function clearHistory() {
  try {
    await clearRemoteHistory()
  } catch {}
  messages.value = [...defaultMessages]
}

const inputText = ref('')
const loading = ref(false)
const chatBodyRef = ref(null)

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true

  await nextTick()
  scrollToBottom()

  try {
    const history = messages.value.map((m) => ({ role: m.role, content: m.content }))
    const reply = await sendMessage(history)
    messages.value.push(reply)
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '请求失败：' + (e.message || '未知错误') })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function scrollToBottom() {
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="chat-page">
    <div class="chat-container">
      <div class="chat-header">
        <el-icon :size="20"><ChatDotRound /></el-icon>
        <span>AI 智能助手</span>
        <el-tag size="small" effect="dark" type="info">DeepSeek V4 Pro</el-tag>
        <el-button text size="small" @click="clearHistory" title="清空对话">
          <el-icon :size="16"><Delete /></el-icon>
        </el-button>
      </div>

      <div class="chat-body" ref="chatBodyRef">
        <div
          v-for="(msg, i) in messages"
          :key="i"
          class="chat-message"
          :class="msg.role"
        >
          <div class="message-bubble">
            <pre class="message-text">{{ msg.content }}</pre>
          </div>
        </div>

        <div v-if="loading" class="chat-message assistant">
          <div class="message-bubble loading">
            <span class="dot-pulse"></span>
          </div>
        </div>
      </div>

      <div class="chat-footer">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入你的问题，按 Enter 发送..."
          @keydown="handleKeydown"
          resize="none"
          :disabled="loading"
        />
        <el-button
          type="success"
          :icon="ChatDotRound"
          circle
          class="send-btn"
          @click="handleSend"
          :loading="loading"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  justify-content: center;
}

.chat-container {
  width: 100%;
  max-width: 800px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px - var(--spacing-xl) * 2);
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.15);
}

.chat-header span {
  font-weight: 600;
  flex: 1;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.chat-message {
  display: flex;
  max-width: 85%;
}

.chat-message.user {
  align-self: flex-end;
}

.chat-message.assistant {
  align-self: flex-start;
}

.message-bubble {
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
  line-height: 1.6;
}

.chat-message.user .message-bubble {
  background: var(--color-green);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-message.assistant .message-bubble {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border-color);
  border-bottom-left-radius: 4px;
}

.message-text {
  margin: 0;
  font-family: inherit;
  font-size: 0.9rem;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-bubble.loading {
  padding: var(--spacing-md) var(--spacing-lg);
  display: flex;
  align-items: center;
}

.dot-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-secondary);
  animation: pulse 1.2s infinite ease-in-out;
}

@keyframes pulse {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.chat-footer {
  display: flex;
  align-items: flex-end;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.1);
}

.send-btn {
  flex-shrink: 0;
}
</style>
