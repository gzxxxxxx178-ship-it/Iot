<script setup>
import { ref, onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { getSensorHistory } from '../api/device'
import { formatTime, formatDateTime } from '../utils/format'
import TempHumChart from '../components/charts/TempHumChart.vue'

const dateRange = ref([])
const tableData = ref([])
const timeLabels = ref([])
const tempSeries = ref([])
const humSeries = ref([])
const loading = ref(false)

function buildChartData(data) {
  timeLabels.value = []
  tempSeries.value = []
  humSeries.value = []
  data.forEach((item) => {
    timeLabels.value.push(formatTime(item.serverReceivedTime || item.timestamp))
    tempSeries.value.push(item.temperature)
    humSeries.value.push(item.humidity)
  })
}

async function query() {
  if (!dateRange.value?.length) return
  loading.value = true
  try {
    const params = {
      start: dateRange.value[0].toISOString(),
      end: dateRange.value[1].toISOString(),
    }
    const data = await getSensorHistory(params)
    tableData.value = data
    buildChartData(data)
  } catch {} finally {
    loading.value = false
  }
}

function exportCSV() {
  if (!tableData.value.length) return
  const header = '设备ID,温度(°C),湿度(%),时间\n'
  const rows = tableData.value.map((r) =>
    `${r.deviceId},${r.temperature ?? ''},${r.humidity ?? ''},${formatDateTime(r.serverReceivedTime || r.timestamp)}`
  ).join('\n')
  const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `sensor-data-${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  const end = new Date()
  const start = new Date(end.getTime() - 24 * 60 * 60 * 1000)
  dateRange.value = [start, end]
  query()
})
</script>

<template>
  <div class="history">
    <div class="page-header">
      <div>
        <h1>历史数据</h1>
        <p>查询和导出传感器历史数据</p>
      </div>
      <div class="header-actions">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        />
        <el-button type="primary" @click="query" :loading="loading">查询</el-button>
        <el-button :icon="Download" @click="exportCSV">导出 CSV</el-button>
      </div>
    </div>

    <el-card class="history-chart-card">
      <TempHumChart
        v-if="timeLabels.length"
        :timeLabels="timeLabels"
        :tempSeries="tempSeries"
        :humSeries="humSeries"
        height="380px"
      />
      <el-empty v-else description="暂无数据" />
    </el-card>

    <el-card class="history-table-card" style="margin-top: 1rem;">
      <template #header><span>数据明细</span></template>
      <el-table :data="tableData" stripe v-loading="loading" max-height="400">
        <el-table-column prop="deviceId" label="设备 ID" min-width="100" />
        <el-table-column prop="temperature" label="温度 (°C)" width="100" />
        <el-table-column prop="humidity" label="湿度 (%)" width="90" />
        <el-table-column prop="water" label="水位 (cm)" width="90" />
        <el-table-column prop="rssi" label="信号 (dBm)" width="90" />
        <el-table-column label="联动" width="70">
          <template #default="{ row }">
            <el-tag :type="row.linkage ? 'success' : 'info'" size="small" effect="dark">
              {{ row.linkage ? 'ON' : 'OFF' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sendCount" label="发送次数" width="80" />
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.serverReceivedTime || row.timestamp) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.history {
  max-width: 1300px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--spacing-md);
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

.header-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}
</style>
