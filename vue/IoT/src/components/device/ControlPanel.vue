<script setup>
import { ref } from 'vue'
import { controlDevice } from '../../api/device'
import { VideoPlay, VideoPause } from '@element-plus/icons-vue'

const emit = defineEmits(['command-sent'])
const status = ref('')
const loading = ref(false)

async function sendCommand(cmd) {
  const label = cmd === 'start' ? '启动' : '停止'
  loading.value = true
  status.value = `正在发送 ${label} 指令...`
  try {
    await controlDevice(cmd)
    status.value = `${label} 指令已发送`
    emit('command-sent', cmd)
  } catch (e) {
    status.value = `发送失败: ${e.message}`
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card class="control-panel">
    <template #header>
      <span class="panel-title">设备控制</span>
    </template>
    <div class="control-buttons">
      <el-button
        type="success"
        :icon="VideoPlay"
        :loading="loading"
        @click="sendCommand('start')"
      >
        启动设备
      </el-button>
      <el-button
        type="danger"
        :icon="VideoPause"
        :loading="loading"
        @click="sendCommand('stop')"
      >
        停止设备
      </el-button>
    </div>
    <div v-if="status" class="control-status">
      {{ status }}
    </div>
  </el-card>
</template>

<style scoped>
.control-panel {
  background: rgba(30, 41, 59, 0.5) !important;
  border-color: rgba(255, 255, 255, 0.06) !important;
}

.panel-title {
  font-weight: 600;
  font-size: 0.95rem;
}

.control-buttons {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.control-status {
  margin-top: 1rem;
  padding: 0.5rem 0.75rem;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  font-size: 0.85rem;
  color: var(--text-secondary);
  font-family: 'Courier New', monospace;
}
</style>
