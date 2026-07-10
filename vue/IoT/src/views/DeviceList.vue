<script setup>
import { ref, onMounted } from 'vue'
import { getDeviceList } from '../api/device'
import DeviceCard from '../components/device/DeviceCard.vue'

const devices = ref([])

// 加载所有设备的列表和最新读数
async function fetchDevices() {
  try {
    devices.value = await getDeviceList()
  } catch {}
}

onMounted(fetchDevices)
</script>

<template>
  <div class="device-list">
    <div class="page-header">
      <div>
        <h1>设备管理</h1>
        <p>当前系统中的 IoT 设备及其最新数据</p>
      </div>
      <el-tag type="info" effect="dark" size="small">{{ devices.length }} 台设备</el-tag>
    </div>

    <!-- 设备卡片网格，响应式列宽 -->
    <div v-if="devices.length" class="device-grid">
      <DeviceCard v-for="d in devices" :key="d.deviceId" :device="d" />
    </div>
    <el-empty v-else description="暂无设备数据" />
  </div>
</template>

<style scoped>
.device-list {
  max-width: 1200px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--spacing-xl);
}

.page-header h1 {
  font-size: 1.5rem;
  margin: 0;
}

.page-header p {
  color: var(--text-secondary);
  margin: 0.25rem 0 0;
  font-size: 0.9rem;
}

.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}
</style>
