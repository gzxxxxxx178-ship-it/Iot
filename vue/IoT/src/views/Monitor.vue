<script setup>
import { ref, computed, onMounted } from 'vue'
import { Connection } from '@element-plus/icons-vue'
import { useWebSocket } from '../composables/useWebSocket'
import { useDeviceStore } from '../stores/device'
import { formatDecimal } from '../utils/format'
import TempHumChart from '../components/charts/TempHumChart.vue'
import GaugeCard from '../components/charts/GaugeCard.vue'
import ControlPanel from '../components/device/ControlPanel.vue'

const deviceStore = useDeviceStore()

// 本地实时指标（结合 WebSocket + deviceStore）
const latestRssi = ref('--')
const latestLinkage = ref(null)
const latestSendCount = ref('--')

// 计算最新值用于 GaugeCard 展示
const latestTemp = computed(() => {
  const arr = deviceStore.tempSeries
  return arr.length ? arr[arr.length - 1] : '--'
})
const latestHum = computed(() => {
  const arr = deviceStore.humSeries
  return arr.length ? arr[arr.length - 1] : '--'
})
const latestWater = computed(() => deviceStore.latest?.water ?? '--')

const linkageText = computed(() => {
  if (latestLinkage.value === null) return '--'
  return latestLinkage.value ? '已联动' : '未联动'
})

// 处理每条 WebSocket/历史数据，追加到 deviceStore
function processDataPoint(item) {
  deviceStore.addDataPoint(item)
  latestRssi.value = item.rssi ?? '--'
  latestLinkage.value = item.linkage
  latestSendCount.value = item.sendCount ?? '--'
}

// 建立 WebSocket 实时连接
const ws = useWebSocket(processDataPoint)

// WebSocket 连接状态中文标签
const connectionLabel = computed(() => {
  switch (ws.status.value) {
    case 'online': return '已连接'
    case 'offline': return '已断开'
    default: return '连接中'
  }
})

// 挂载时先加载 deviceStore 历史数据作为初始展示
onMounted(async () => {
  await deviceStore.fetchHistory()
})
</script>

<template>
  <div class="monitor">
    <div class="page-header">
      <div>
        <h1>实时监控</h1>
        <p>WebSocket 实时接收设备传感器数据</p>
      </div>
      <!-- WebSocket 连接状态指示 -->
      <el-tag
        :type="ws.status.value === 'online' ? 'success' : 'danger'"
        effect="dark"
        size="small"
      >
        <el-icon :size="12" style="margin-right:4px"><Connection /></el-icon>
        {{ connectionLabel }}
      </el-tag>
    </div>

    <!-- 四个实时指标卡片 -->
    <div class="gauge-grid">
      <GaugeCard label="实时温度" :value="formatDecimal(latestTemp)" unit="°C" color="var(--color-green)" />
      <GaugeCard label="实时湿度" :value="formatDecimal(latestHum)" unit="%" color="var(--color-blue)" />
      <GaugeCard label="水位" :value="latestWater" unit="" color="var(--color-yellow)" />
      <GaugeCard label="信号强度" :value="latestRssi" unit="dBm" color="var(--color-purple)" />
    </div>

    <div class="monitor-grid">
      <div class="chart-col">
        <TempHumChart :timeLabels="deviceStore.timeLabels" :tempSeries="deviceStore.tempSeries" :humSeries="deviceStore.humSeries" height="350px" />
      </div>
      <div class="control-col">
        <div class="info-cards">
          <el-card class="info-card">
            <span class="info-label">数据点数</span>
            <span class="info-value">{{ deviceStore.dataPoints.length }}</span>
          </el-card>
          <el-card class="info-card">
            <span class="info-label">发送次数</span>
            <span class="info-value">{{ latestSendCount }}</span>
          </el-card>
          <el-card class="info-card">
            <span class="info-label">联动</span>
            <el-tag :type="latestLinkage ? 'success' : 'info'">{{ linkageText }}</el-tag>
          </el-card>
        </div>
        <ControlPanel style="margin-top: 1rem;" @command-sent="(cmd) => {}" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}
.page-header h1 { font-size: 1.5rem; margin: 0; }
.page-header p { color: var(--text-secondary); margin: 0.25rem 0 0; font-size: 0.9rem; }

.gauge-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
  margin-bottom: 1.5rem;
}
@media (max-width: 768px) { .gauge-grid { grid-template-columns: repeat(2, 1fr); } }

.monitor-grid {
  display: grid;
  grid-template-columns: 1fr 260px;
  gap: var(--spacing-md);
}
@media (max-width: 900px) { .monitor-grid { grid-template-columns: 1fr; } }

.chart-col {
  background: rgba(30, 41, 59, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 1rem;
}

.control-col {
  display: flex;
  flex-direction: column;
}

.info-cards {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.info-card {
  background: rgba(30, 41, 59, 0.5) !important;
  border-color: rgba(255, 255, 255, 0.06) !important;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label { color: var(--text-muted); font-size: 0.8rem; }
.info-value { font-size: 1.3rem; font-weight: 600; }
</style>
