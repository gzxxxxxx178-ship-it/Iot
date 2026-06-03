<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import { useWebSocket } from '../composables/useWebSocket'
import { getDashboardStats } from '../api/dashboard'
import { getHistoryData } from '../api/device'
import { formatTime, formatDecimal } from '../utils/format'
import TempHumChart from '../components/charts/TempHumChart.vue'
import StatusPie from '../components/charts/StatusPie.vue'

const router = useRouter()

const stats = ref({ deviceCount: 0, onlineCount: 0, avgTemp: '--', avgHum: '--' })
const pieData = ref([])
const timeLabels = ref([])
const tempSeries = ref([])
const humSeries = ref([])
const latestWater = ref('--')
const latestRssi = ref('--')
const latestLinkage = ref(null)
const currentTime = ref('')
let clockTimer = null

const ws = useWebSocket((item) => {
  timeLabels.value.push(formatTime(item.serverReceivedTime || item.timestamp))
  tempSeries.value.push(item.temperature)
  humSeries.value.push(item.humidity)
  latestWater.value = item.water ?? '--'
  latestRssi.value = item.rssi ?? '--'
  latestLinkage.value = item.linkage
  if (timeLabels.value.length > 60) {
    timeLabels.value.shift()
    tempSeries.value.shift()
    humSeries.value.shift()
  }
})

function updateClock() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

onMounted(async () => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)

  try {
    const [s, h] = await Promise.allSettled([getDashboardStats(), getHistoryData()])
    if (s.status === 'fulfilled') stats.value = s.value
    if (h.status === 'fulfilled') {
      h.value.reverse().forEach((item) => {
        timeLabels.value.push(formatTime(item.serverReceivedTime || item.timestamp))
        tempSeries.value.push(item.temperature)
        humSeries.value.push(item.humidity)
      })
    }
  } catch {}

  pieData.value = [
    { value: stats.value.onlineCount, name: '在线', itemStyle: { color: '#10b981' } },
    { value: (stats.value.deviceCount - stats.value.onlineCount) || 0, name: '离线', itemStyle: { color: '#ef4444' } },
  ]
})

onUnmounted(() => clearInterval(clockTimer))
</script>

<template>
  <div class="screen">
    <div class="screen-bg"></div>

    <div class="screen-header">
      <h1>智慧农业数据大屏</h1>
      <div class="header-right">
        <span class="clock">{{ currentTime }}</span>
        <el-button circle :icon="Close" size="small" @click="router.push('/dashboard')" />
      </div>
    </div>

    <div class="screen-body">
      <div class="col col-side">
        <div class="panel">
          <div class="panel-title">设备总览</div>
          <div class="stat-row">
            <div class="stat-item">
              <div class="stat-num" style="color: var(--color-blue)">{{ stats.deviceCount }}</div>
              <div class="stat-label">设备总数</div>
            </div>
            <div class="stat-item">
              <div class="stat-num" style="color: var(--color-green)">{{ stats.onlineCount }}</div>
              <div class="stat-label">在线设备</div>
            </div>
          </div>
        </div>
        <div class="panel flex-1">
          <div class="panel-title">设备状态分布</div>
          <StatusPie :data="pieData" />
        </div>
        <div class="panel">
          <div class="panel-title">设备状态</div>
          <div class="info-row">
            <span>联动状态</span>
            <el-tag :type="latestLinkage ? 'success' : 'info'" size="small" effect="dark">
              {{ latestLinkage === null ? '--' : latestLinkage ? '已联动' : '未联动' }}
            </el-tag>
          </div>
          <div class="info-row">
            <span>信号强度</span>
            <strong>{{ latestRssi }} dBm</strong>
          </div>
        </div>
      </div>

      <div class="col col-main">
        <div class="panel flex-1">
          <div class="panel-title">实时传感器数据</div>
          <TempHumChart :timeLabels="timeLabels" :tempSeries="tempSeries" :humSeries="humSeries" height="calc(100% - 40px)" />
        </div>
        <div class="gauge-row">
          <div class="panel gauge-panel">
            <div class="panel-title">实时温度</div>
            <div class="big-num" style="color: var(--color-green)">
              {{ formatDecimal(tempSeries.value.at(-1)) }}<small>°C</small>
            </div>
          </div>
          <div class="panel gauge-panel">
            <div class="panel-title">实时湿度</div>
            <div class="big-num" style="color: var(--color-blue)">
              {{ formatDecimal(humSeries.value.at(-1)) }}<small>%</small>
            </div>
          </div>
          <div class="panel gauge-panel">
            <div class="panel-title">水位</div>
            <div class="big-num" style="color: var(--color-yellow)">
              {{ formatDecimal(latestWater) }}<small>cm</small>
            </div>
          </div>
          <div class="panel gauge-panel">
            <div class="panel-title">信号</div>
            <div class="big-num" style="color: var(--color-purple)">
              {{ latestRssi }}<small>dBm</small>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.screen {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  position: relative;
  background: #0a0f1a;
  display: flex;
  flex-direction: column;
}

.screen-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 30%, rgba(16, 185, 129, 0.06), transparent 50%),
    radial-gradient(ellipse at 80% 60%, rgba(59, 130, 246, 0.06), transparent 50%);
  pointer-events: none;
}

.screen-header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2rem;
  height: 70px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.screen-header h1 {
  font-size: 1.6rem;
  letter-spacing: 2px;
  margin: 0;
  background: linear-gradient(90deg, var(--color-green), var(--color-blue));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

.clock {
  font-size: 1.1rem;
  color: var(--text-secondary);
  font-family: 'Courier New', monospace;
}

.screen-body {
  flex: 1;
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  position: relative;
  z-index: 1;
  min-height: 0;
}

.col {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.col-side {
  width: 300px;
  flex-shrink: 0;
}

.col-main {
  flex: 1;
  min-width: 0;
}

.panel {
  background: rgba(20, 30, 48, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: var(--spacing-md);
  backdrop-filter: blur(8px);
}

.panel.flex-1 {
  flex: 1;
}

.panel-title {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.stat-row {
  display: flex;
  gap: var(--spacing-md);
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 2rem;
  font-weight: 700;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-top: 0.25rem;
}

.gauge-row {
  display: flex;
  gap: var(--spacing-md);
}

.gauge-panel {
  flex: 1;
  text-align: center;
}

.big-num {
  font-size: 1.8rem;
  font-weight: 700;
  margin-top: 0.5rem;
}

.big-num small {
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.info-row:not(:last-child) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.info-row strong {
  color: var(--text-primary);
}
</style>
