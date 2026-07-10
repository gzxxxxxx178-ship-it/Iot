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

// 统计数据
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

// WebSocket 实时接收传感器数据，保留最多 60 个数据点
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

// 每秒更新页面时钟
function updateClock() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

// 挂载时加载仪表盘和历史数据，启动时钟
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

// 卸载时清除时钟
onUnmounted(() => clearInterval(clockTimer))
</script>

<template>
  <div class="screen">
    <!-- 顶部信息栏：时钟 + 设备统计 + 关闭按钮 -->
    <div class="screen-header">
      <h1 class="screen-title">智慧农业数据大屏</h1>
      <div class="screen-stats">
        <span>设备总数: {{ stats.deviceCount }}</span>
        <span>在线: {{ stats.onlineCount }}</span>
        <span>{{ currentTime }}</span>
      </div>
      <el-button class="close-btn" :icon="Close" circle @click="router.push('/dashboard')" title="退出大屏" />
    </div>

    <!-- 中部：饼图 + 联动状态 + 信号强度 -->
    <div class="screen-body">
      <div class="screen-left">
        <StatusPie :data="pieData" />
        <div class="info-cards">
          <div class="info-card"><span>联动</span><el-tag :type="latestLinkage ? 'success' : 'danger'" size="large">{{ latestLinkage ? 'ON' : 'OFF' }}</el-tag></div>
          <div class="info-card"><span>信号</span><b>{{ latestRssi }} dBm</b></div>
          <div class="info-card"><span>水位</span><b>{{ latestWater }}</b></div>
        </div>
      </div>
      <!-- 趋势大图 -->
      <div class="screen-chart">
        <TempHumChart :timeLabels="timeLabels" :tempSeries="tempSeries" :humSeries="humSeries" height="400px" />
      </div>
    </div>

    <!-- 底部四个大号指标 -->
    <div class="screen-footer">
      <div class="big-card"><span>平均温度</span><b>{{ formatDecimal(stats.avgTemp) }}°C</b></div>
      <div class="big-card"><span>平均湿度</span><b>{{ formatDecimal(stats.avgHum) }}%</b></div>
      <div class="big-card"><span>在线设备</span><b>{{ stats.onlineCount }}</b></div>
      <div class="big-card"><span>离线设备</span><b>{{ stats.deviceCount - stats.onlineCount }}</b></div>
    </div>
  </div>
</template>

<style scoped>
.screen {
  min-height: 100vh; background: #0b1120; color: #e2e8f0;
  padding: 1.5rem; display: flex; flex-direction: column; gap: 1.5rem;
}

.screen-header {
  display: flex; align-items: center; justify-content: space-between;
  padding-bottom: 1rem; border-bottom: 1px solid rgba(255,255,255,0.06);
}
.screen-title { font-size: 1.8rem; margin: 0; font-weight: 700; letter-spacing: 2px; }
.screen-stats { display: flex; gap: 2rem; font-size: 0.9rem; color: var(--text-secondary); }

.screen-body {
  display: grid; grid-template-columns: 280px 1fr; gap: 1.5rem; flex: 1;
}
@media (max-width: 900px) { .screen-body { grid-template-columns: 1fr; } }

.screen-left { display: flex; flex-direction: column; gap: 1rem; }
.info-cards { display: flex; gap: 1rem; }
.info-card {
  flex: 1; background: rgba(30,41,59,0.6); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px; padding: 1rem; display: flex; flex-direction: column; gap: 0.5rem; text-align: center;
}
.info-card span { font-size: 0.8rem; color: var(--text-muted); }
.info-card b { font-size: 1.3rem; }

.screen-chart {
  background: rgba(30,41,59,0.35); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 16px; padding: 1.5rem;
}

.screen-footer { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; }
@media (max-width: 768px) { .screen-footer { grid-template-columns: repeat(2, 1fr); } }
.big-card {
  background: rgba(30,41,59,0.6); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 16px; padding: 1.5rem; display: flex; flex-direction: column; gap: 0.5rem; text-align: center;
}
.big-card span { font-size: 0.85rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; }
.big-card b { font-size: 2.5rem; font-weight: 700; }
</style>
