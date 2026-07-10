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

// 将查询结果构建为图表数据序列
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

// 按时间范围查询传感器历史
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

// 导出 CSV 文件：包含设备ID、温度、湿度、时间
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

// 默认查询最近 24 小时
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
        <!-- 时间范围选择器 -->
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
        <el-button :icon="Download" @click="exportCSV" :disabled="!tableData.length">导出 CSV</el-button>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="chart-wrap" v-if="tableData.length">
      <TempHumChart :timeLabels="timeLabels" :tempSeries="tempSeries" :humSeries="humSeries" height="300px" />
    </div>
    <el-empty v-else description="暂无数据，请调整时间范围" />

    <!-- 数据表格 -->
    <el-card v-if="tableData.length" style="margin-top: 1.5rem;">
      <el-table :data="tableData" max-height="450" stripe size="small">
        <el-table-column prop="deviceId" label="设备 ID" width="140" />
        <el-table-column prop="temperature" label="温度 (°C)" width="120" />
        <el-table-column prop="humidity" label="湿度 (%)" width="120" />
        <el-table-column prop="water" label="水位" width="100" />
        <el-table-column prop="rssi" label="RSSI (dBm)" width="120" />
        <el-table-column label="联动" width="80">
          <template #default="{ row }">
            <el-tag :type="row.linkage ? 'success' : 'info'" size="small">{{ row.linkage ? 'ON' : 'OFF' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sendCount" label="发送次数" width="100" />
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
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}
.page-header h1 { font-size: 1.5rem; margin: 0; }
.page-header p { color: var(--text-secondary); margin: 0.25rem 0 0; font-size: 0.9rem; }
.header-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; }

.chart-wrap {
  background: rgba(30, 41, 59, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 1rem;
}
</style>
