<script setup>
import { ref, onMounted } from 'vue'
import { getHistoryData } from '../api/device'
import GaugeCard from '../components/charts/GaugeCard.vue'
import TempHumChart from '../components/charts/TempHumChart.vue'
import StatusPie from '../components/charts/StatusPie.vue'
import { formatTime } from '../utils/format'

// 仪表盘统计数据
const stats = ref({ deviceCount: 0, onlineCount: 0, avgTemp: '--', avgHum: '--', avgWater: '--', latestRssi: '--', latestLinkage: null })
const pieData = ref([])
const timeLabels = ref([])
const tempSeries = ref([])
const humSeries = ref([])
const recentMessages = ref(0)

// 加载历史数据 → 计算各项统计指标 → 填充图表
onMounted(async () => {
  try {
    const history = await getHistoryData()
    if (history.length) {
      const latest = history[history.length - 1]
      const temps = history.map((h) => h.temperature).filter(Boolean)
      const hums = history.map((h) => h.humidity).filter(Boolean)
      const waters = history.map((h) => h.water).filter(Boolean)

      // 计算平均值
      stats.value.avgTemp = temps.length ? (temps.reduce((a, b) => a + b, 0) / temps.length).toFixed(1) : '--'
      stats.value.avgHum = hums.length ? (hums.reduce((a, b) => a + b, 0) / hums.length).toFixed(1) : '--'
      stats.value.avgWater = waters.length ? (waters.reduce((a, b) => a + b, 0) / waters.length).toFixed(1) : '--'
      stats.value.latestRssi = latest.rssi ?? '--'
      stats.value.latestLinkage = latest.linkage
      stats.value.deviceCount = new Set(history.map((h) => h.deviceId)).size
      stats.value.onlineCount = stats.value.deviceCount
      recentMessages.value = history.reduce((sum, h) => sum + (h.sendCount || 0), 0)

      // 填充趋势图数据（最新数据在末端）
      history.reverse().forEach((item) => {
        timeLabels.value.push(formatTime(item.serverReceivedTime || item.timestamp))
        tempSeries.value.push(item.temperature)
        humSeries.value.push(item.humidity)
      })
    }
  } catch {}

  // 饼图数据
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

    <!-- 四个指标卡片 -->
    <div class="gauge-grid">
      <GaugeCard label="平均温度" :value="stats.avgTemp" unit="°C" color="var(--color-green)" />
      <GaugeCard label="平均湿度" :value="stats.avgHum" unit="%" color="var(--color-blue)" />
      <GaugeCard label="平均水位" :value="stats.avgWater" unit="cm" color="var(--color-yellow)" />
      <GaugeCard label="信号强度" :value="stats.latestRssi" unit="dBm" color="var(--color-purple)" />
    </div>

    <!-- 状态卡片 + 趋势图 -->
    <div class="dashboard-grid">
      <div class="stats-col">
        <el-card class="stat-card">
          <span class="stat-label">设备数量</span>
          <span class="stat-value">{{ stats.deviceCount }}</span>
        </el-card>
        <el-card class="stat-card">
          <span class="stat-label">联动状态</span>
          <el-tag :type="stats.latestLinkage ? 'success' : 'info'">{{ stats.latestLinkage ? 'ON' : 'OFF' }}</el-tag>
        </el-card>
        <el-card class="stat-card">
          <span class="stat-label">消息总数</span>
          <span class="stat-value">{{ recentMessages }}</span>
        </el-card>
      </div>
      <div class="chart-col">
        <TempHumChart :timeLabels="timeLabels" :tempSeries="tempSeries" :humSeries="humSeries" height="300px" />
      </div>
    </div>

    <!-- 饼图 -->
    <div style="margin-top: 1.5rem;">
      <StatusPie :data="pieData" />
    </div>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 1.5rem; }
.page-header h1 { font-size: 1.5rem; margin: 0 0 0.25rem; }
.page-header p { color: var(--text-secondary); margin: 0; font-size: 0.85rem; }

.gauge-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
  margin-bottom: 1.5rem;
}
@media (max-width: 768px) { .gauge-grid { grid-template-columns: repeat(2, 1fr); } }

.dashboard-grid {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: var(--spacing-md);
}
@media (max-width: 768px) { .dashboard-grid { grid-template-columns: 1fr; } }

.stats-col { display: flex; flex-direction: column; gap: var(--spacing-md); }
.stat-card { background: rgba(30, 41, 59, 0.5) !important; border-color: rgba(255, 255, 255, 0.06) !important; display: flex; flex-direction: column; gap: 0.25rem; }
.stat-label { color: var(--text-muted); font-size: 0.8rem; }
.stat-value { font-size: 1.5rem; font-weight: 600; }
</style>
