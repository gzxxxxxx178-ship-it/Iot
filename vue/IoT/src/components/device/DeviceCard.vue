<script setup>
import { formatDateTime } from '../../utils/format'

// 设备信息卡片：显示状态灯 + 设备 ID/温度/湿度/水位/信号/联动/最后在线
defineProps({
  device: { type: Object, default: () => ({}) },
})
</script>

<template>
  <el-card class="device-card" shadow="hover">
    <div class="device-card-header">
      <!-- 在线状态指示灯：绿色在线，红色离线 -->
      <div class="device-status-dot" :class="{ online: device.status === 'online' }"></div>
      <h3>{{ device.name || device.deviceId || '未知设备' }}</h3>
    </div>
    <div class="device-card-body">
      <div class="device-row"><span>设备 ID</span><span>{{ device.deviceId }}</span></div>
      <div class="device-row"><span>温度</span><span>{{ device.temperature ?? '--' }}°C</span></div>
      <div class="device-row"><span>湿度</span><span>{{ device.humidity ?? '--' }}%</span></div>
      <div class="device-row"><span>水位</span><span>{{ device.water ?? '--' }} cm</span></div>
      <div class="device-row"><span>信号</span><span>{{ device.rssi ?? '--' }} dBm</span></div>
      <div class="device-row">
        <span>联动</span>
        <el-tag :type="device.linkage ? 'success' : 'info'" size="small" effect="dark">
          {{ device.linkage ? 'ON' : 'OFF' }}
        </el-tag>
      </div>
      <div class="device-row"><span>最后在线</span><span>{{ device.lastSeen || formatDateTime(device.serverReceivedTime) || '--' }}</span></div>
    </div>
  </el-card>
</template>

<style scoped>
.device-card {
  background: rgba(30, 41, 59, 0.5) !important;
  border-color: rgba(255, 255, 255, 0.06) !important;
}

.device-card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.device-card-header h3 {
  margin: 0;
  font-size: 1rem;
}

.device-status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.5);
}

.device-status-dot.online {
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
}

.device-card-body {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.device-row {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.device-row span:last-child {
  color: var(--text-primary);
}

.device-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border-color);
}
</style>
