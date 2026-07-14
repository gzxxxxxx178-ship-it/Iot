<script setup>
import { ref, onMounted } from 'vue'
import { useDeviceStore } from '../stores/device'
import { getDeviceStatusDistribution } from '../api/dashboard'
import GaugeCard from '../components/charts/GaugeCard.vue'
import TempHumChart from '../components/charts/TempHumChart.vue'
import StatusPie from '../components/charts/StatusPie.vue'

const deviceStore = useDeviceStore()

// 仪表盘本地统计数据（基于 deviceStore 数据计算）
const stats = ref({ deviceCount: 0, onlineCount: 0, avgTemp: '--', avgHum: '--', avgWater: '--', latestRssi: '--', latestLinkage: null })
const pieData = ref([])
const recentMessages = ref(0)

// 加载服务端统计与历史数据并填充仪表盘
async function loadDashboard() {
  await Promise.all([deviceStore.fetchHistory(), deviceStore.fetchStats()])
  const history = deviceStore.dataPoints
  const backendStats = deviceStore.stats

  stats.value.deviceCount = backendStats.deviceCount ?? 0
  stats.value.onlineCount = backendStats.onlineCount ?? 0
  stats.value.avgTemp = Number.isFinite(backendStats.avgTemp) ? backendStats.avgTemp.toFixed(1) : '--'
  stats.value.avgHum = Number.isFinite(backendStats.avgHum) ? backendStats.avgHum.toFixed(1) : '--'

  if (history.length) {
    const waters = history.map((h) => h.water).filter(Number.isFinite)

    // 计算尚未由后端提供的辅助指标
    stats.value.avgWater = waters.length ? (waters.reduce((a, b) => a + b, 0) / waters.length).toFixed(1) : '--'
    stats.value.latestRssi = deviceStore.latest?.rssi ?? '--'
    stats.value.latestLinkage = deviceStore.latest?.linkage ?? null
    recentMessages.value = history.reduce((sum, h) => sum + (h.sendCount || 0), 0)
  }

  try {
    const distribution = await getDeviceStatusDistribution()
    pieData.value = distribution.map((item) => ({
      ...item,
      itemStyle: { color: item.name === '在线' ? '#10b981' : '#ef4444' },
    }))
  } catch {
    // 状态分布接口异常时使用统计接口结果降级展示
    pieData.value = [
      { value: stats.value.onlineCount, name: '在线', itemStyle: { color: '#10b981' } },
      { value: stats.value.deviceCount - stats.value.onlineCount, name: '离线', itemStyle: { color: '#ef4444' } },
    ]
  }
}

// 页面挂载时加载仪表盘数据
onMounted(loadDashboard)
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
		<div class="chart-col">
		  <TempHumChart :timeLabels="deviceStore.timeLabels" :tempSeries="deviceStore.tempSeries" :humSeries="deviceStore.humSeries" height="300px" />
		</div>
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
  grid-template-columns: 1050px 1fr;
  gap: var(--spacing-md);
}
@media (max-width: 768px) { .dashboard-grid { grid-template-columns: 1fr; } }

.stats-col { display: flex; flex-direction: column; gap: var(--spacing-md); }
.stat-card { background: rgba(30, 41, 59, 0.5) !important; border-color: rgba(255, 255, 255, 0.06) !important; display: flex; flex-direction: column; gap: 0.25rem; }
.stat-label { color: var(--text-muted); font-size: 0.8rem; }
.stat-value { font-size: 1.5rem; font-weight: 600; }
</style>
