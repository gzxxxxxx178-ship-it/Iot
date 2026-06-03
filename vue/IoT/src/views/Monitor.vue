<script setup>
import { ref, computed, onMounted } from 'vue'
import { VideoPlay, VideoPause, Connection } from '@element-plus/icons-vue'
import { useWebSocket } from '../composables/useWebSocket'
import { getHistoryData } from '../api/device'
import { formatTime, formatDecimal } from '../utils/format'
import TempHumChart from '../components/charts/TempHumChart.vue'
import GaugeCard from '../components/charts/GaugeCard.vue'
import ControlPanel from '../components/device/ControlPanel.vue'

const timeLabels = ref([])
const tempSeries = ref([])
const humSeries = ref([])
const waterSeries = ref([])
const latestRssi = ref('--')
const latestLinkage = ref(null)
const latestSendCount = ref('--')

const latestTemp = computed(() => tempSeries.value.at(-1) ?? '--')
const latestHum = computed(() => humSeries.value.at(-1) ?? '--')
const latestWater = computed(() => waterSeries.value.at(-1) ?? '--')

const linkageText = computed(() => {
  if (latestLinkage.value === null) return '--'
  return latestLinkage.value ? '已联动' : '未联动'
})

function processDataPoint(item) {
  const time = formatTime(item.serverReceivedTime || item.timestamp)
  timeLabels.value.push(time)
  tempSeries.value.push(item.temperature)
  humSeries.value.push(item.humidity)
  waterSeries.value.push(item.water)
  latestRssi.value = item.rssi ?? '--'
  latestLinkage.value = item.linkage
  latestSendCount.value = item.sendCount ?? '--'

  if (timeLabels.value.length > 50) {
    timeLabels.value.shift()
    tempSeries.value.shift()
    humSeries.value.shift()
    waterSeries.value.shift()
  }
}

const ws = useWebSocket(processDataPoint)

const connectionLabel = computed(() => {
  switch (ws.status.value) {
    case 'online': return '已连接'
    case 'offline': return '已断开'
    default: return '连接中'
  }
})

onMounted(async () => {
  try {
    const data = await getHistoryData()
    data.reverse().forEach(processDataPoint)
  } catch {}
})
</script>

<template>
  <div class="monitor">
    <div class="page-header">
      <div>
        <h1>实时监控</h1>
        <p>WebSocket 实时接收设备传感器数据</p>
      </div>
      <el-tag
        :type="ws.status.value === 'online' ? 'success' : ws.status.value === 'offline' ? 'danger' : 'warning'"
        effect="dark"
        size="small"
      >
        <el-icon :size="12"><Connection /></el-icon>
        {{ connectionLabel }}
      </el-tag>
    </div>

    <div class="gauge-grid">
      <GaugeCard label="实时温度" :value="formatDecimal(latestTemp)" unit="°C" color="var(--color-green)" />
      <GaugeCard label="实时湿度" :value="formatDecimal(latestHum)" unit="%" color="var(--color-blue)" />
      <GaugeCard label="水位" :value="formatDecimal(latestWater)" unit="cm" color="var(--color-yellow)" />
      <GaugeCard label="信号强度" :value="latestRssi" unit="dBm" color="var(--color-purple)" />
    </div>

    <div class="status-grid">
      <div class="status-card" :class="{ linked: latestLinkage === true }">
        <span class="status-label">联动状态</span>
        <span class="status-value">{{ linkageText }}</span>
      </div>
      <div class="status-card">
        <span class="status-label">发送计数</span>
        <span class="status-value">{{ latestSendCount }}</span>
      </div>
    </div>

    <div class="monitor-grid">
      <div class="chart-section">
        <TempHumChart :timeLabels="timeLabels" :tempSeries="tempSeries" :humSeries="humSeries" height="400px" />
      </div>
      <div class="control-section">
        <ControlPanel @command-sent="(cmd) => console.log('Command sent:', cmd)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.monitor {
  max-width: 1300px;
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

.gauge-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.status-card {
  background: rgba(30, 41, 59, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 0.9rem 1.2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-card.linked {
  border-color: rgba(16, 185, 129, 0.3);
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.15);
}

.status-label {
  color: var(--text-secondary);
  font-size: 0.8rem;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.status-value {
  font-weight: 600;
  font-size: 1rem;
}

.monitor-grid {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--spacing-md);
  align-items: start;
}

@media (max-width: 900px) {
  .gauge-grid {
    grid-template-columns: 1fr;
  }
  .monitor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
