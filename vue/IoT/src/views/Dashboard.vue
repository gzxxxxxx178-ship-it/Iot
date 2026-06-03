<script setup>
import { ref, onMounted } from 'vue'
import { getHistoryData } from '../api/device'
import GaugeCard from '../components/charts/GaugeCard.vue'
import TempHumChart from '../components/charts/TempHumChart.vue'
import StatusPie from '../components/charts/StatusPie.vue'
import { formatTime } from '../utils/format'

const stats = ref({ deviceCount: 0, onlineCount: 0, avgTemp: '--', avgHum: '--', avgWater: '--', latestRssi: '--', latestLinkage: null })
const pieData = ref([])
const timeLabels = ref([])
const tempSeries = ref([])
const humSeries = ref([])
const recentMessages = ref(0)

onMounted(async () => {
  try {
    const history = await getHistoryData()
    if (history.length) {
      const latest = history[history.length - 1]
      const temps = history.map((h) => h.temperature).filter(Boolean)
      const hums = history.map((h) => h.humidity).filter(Boolean)
      const waters = history.map((h) => h.water).filter(Boolean)

      stats.value.avgTemp = temps.length ? (temps.reduce((a, b) => a + b, 0) / temps.length).toFixed(1) : '--'
      stats.value.avgHum = hums.length ? (hums.reduce((a, b) => a + b, 0) / hums.length).toFixed(1) : '--'
      stats.value.avgWater = waters.length ? (waters.reduce((a, b) => a + b, 0) / waters.length).toFixed(1) : '--'
      stats.value.latestRssi = latest.rssi ?? '--'
      stats.value.latestLinkage = latest.linkage
      stats.value.deviceCount = new Set(history.map((h) => h.deviceId)).size
      stats.value.onlineCount = stats.value.deviceCount
      recentMessages.value = history.reduce((sum, h) => sum + (h.sendCount || 0), 0)

      history.reverse().forEach((item) => {
        timeLabels.value.push(formatTime(item.serverReceivedTime || item.timestamp))
        tempSeries.value.push(item.temperature)
        humSeries.value.push(item.humidity)
      })
    }
  } catch {}

  pieData.value = [
    { value: stats.value.onlineCount, name: '在线', itemStyle: { color: '#10b981' } },
    { value: stats.value.deviceCount - stats.value.onlineCount, name: '离线', itemStyle: { color: '#ef4444' } },
  ]
})
</script>

<template>
  <div class="dashboard">
    <div class="page-header">
      <h1>系统概览</h1>
      <p>智慧农业设备监控总览</p>
    </div>

    <div class="gauge-grid">
      <GaugeCard label="平均温度" :value="stats.avgTemp" unit="°C" color="var(--color-green)" />
      <GaugeCard label="平均湿度" :value="stats.avgHum" unit="%" color="var(--color-blue)" />
      <GaugeCard label="平均水位" :value="stats.avgWater" unit="cm" color="var(--color-yellow)" />
      <GaugeCard label="信号强度" :value="stats.latestRssi" unit="dBm" color="var(--color-purple)" />
    </div>

    <div class="dashboard-status-row">
      <div class="db-status-card">
        <span class="db-status-label">设备数量</span>
        <span class="db-status-value">{{ stats.deviceCount }}</span>
      </div>
      <div class="db-status-card">
        <span class="db-status-label">联动状态</span>
        <el-tag :type="stats.latestLinkage ? 'success' : 'info'" size="small" effect="dark">
          {{ stats.latestLinkage === null ? '--' : stats.latestLinkage ? '已联动' : '未联动' }}
        </el-tag>
      </div>
      <div class="db-status-card">
        <span class="db-status-label">累计消息</span>
        <span class="db-status-value">{{ recentMessages }}</span>
      </div>
    </div>

    <div class="dashboard-grid">
      <el-card class="db-chart-card">
        <template #header><span>最近趋势</span></template>
        <TempHumChart :timeLabels="timeLabels" :tempSeries="tempSeries" :humSeries="humSeries" height="320px" />
      </el-card>

      <el-card class="db-pie-card">
        <template #header><span>设备状态分布</span></template>
        <StatusPie :data="pieData" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1200px;
}

.page-header {
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

.gauge-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--spacing-md);
}

.dashboard-status-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.db-status-card {
  background: rgba(30, 41, 59, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 0.9rem 1.2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.db-status-label {
  color: var(--text-secondary);
  font-size: 0.8rem;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.db-status-value {
  font-weight: 600;
  font-size: 1rem;
  color: var(--text-primary);
}

@media (max-width: 900px) {
  .gauge-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  .dashboard-status-row {
    grid-template-columns: 1fr;
  }
}
</style>
