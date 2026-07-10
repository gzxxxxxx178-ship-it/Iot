<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { sendMessage, getHistory, clearHistory as clearRemoteHistory } from '../api/chat'
import { ChatDotRound, Delete } from '@element-plus/icons-vue'

// 默认欢迎消息
const defaultMessages = [
  {
    role: 'assistant',
    content:
      '你好！我是智慧农业 AI 助手。\n\n我可以帮你：\n- 分析传感器数据趋势\n- 建议灌溉和温控策略\n- 解读设备状态\n- 解答农业 IoT 相关问题\n\n请随时提问！',
  },
]

const messages = ref([...defaultMessages])

// 加载历史消息，覆盖默认欢迎消息
onMounted(async () => {
  try {
    const history = await getHistory()
    if (history && history.length) {
      messages.value = history.map((m) => ({ role: m.role, content: m.content }))
    }
  } catch {}
})

// 清除远程聊天记录，重置为默认欢迎消息
async function clearHistory() {
  try {
    await clearRemoteHistory()
  } catch {}
  messages.value = [...defaultMessages]
}

const inputText = ref('')
const loading = ref(false)
const chatBodyRef = ref(null)

// 发送消息：追加用户消息 → POST /api/chat → 追加 AI 回复
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

// 滚动到消息列表底部
function scrollToBottom() {
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

// Enter 发送，Shift+Enter 换行
function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="chat-page">
    <div class="page-header">
      <div>
        <h1>AI 助手</h1>
        <p>基于 DeepSeek 的智慧农业 AI 对话</p>
      </div>
      <el-button text :icon="Delete" @click="clearHistory">清除历史</el-button>
    </div>

    <el-card class="chat-card">
      <!-- 消息列表区域 -->
      <div class="chat-body" ref="chatBodyRef">
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="chat-message"
          :class="m.role"
        >
          <span class="msg-avatar">{{ m.role === 'user' ? '👤' : '🤖' }}</span>
          <div class="msg-bubble" v-text="m.content" />
        </div>
        <!-- 加载动画 -->
        <div v-if="loading" class="chat-message assistant">
          <span class="msg-avatar">🤖</span>
          <div class="msg-bubble loading-bubble">
            <span class="dot-pulse" />
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入消息，Enter 发送..."
          @keydown="handleKeydown"
          :disabled="loading"
        />
        <el-button type="primary" :icon="ChatDotRound" :loading="loading" @click="handleSend">
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.chat-page { max-width: 800px; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; }
.page-header h1 { font-size: 1.5rem; margin: 0; }
.page-header p { color: var(--text-secondary); margin: 0.25rem 0 0; font-size: 0.85rem; }

.chat-card { height: calc(100vh - 200px); display: flex; flex-direction: column; }

.chat-body {
  flex: 1; overflow-y: auto; padding: 1rem;
  display: flex; flex-direction: column; gap: 1rem;
  background: rgba(15, 23, 42, 0.3);
}

.chat-message { display: flex; gap: 0.5rem; max-width: 85%; }
.chat-message.user { align-self: flex-end; flex-direction: row-reverse; }
.chat-message.assistant { align-self: flex-start; }

.msg-avatar { font-size: 1.5rem; flex-shrink: 0; }

.msg-bubble {
  padding: 0.65rem 0.9rem; border-radius: 12px; font-size: 0.9rem; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
}
.chat-message.user .msg-bubble { background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.2); }
.chat-message.assistant .msg-bubble { background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(255, 255, 255, 0.06); }

.chat-input-area {
  display: flex; gap: 0.5rem; padding: 0.75rem 1rem;
  border-top: 1px solid var(--border-color);
  align-items: flex-end;
}
.chat-input-area .el-textarea { flex: 1; }

.loading-bubble { display: flex; align-items: center; min-width: 40px; min-height: 20px; }
.dot-pulse { width: 6px; height: 6px; background: var(--text-muted); border-radius: 50%; display: block; animation: pulse 0.8s infinite alternate; }
@keyframes pulse { to { opacity: 0.3; transform: scale(0.8); } }
</style>
